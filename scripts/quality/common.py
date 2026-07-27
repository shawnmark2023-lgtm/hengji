from __future__ import annotations

import hashlib
import json
import os
import platform
import shlex
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


IGNORED_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    ".isolated-business-build",
    ".kotlin",
    "artifacts",
    "build",
    "dist",
    "evidence",
    "node_modules",
    "__pycache__",
}


def project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def relative(path: Path, root: Path | None = None) -> str:
    base = root or project_root()
    return path.resolve().relative_to(base.resolve()).as_posix()


def iter_source_files(root: Path, suffixes: Iterable[str]) -> Iterable[Path]:
    accepted = {suffix.lower() for suffix in suffixes}
    for path in root.rglob("*"):
        if not path.is_file() or any(part in IGNORED_PARTS for part in path.parts):
            continue
        if path.suffix.lower() in accepted:
            yield path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def environment_record() -> dict[str, Any]:
    root = project_root()
    source: dict[str, Any] = {"projectFingerprint": source_fingerprint(root)}
    git = shutil.which("git")
    if git and (root / ".git").exists():
        revision = subprocess.run(
            [git, "rev-parse", "HEAD"],
            cwd=root,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        status = subprocess.run(
            [git, "status", "--porcelain=v1", "--untracked-files=all"],
            cwd=root,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        if revision.returncode == 0 and status.returncode == 0:
            dirty_paths = [line[3:].replace("\\", "/") for line in status.stdout.splitlines() if len(line) >= 4]
            source.update(
                {
                    "gitSha": revision.stdout.strip(),
                    "dirty": bool(dirty_paths),
                    "dirtyPaths": dirty_paths,
                }
            )
    return {
        "os": platform.platform(),
        "architecture": platform.machine(),
        "python": platform.python_version(),
        "ci": bool(os.environ.get("CI")),
        "githubRunId": os.environ.get("GITHUB_RUN_ID"),
        "githubRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "githubSha": os.environ.get("GITHUB_SHA"),
        "source": source,
    }


def source_fingerprint(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(
        (
            candidate
            for candidate in root.rglob("*")
            if candidate.is_file() and not any(part in IGNORED_PARTS for part in candidate.parts)
        ),
        key=lambda candidate: candidate.relative_to(root).as_posix(),
    ):
        relative_path = path.relative_to(root).as_posix()
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest().upper()


def artifact_records(paths: Iterable[Path]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for path in paths:
        resolved = path.resolve()
        if not resolved.is_file():
            raise FileNotFoundError(f"Artifact does not exist: {path}")
        records.append(
            {
                "path": relative(resolved),
                "bytes": resolved.stat().st_size,
                "sha256": sha256_file(resolved),
            }
        )
    return records


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def command_text(parts: Iterable[str | Path]) -> str:
    values = [str(part) for part in parts]
    if os.name == "nt":
        return subprocess.list2cmdline(values)
    return shlex.join(values)


def run_command(parts: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        parts,
        cwd=cwd,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def extract_last_json_object(output: str) -> dict[str, Any] | None:
    for line in reversed(output.splitlines()):
        candidate = line.strip()
        if not (candidate.startswith("{") and candidate.endswith("}")):
            continue
        try:
            value = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            return value
    return None


def evidence_record(
    *,
    gate: str,
    status: str,
    command: str,
    started_at: str,
    finished_at: str,
    duration_ms: int | None,
    test_count: int | None = None,
    artifacts: Iterable[Path] = (),
    limitations: Iterable[str] = (),
    details: dict[str, Any] | None = None,
) -> dict[str, Any]:
    allowed = {"configured", "passed", "failed", "unverified", "blocked", "prohibited"}
    if status not in allowed:
        raise ValueError(f"Unsupported evidence status: {status}")
    if status == "configured" and test_count is not None:
        raise ValueError("Configured evidence cannot contain a passed test count")
    return {
        "schemaVersion": 1,
        "gate": gate,
        "status": status,
        "configuredIsPassed": False,
        "command": command,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "durationMs": duration_ms,
        "environment": environment_record(),
        "testCount": test_count,
        "artifacts": artifact_records(artifacts),
        "limitations": list(limitations),
        "details": details or {},
    }


def print_result(value: dict[str, Any], as_json: bool) -> None:
    if as_json:
        print(json.dumps(value, ensure_ascii=False, separators=(",", ":")))
        return
    status = value.get("status", "unknown").upper()
    gate = value.get("gate", "quality")
    print(f"{status} {gate}")
    for issue in value.get("issues", []):
        location = issue.get("path", "")
        if issue.get("line"):
            location += f":{issue['line']}"
        print(f"  {issue.get('rule', 'RULE')} {location} {issue.get('message', '')}".rstrip())


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(2)
