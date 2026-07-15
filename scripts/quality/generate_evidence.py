from __future__ import annotations

import argparse
import glob
from pathlib import Path
from xml.etree import ElementTree

from common import evidence_record, project_root, utc_now, write_json


def main() -> int:
    parser = argparse.ArgumentParser(description="Write one truthful quality evidence record")
    parser.add_argument("--gate", required=True)
    parser.add_argument(
        "--status",
        required=True,
        choices=("configured", "passed", "failed", "unverified", "blocked", "prohibited"),
    )
    parser.add_argument("--command", required=True)
    parser.add_argument("--started-at")
    parser.add_argument("--finished-at")
    parser.add_argument("--duration-ms", type=int)
    parser.add_argument("--test-count", type=int)
    parser.add_argument("--junit-glob", action="append", default=[])
    parser.add_argument("--artifact", action="append", default=[])
    parser.add_argument("--artifact-glob", action="append", default=[])
    parser.add_argument("--limitation", action="append", default=[])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    root = project_root()
    artifacts = [root / item for item in args.artifact]
    for pattern in args.artifact_glob:
        artifacts.extend(Path(match) for match in glob.glob(str(root / pattern), recursive=True))
    if args.artifact_glob and not artifacts:
        parser.error("Artifact glob did not match any files")

    test_count = args.test_count
    junit_details = None
    if args.junit_glob:
        reports: list[Path] = []
        for pattern in args.junit_glob:
            reports.extend(Path(match) for match in glob.glob(str(root / pattern), recursive=True))
        reports = sorted({path.resolve() for path in reports if path.is_file()})
        if not reports:
            parser.error("JUnit glob did not match any report files")
        totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        for report in reports:
            suite = ElementTree.parse(report).getroot()
            for key in totals:
                totals[key] += int(suite.attrib.get(key, "0"))
        if totals["failures"] or totals["errors"]:
            parser.error(f"JUnit reports contain failures: {totals}")
        test_count = totals["tests"]
        junit_details = {
            **totals,
            "reports": [str(path.relative_to(root)).replace("\\", "/") for path in reports],
        }

    finished = args.finished_at or utc_now()
    record = evidence_record(
        gate=args.gate,
        status=args.status,
        command=args.command,
        started_at=args.started_at or finished,
        finished_at=finished,
        duration_ms=args.duration_ms,
        test_count=test_count,
        artifacts=artifacts,
        limitations=args.limitation,
        details={"junit": junit_details} if junit_details is not None else None,
    )
    write_json(args.output, record)
    print(f"WROTE {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
