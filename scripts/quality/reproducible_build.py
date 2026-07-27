from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Any

from common import command_text, project_root, sha256_file, source_fingerprint, utc_now


IGNORED_COPY_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".isolated-business-build",
    ".kotlin",
    "__pycache__",
    "artifacts",
    "build",
    "coverage",
    "dist",
    "evidence",
    "node_modules",
}


def ignore_copy(_directory: str, names: list[str]) -> set[str]:
    return {name for name in names if name in IGNORED_COPY_NAMES or name.endswith(".pyc")}


def normalized_archive_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with zipfile.ZipFile(path) as archive:
        for item in sorted(archive.infolist(), key=lambda entry: entry.filename):
            digest.update(item.filename.encode("utf-8"))
            digest.update(b"\0")
            digest.update(str(item.external_attr).encode("ascii"))
            digest.update(b"\0")
            if not item.is_dir():
                with archive.open(item) as handle:
                    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                        digest.update(chunk)
            digest.update(b"\0")
    return digest.hexdigest().upper()


def artifacts(root: Path, patterns: list[str]) -> list[dict[str, Any]]:
    found = sorted(
        {
            Path(match).resolve()
            for pattern in patterns
            for match in glob.glob(str(root / pattern), recursive=True)
            if Path(match).is_file()
        },
        key=lambda path: path.relative_to(root).as_posix(),
    )
    if not found:
        raise RuntimeError(f"No artifacts matched: {patterns}")
    records: list[dict[str, Any]] = []
    for path in found:
        record = {
            "path": path.relative_to(root).as_posix(),
            "bytes": path.stat().st_size,
            "sha256": sha256_file(path),
        }
        if zipfile.is_zipfile(path):
            record["normalizedArchiveContentSha256"] = normalized_archive_sha256(path)
        records.append(record)
    return records


def run_build(root: Path, tasks: list[str], patterns: list[str], iteration: int) -> tuple[dict[str, Any], str]:
    gradle = root / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    command = [
        str(gradle),
        "clean",
        *tasks,
        "--dependency-verification",
        "strict",
        "--no-daemon",
        "--no-build-cache",
        "--no-configuration-cache",
        "--rerun-tasks",
        "--console=plain",
        "--stacktrace",
    ]
    started_at = utc_now()
    started = time.monotonic()
    environment = os.environ.copy()
    environment["SOURCE_DATE_EPOCH"] = "0"
    completed = subprocess.run(
        command,
        cwd=root,
        env=environment,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    duration_ms = round((time.monotonic() - started) * 1000)
    if completed.returncode != 0:
        raise RuntimeError(
            f"Build {iteration} failed ({command_text(command)}):\n"
            + "\n".join(completed.stdout.splitlines()[-100:])
        )
    return (
        {
            "iteration": iteration,
            "startedAt": started_at,
            "finishedAt": utc_now(),
            "durationMs": duration_ms,
            "command": command_text(command),
            "artifacts": artifacts(root, patterns),
        },
        completed.stdout,
    )


def compare(
    first: list[dict[str, Any]],
    second: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    first_by_path = {item["path"]: item for item in first}
    second_by_path = {item["path"]: item for item in second}
    content_differences: list[dict[str, Any]] = []
    container_differences: list[dict[str, Any]] = []
    for path in sorted(set(first_by_path) | set(second_by_path)):
        left = first_by_path.get(path)
        right = second_by_path.get(path)
        if left is None or right is None:
            content_differences.append({"path": path, "first": left, "second": right})
            continue
        if left["sha256"] != right["sha256"]:
            container_differences.append(
                {
                    "path": path,
                    "firstSha256": left["sha256"],
                    "secondSha256": right["sha256"],
                }
            )
        checks = {
            "bytes": left["bytes"] == right["bytes"],
            "normalizedArchiveContentSha256":
                left.get("normalizedArchiveContentSha256")
                == right.get("normalizedArchiveContentSha256"),
        }
        if not all(checks.values()):
            content_differences.append(
                {
                    "path": path,
                    "checks": checks,
                    "first": left,
                    "second": right,
                }
            )
    return content_differences, container_differences


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build a Windows or Android artifact twice and compare normalized archive content"
    )
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--target", choices=("android", "windows"), required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    source = args.project.resolve()
    policy = json.loads((source / "quality" / "reproducibility-policy.json").read_text(encoding="utf-8"))
    target = policy["reproducibleTargets"][args.target]
    output = args.output or source / "quality" / "evidence" / f"reproducible-{args.target}.json"
    if not output.is_absolute():
        output = source / output
    output = output.resolve()
    source_hash = source_fingerprint(source)
    result: dict[str, Any]
    with tempfile.TemporaryDirectory(
        prefix=f"hengji-repro-{args.target}-",
        ignore_cleanup_errors=True,
    ) as temporary:
        worktree = Path(temporary) / "source"
        shutil.copytree(source, worktree, ignore=ignore_copy)
        try:
            first, first_output = run_build(worktree, target["tasks"], target["artifacts"], 1)
            second, second_output = run_build(worktree, target["tasks"], target["artifacts"], 2)
            differences, container_differences = compare(first["artifacts"], second["artifacts"])
            result = {
                "schemaVersion": 1,
                "gate": f"reproducible-{args.target}",
                "status": "passed" if not differences else "failed",
                "configuredIsPassed": False,
                "sourceFingerprint": source_hash,
                "target": args.target,
                "comparison": target["comparison"],
                "builds": [first, second],
                "differences": differences,
                "rawContainerSha256Identical": not container_differences,
                "rawContainerDifferences": container_differences,
                "limitations": [
                    "Pass/fail compares archive paths, sizes, permissions, and uncompressed entry content "
                    "while ignoring ZIP timestamps; raw container SHA-256 is retained as a diagnostic and "
                    "is explicitly not the reproducibility decision",
                    "Two clean builds use one source snapshot on one runner; independent-runner "
                    "reproducibility still requires CI evidence",
                ],
                "outputTail": (first_output + second_output).splitlines()[-80:],
            }
        except (OSError, RuntimeError, subprocess.SubprocessError) as error:
            result = {
                "schemaVersion": 1,
                "gate": f"reproducible-{args.target}",
                "status": "failed",
                "configuredIsPassed": False,
                "sourceFingerprint": source_hash,
                "target": args.target,
                "comparison": target["comparison"],
                "builds": [],
                "differences": [],
                "rawContainerSha256Identical": None,
                "rawContainerDifferences": [],
                "limitations": [],
                "error": str(error),
            }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
