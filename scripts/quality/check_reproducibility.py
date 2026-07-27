from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

from common import project_root, relative, sha256_file


DYNAMIC_VERSION = re.compile(r"(?i)(?:^|[:=])(?:latest(?:\.[a-z]+)?|[^,\s\"']*\+)(?:$|[,\"'])")


def issue(issues: list[dict[str, Any]], rule: str, path: Path, message: str) -> None:
    issues.append({"rule": rule, "path": relative(path), "message": message})


def normalized_wrapper_url(value: str) -> str:
    return value.replace("\\:", ":")


def check_verification_metadata(path: Path, issues: list[dict[str, Any]]) -> int:
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as error:
        issue(issues, "REPRO-VERIFICATION-XML", path, f"Invalid verification metadata: {error}")
        return 0
    artifacts = root.findall(".//{*}artifact")
    for artifact in artifacts:
        checksums = artifact.findall("{*}sha256")
        if not checksums or any(not checksum.attrib.get("value") for checksum in checksums):
            issue(
                issues,
                "REPRO-VERIFICATION-SHA256",
                path,
                f"Artifact has no SHA-256 checksum: {artifact.attrib.get('name', '<unknown>')}",
            )
    return len(artifacts)


def check(root: Path, manifest_output: Path | None) -> dict[str, Any]:
    policy_path = root / "quality" / "reproducibility-policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    issues: list[dict[str, Any]] = []
    wrapper_properties_path = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    wrapper_jar_path = root / "gradle" / "wrapper" / "gradle-wrapper.jar"
    properties: dict[str, str] = {}
    for line in wrapper_properties_path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key] = value
    expected_wrapper = policy["gradleWrapper"]
    actual_url = normalized_wrapper_url(properties.get("distributionUrl", ""))
    if actual_url != expected_wrapper["distributionUrl"]:
        issue(issues, "REPRO-WRAPPER-URL", wrapper_properties_path, "Gradle distribution URL differs from policy")
    if properties.get("distributionSha256Sum", "").lower() != expected_wrapper["distributionSha256"].lower():
        issue(issues, "REPRO-WRAPPER-DISTRIBUTION-SHA256", wrapper_properties_path, "Gradle distribution checksum differs from policy")
    wrapper_hash = sha256_file(wrapper_jar_path)
    if wrapper_hash != expected_wrapper["wrapperJarSha256"].upper():
        issue(issues, "REPRO-WRAPPER-JAR-SHA256", wrapper_jar_path, "Gradle Wrapper JAR checksum differs from policy")

    lock_records: list[dict[str, Any]] = []
    locked_coordinates = 0
    for item in policy["requiredLockfiles"]:
        path = root / item
        if not path.is_file():
            issue(issues, "REPRO-LOCK-MISSING", path, "Required dependency lockfile is missing")
            continue
        content = path.read_text(encoding="utf-8")
        dynamic = [line for line in content.splitlines() if DYNAMIC_VERSION.search(line)]
        if dynamic:
            issue(issues, "REPRO-DYNAMIC-LOCK", path, "Dependency lockfile contains a dynamic version")
        coordinates = [
            line.split("=", 1)[0]
            for line in content.splitlines()
            if line and not line.startswith("#") and line != "empty="
        ]
        locked_coordinates += len(coordinates)
        lock_records.append(
            {
                "path": item,
                "bytes": path.stat().st_size,
                "sha256": sha256_file(path),
                "coordinates": len(coordinates),
            }
        )

    verification_records: list[dict[str, Any]] = []
    verified_artifacts = 0
    for item in policy["verificationMetadata"]:
        path = root / item
        if not path.is_file():
            issue(issues, "REPRO-VERIFICATION-MISSING", path, "Required verification metadata is missing")
            continue
        count = check_verification_metadata(path, issues)
        verified_artifacts += count
        verification_records.append(
            {
                "path": item,
                "bytes": path.stat().st_size,
                "sha256": sha256_file(path),
                "artifacts": count,
            }
        )

    build_files = sorted(
        [
            root / "build.gradle.kts",
            root / "settings.gradle.kts",
            root / "gradle" / "libs.versions.toml",
            *(root.glob("apps/**/build.gradle.kts")),
            *(root.glob("modules/**/build.gradle.kts")),
            *(root.glob("quality/**/build.gradle.kts")),
        ],
        key=lambda path: relative(path, root),
    )
    for path in build_files:
        if not path.is_file():
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if DYNAMIC_VERSION.search(line):
                issues.append(
                    {
                        "rule": "REPRO-DYNAMIC-BUILD",
                        "path": relative(path, root),
                        "line": line_number,
                        "message": "Build configuration contains a dynamic version",
                    }
                )

    root_build = (root / "build.gradle.kts").read_text(encoding="utf-8")
    if "lockMode = LockMode.STRICT" not in root_build:
        issue(issues, "REPRO-STRICT-LOCK", root / "build.gradle.kts", "Root build does not enforce strict dependency locking")
    manifest = {
        "schemaVersion": 1,
        "wrapper": {
            "distributionUrl": actual_url,
            "distributionSha256": properties.get("distributionSha256Sum"),
            "wrapperJarSha256": wrapper_hash,
        },
        "locks": lock_records,
        "verificationMetadata": verification_records,
    }
    canonical = json.dumps(manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    manifest["manifestSha256"] = hashlib.sha256(canonical).hexdigest().upper()
    if manifest_output:
        manifest_output.parent.mkdir(parents=True, exist_ok=True)
        manifest_output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {
        "gate": "reproducibility",
        "status": "failed" if issues else "passed",
        "testCount": len(lock_records) + len(verification_records) + 4,
        "checkedFiles": len(lock_records) + len(verification_records) + len(build_files) + 3,
        "lockedCoordinates": locked_coordinates,
        "verifiedArtifacts": verified_artifacts,
        "dependencyManifestSha256": manifest["manifestSha256"],
        "manifest": relative(manifest_output, root) if manifest_output else None,
        "configuredTargets": sorted(policy["reproducibleTargets"]),
        "issues": issues,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate pinned dependencies and emit a deterministic dependency manifest")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--manifest-output", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    root = args.project.resolve()
    output = args.manifest_output
    if output is None:
        output = root / "quality" / "evidence" / "dependency-manifest.json"
    elif not output.is_absolute():
        output = root / output
    result = check(root, output.resolve())
    if args.json:
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    else:
        print(
            f"{result['status'].upper()} reproducibility "
            f"({result['lockedCoordinates']} locked coordinates, {result['verifiedArtifacts']} verified artifacts)"
        )
        for item in result["issues"]:
            line = f":{item['line']}" if item.get("line") else ""
            print(f"  {item['rule']} {item['path']}{line} {item['message']}")
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
