from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any

from common import (
    command_text,
    evidence_record,
    extract_last_json_object,
    project_root,
    run_command,
    utc_now,
    write_json,
)


ALL_GATES = (
    "formatting",
    "architecture",
    "release-guards",
    "reproducibility",
    "coverage",
    "malformed-import",
    "large-ledger",
)


def python_gate(root: Path, gate: str) -> list[str]:
    script = {
        "formatting": "check_formatting.py",
        "architecture": "check_architecture.py",
        "release-guards": "check_release_guards.py",
        "reproducibility": "check_reproducibility.py",
        "coverage": "check_coverage.py",
    }[gate]
    return [sys.executable, str(root / "scripts" / "quality" / script), "--project", str(root), "--json"]


def gradle_gate(root: Path, gate: str, count: int, max_millis: int, max_memory_mib: int) -> list[str]:
    gradle_override = os.environ.get("HENGJI_GRADLE")
    gradle = Path(gradle_override) if gradle_override else root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    task = ":import-harness:run" if gate == "malformed-import" else ":ledger-harness:run"
    command = [
        str(gradle),
        "-p",
        str(root / "quality" / "harness"),
        "--init-script",
        str(root / "quality" / "harness" / "isolate-business-build.init.gradle.kts"),
        task,
        "--console=plain",
    ]
    if gate == "large-ledger":
        command.insert(-1, f"--args={count} {max_millis} {max_memory_mib}")
    return command


def run_gate(root: Path, gate: str, args: argparse.Namespace) -> tuple[dict[str, Any], str]:
    command = (
        python_gate(root, gate)
        if gate in {"formatting", "architecture", "release-guards", "reproducibility", "coverage"}
        else gradle_gate(root, gate, args.benchmark_count, args.max_millis, args.max_memory_mib)
    )
    started_at = utc_now()
    started = time.monotonic()
    completed = run_command(command, root)
    duration_ms = round((time.monotonic() - started) * 1000)
    finished_at = utc_now()
    details = extract_last_json_object(completed.stdout) or {}
    status = "passed" if completed.returncode == 0 and details.get("status", "passed") == "passed" else "failed"
    test_count = details.get("testCount")
    if not isinstance(test_count, int):
        test_count = details.get("checkedFiles") if isinstance(details.get("checkedFiles"), int) else None
    limitations: list[str] = []
    if gate == "large-ledger":
        limitations.append("Developer/CI in-memory baseline; not representative-device Beta performance evidence")
    record = evidence_record(
        gate=gate,
        status=status,
        command=command_text(command),
        started_at=started_at,
        finished_at=finished_at,
        duration_ms=duration_ms,
        test_count=test_count,
        limitations=limitations,
        details={
            **details,
            "exitCode": completed.returncode,
            "outputTail": completed.stdout.splitlines()[-80:],
        },
    )
    return record, completed.stdout


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")

    parser = argparse.ArgumentParser(description="Run HENGJI quality gates and emit evidence")
    parser.add_argument("--gates", nargs="+", choices=ALL_GATES, default=list(ALL_GATES))
    parser.add_argument("--benchmark-count", type=int, default=100_000)
    parser.add_argument("--max-millis", type=int, default=20_000)
    parser.add_argument("--max-memory-mib", type=int, default=768)
    parser.add_argument(
        "--project",
        type=Path,
        help="Project root override, for example an ASCII subst drive on Windows",
    )
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    root = args.project.absolute() if args.project else project_root()
    if not (root / "settings.gradle.kts").is_file():
        parser.error(f"Project root is invalid: {root}")
    output_dir = (args.output_dir or root / "quality" / "evidence").resolve()

    records: list[dict[str, Any]] = []
    for gate in args.gates:
        record, output = run_gate(root, gate, args)
        records.append(record)
        write_json(output_dir / f"{gate}.json", record)
        print(output, end="" if output.endswith("\n") or not output else "\n")
        print(f"{record['status'].upper()} {gate} ({record['durationMs']} ms)")

    summary = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "configuredIsPassed": False,
        "status": "passed" if all(record["status"] == "passed" for record in records) else "failed",
        "gates": [
            {
                "gate": record["gate"],
                "status": record["status"],
                "evidence": f"{record['gate']}.json",
            }
            for record in records
        ],
    }
    write_json(output_dir / "summary.json", summary)
    print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
    return 0 if summary["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
