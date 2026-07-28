from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

from check_supply_chain import check
from common import command_text, evidence_record, project_root, utc_now, write_json
from install_osv_scanner import platform_key


def package_count(result: dict[str, Any]) -> int:
    packages: set[tuple[str, str, str]] = set()
    for scan_result in result.get("results", []):
        for item in scan_result.get("packages", []):
            package = item.get("package", {})
            packages.add(
                (
                    str(package.get("ecosystem", "")),
                    str(package.get("name", "")),
                    str(package.get("version", "")),
                )
            )
    return len(packages)


def vulnerability_count(result: dict[str, Any]) -> int:
    identifiers: set[str] = set()
    for scan_result in result.get("results", []):
        for item in scan_result.get("packages", []):
            for vulnerability in item.get("vulnerabilities", []):
                identifier = vulnerability.get("id")
                if identifier:
                    identifiers.add(str(identifier))
    return len(identifiers)


def license_violation_count(result: dict[str, Any]) -> int:
    return sum(
        len(item.get("license_violations", []))
        for scan_result in result.get("results", [])
        for item in scan_result.get("packages", [])
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the pinned OSV vulnerability and license audit")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--scanner", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    root = args.project.resolve()
    scanner = args.scanner.resolve()
    if not scanner.is_file():
        parser.error(f"OSV Scanner does not exist: {scanner}")
    policy = json.loads((root / "quality" / "supply-chain-policy.json").read_text(encoding="utf-8"))
    scanner_artifact = policy["osvScanner"]["artifacts"].get(platform_key())
    if not scanner_artifact:
        parser.error(f"Unsupported OSV Scanner host: {platform_key()}")
    scanner_hash = hashlib.sha256(scanner.read_bytes()).hexdigest()
    if scanner_hash != scanner_artifact["sha256"].lower():
        parser.error(
            f"OSV Scanner checksum mismatch: expected {scanner_artifact['sha256'].lower()}, got {scanner_hash}"
        )
    output_dir = args.output_dir or root / "quality" / "evidence" / "supply-chain"
    if not output_dir.is_absolute():
        output_dir = root / output_dir
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    inventory = check(root, output_dir)
    write_json(output_dir / "inventory.json", inventory)
    if inventory["status"] != "passed":
        print(json.dumps(inventory, ensure_ascii=False, separators=(",", ":")))
        return 1

    scanner_input = root / inventory["scannerInput"]
    scanner_config = root / inventory["scannerConfig"]
    raw_output = output_dir / "osv-scan.json"
    raw_output.unlink(missing_ok=True)
    licenses = ",".join(inventory["allowedLicenses"])
    command = [
        str(scanner),
        "scan",
        "source",
        "--lockfile",
        f"osv-scanner:{scanner_input}",
        "--config",
        str(scanner_config),
        "--format",
        "json",
        "--all-packages",
        f"--licenses={licenses}",
        "--output-file",
        str(raw_output),
    ]
    started_at = utc_now()
    started = time.monotonic()
    completed = subprocess.run(
        command,
        cwd=root,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    duration_ms = round((time.monotonic() - started) * 1000)
    finished_at = utc_now()
    raw: dict[str, Any] = {}
    if raw_output.is_file():
        raw = json.loads(raw_output.read_text(encoding="utf-8"))
    scanned = package_count(raw)
    vulnerabilities = vulnerability_count(raw)
    license_violations = license_violation_count(raw)
    issues: list[dict[str, Any]] = []
    if completed.returncode != 0:
        issues.append(
            {
                "rule": "SUPPLY-OSV-FINDING",
                "message": f"OSV Scanner returned {completed.returncode}; inspect osv-scan.json",
            }
        )
    if scanned != inventory["scanPackageCount"]:
        issues.append(
            {
                "rule": "SUPPLY-OSV-COVERAGE",
                "message": f"Expected {inventory['scanPackageCount']} packages, scanner reported {scanned}",
            }
        )
    if vulnerabilities:
        issues.append(
            {
                "rule": "SUPPLY-VULNERABILITY",
                "message": f"OSV Scanner reported {vulnerabilities} known vulnerabilities",
            }
        )
    if license_violations:
        issues.append(
            {
                "rule": "SUPPLY-LICENSE-VIOLATION",
                "message": f"OSV Scanner reported {license_violations} license violations",
            }
        )
    artifacts = [output_dir / "hengji-windows-android.cdx.json"]
    if raw_output.is_file():
        artifacts.append(raw_output)
    record = evidence_record(
        gate="supply-chain-scan",
        status="failed" if issues else "passed",
        command=command_text(command),
        started_at=started_at,
        finished_at=finished_at,
        duration_ms=duration_ms,
        test_count=scanned,
        artifacts=artifacts,
        details={
            "inventory": inventory,
            "scannerExitCode": completed.returncode,
            "scannedPackageCount": scanned,
            "vulnerabilityCount": vulnerabilities,
            "licenseViolationCount": license_violations,
            "issues": issues,
            "outputTail": completed.stdout.splitlines()[-80:],
        },
    )
    write_json(output_dir / "supply-chain-scan.json", record)
    print(completed.stdout, end="" if completed.stdout.endswith("\n") or not completed.stdout else "\n")
    print(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
    return 0 if record["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
