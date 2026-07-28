from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import tempfile
import urllib.request
from pathlib import Path

from common import project_root


def platform_key() -> str:
    system = platform.system().lower()
    machine = platform.machine().lower()
    architecture = "amd64" if machine in {"amd64", "x86_64"} else machine
    return f"{system}-{architecture}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Install the checksum-pinned OSV Scanner release")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = args.project.resolve()
    policy = json.loads((root / "quality" / "supply-chain-policy.json").read_text(encoding="utf-8"))
    key = platform_key()
    artifact = policy["osvScanner"]["artifacts"].get(key)
    if not artifact:
        raise SystemExit(f"Unsupported OSV Scanner host: {key}")

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    expected_hash = artifact["sha256"].lower()
    if output.is_file() and hashlib.sha256(output.read_bytes()).hexdigest() == expected_hash:
        print(f"Verified existing OSV Scanner {policy['osvScanner']['version']}: {output}")
        return 0

    url = f"{policy['osvScanner']['releaseBaseUrl']}/{artifact['name']}"
    request = urllib.request.Request(url, headers={"User-Agent": "Hengji-Supply-Chain-Gate"})
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as handle:
        temporary = Path(handle.name)
        with urllib.request.urlopen(request, timeout=120) as response:
            while chunk := response.read(1024 * 1024):
                handle.write(chunk)
    actual_hash = hashlib.sha256(temporary.read_bytes()).hexdigest()
    if actual_hash != expected_hash:
        temporary.unlink(missing_ok=True)
        raise SystemExit(f"OSV Scanner checksum mismatch: expected {expected_hash}, got {actual_hash}")
    os.replace(temporary, output)
    output.chmod(output.stat().st_mode | 0o111)
    print(f"Installed OSV Scanner {policy['osvScanner']['version']}: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
