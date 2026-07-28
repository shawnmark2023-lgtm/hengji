from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import tomllib
import uuid
from collections import defaultdict
from pathlib import Path
from typing import Any
from urllib.parse import quote

from common import print_result, project_root, relative, write_json


EXACT_PYTHON_REQUIREMENT = re.compile(r"^([A-Za-z0-9_.-]+)==([A-Za-z0-9_.!+-]+)$")
GITHUB_ACTION = re.compile(r"(?m)^\s*(?:-\s*)?uses:\s+([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)@([^\s#]+)")
FULL_GIT_SHA = re.compile(r"^[0-9a-f]{40}$")
DYNAMIC_VERSION = re.compile(r"(?i)(?:latest|snapshot|[\[\](),*+])")


def issue(issues: list[dict[str, Any]], rule: str, path: Path, message: str) -> None:
    issues.append({"rule": rule, "path": relative(path), "message": message})


def maven_purl(group: str, name: str, version: str) -> str:
    namespace = quote(group, safe="._~-")
    package = quote(name, safe="._~-")
    release = quote(version, safe="._~+-")
    return f"pkg:maven/{namespace}/{package}@{release}"


def npm_purl(name: str, version: str) -> str:
    if name.startswith("@") and "/" in name:
        namespace, package = name.split("/", 1)
        encoded_name = f"{quote(namespace, safe='._~-')}/{quote(package, safe='._~-')}"
    else:
        encoded_name = quote(name, safe="._~-")
    return f"pkg:npm/{encoded_name}@{quote(version, safe='._~+-')}"


def pypi_purl(name: str, version: str) -> str:
    return f"pkg:pypi/{quote(name, safe='._~-')}@{quote(version, safe='._~+-')}"


def internal_component(name: str, version: str, component_type: str = "application") -> dict[str, Any]:
    purl = f"pkg:generic/{quote(name, safe='._~-')}@{quote(version, safe='._~+-')}"
    return {
        "type": component_type,
        "bom-ref": purl,
        "name": name,
        "version": version,
        "scope": "required",
        "licenses": [{"expression": "LicenseRef-Proprietary"}],
        "purl": purl,
    }


def parse_gradle_runtime(
    root: Path,
    platform: str,
    value: dict[str, Any],
    issues: list[dict[str, Any]],
) -> dict[tuple[str, str, str], set[str]]:
    path = root / value["lockfile"]
    selected = set(value["configurations"])
    records: dict[tuple[str, str, str], set[str]] = defaultdict(set)
    found_configurations: set[str] = set()
    if not path.is_file():
        issue(issues, "SUPPLY-LOCK-MISSING", path, f"{platform} runtime lockfile is missing")
        return records

    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        coordinate, configurations = line.split("=", 1)
        if coordinate == "empty":
            continue
        parts = coordinate.split(":")
        if len(parts) != 3:
            issue(issues, "SUPPLY-GRADLE-COORDINATE", path, f"Invalid Gradle coordinate: {coordinate}")
            continue
        active = selected & set(configurations.split(","))
        found_configurations.update(active)
        if not active:
            continue
        group, name, version = parts
        if not version or DYNAMIC_VERSION.search(version):
            issue(issues, "SUPPLY-DYNAMIC-VERSION", path, f"Runtime dependency is not exact: {coordinate}")
            continue
        records[(group, name, version)].add(platform)

    missing = selected - found_configurations
    if missing:
        issue(
            issues,
            "SUPPLY-RUNTIME-CONFIGURATION",
            path,
            f"Configured runtime classpaths were not found: {', '.join(sorted(missing))}",
        )
    if not records:
        issue(issues, "SUPPLY-RUNTIME-EMPTY", path, f"No {platform} runtime dependencies were selected")
    return records


def sri_hash(integrity: str, path: Path, issues: list[dict[str, Any]]) -> list[dict[str, str]]:
    try:
        algorithm, encoded = integrity.split("-", 1)
        digest = base64.b64decode(encoded, validate=True).hex().upper()
    except (ValueError, TypeError):
        issue(issues, "SUPPLY-NPM-INTEGRITY", path, "Package lock contains an invalid integrity digest")
        return []
    names = {"sha256": "SHA-256", "sha384": "SHA-384", "sha512": "SHA-512"}
    if algorithm not in names:
        issue(issues, "SUPPLY-NPM-INTEGRITY", path, f"Unsupported npm integrity algorithm: {algorithm}")
        return []
    return [{"alg": names[algorithm], "content": digest}]


def parse_npm_project(
    root: Path,
    value: dict[str, Any],
    allowed_licenses: set[str],
    issues: list[dict[str, Any]],
) -> tuple[dict[str, Any], list[dict[str, Any]], list[dict[str, str]]]:
    path = root / value["packageLock"]
    if not path.is_file():
        issue(issues, "SUPPLY-NPM-LOCK-MISSING", path, "npm package lock is missing")
        return internal_component(value["name"], "0.0.0"), [], []
    lock = json.loads(path.read_text(encoding="utf-8"))
    root_package = lock.get("packages", {}).get("", {})
    root_component = internal_component(
        root_package.get("name", value["name"]),
        root_package.get("version", "0.0.0"),
    )
    components: list[dict[str, Any]] = []
    scan_packages: list[dict[str, str]] = []
    for package_path, package in sorted(lock.get("packages", {}).items()):
        if not package_path or "version" not in package:
            continue
        name = package_path.rsplit("node_modules/", 1)[-1]
        version = str(package["version"])
        license_name = package.get("license")
        integrity = package.get("integrity")
        if DYNAMIC_VERSION.search(version):
            issue(issues, "SUPPLY-DYNAMIC-VERSION", path, f"npm dependency is not exact: {name}@{version}")
        if not integrity:
            issue(issues, "SUPPLY-NPM-INTEGRITY", path, f"npm dependency has no integrity digest: {name}@{version}")
        if not license_name:
            issue(issues, "SUPPLY-LICENSE-MISSING", path, f"npm dependency has no license: {name}@{version}")
        elif license_name not in allowed_licenses:
            issue(issues, "SUPPLY-LICENSE-POLICY", path, f"npm dependency license is not allowed: {license_name}")
        purl = npm_purl(name, version)
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "name": name,
            "version": version,
            "scope": "excluded" if package.get("dev") else "required",
            "purl": purl,
            "properties": [
                {
                    "name": "hengji:dependency-purpose",
                    "value": "development" if package.get("dev") else "runtime",
                }
            ],
        }
        if license_name:
            component["licenses"] = [{"license": {"id": license_name}}]
        if integrity:
            component["hashes"] = sri_hash(integrity, path, issues)
        components.append(component)
        scan_packages.append({"name": name, "version": version, "ecosystem": "npm"})
    return root_component, components, scan_packages


def parse_python_project(
    root: Path,
    value: dict[str, Any],
    license_policy: dict[str, str],
    allowed_licenses: set[str],
    issues: list[dict[str, Any]],
) -> tuple[dict[str, Any], list[dict[str, Any]], list[dict[str, str]]]:
    path = root / value["pyproject"]
    if not path.is_file():
        issue(issues, "SUPPLY-PYPROJECT-MISSING", path, "Python project metadata is missing")
        return internal_component(value["name"], "0.0.0"), [], []
    project = tomllib.loads(path.read_text(encoding="utf-8"))
    metadata = project.get("project", {})
    root_component = internal_component(metadata.get("name", value["name"]), metadata.get("version", "0.0.0"))
    components: list[dict[str, Any]] = []
    scan_packages: list[dict[str, str]] = []

    requirements = [
        (requirement, "build")
        for requirement in project.get("build-system", {}).get("requires", [])
    ] + [
        (requirement, "runtime")
        for requirement in metadata.get("dependencies", [])
    ]
    for requirement, purpose in requirements:
        match = EXACT_PYTHON_REQUIREMENT.fullmatch(requirement)
        if not match:
            issue(
                issues,
                "SUPPLY-PYTHON-EXACT",
                path,
                f"Python {purpose} dependency must use an exact == version: {requirement}",
            )
            continue
        name, version = match.groups()
        license_name = license_policy.get(name)
        if not license_name:
            issue(issues, "SUPPLY-LICENSE-MISSING", path, f"Python dependency has no audited license: {name}")
        elif license_name not in allowed_licenses:
            issue(issues, "SUPPLY-LICENSE-POLICY", path, f"Python dependency license is not allowed: {license_name}")
        purl = pypi_purl(name, version)
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "name": name,
            "version": version,
            "scope": "excluded" if purpose == "build" else "required",
            "purl": purl,
            "properties": [{"name": "hengji:dependency-purpose", "value": purpose}],
        }
        if license_name:
            component["licenses"] = [{"license": {"id": license_name}}]
        components.append(component)
        scan_packages.append({"name": name, "version": version, "ecosystem": "PyPI"})
    return root_component, components, scan_packages


def check_action_pins(
    root: Path,
    policy: dict[str, dict[str, str]],
    issues: list[dict[str, Any]],
) -> int:
    count = 0
    for path in sorted((root / ".github" / "workflows").glob("*.yml")):
        for name, action_ref in GITHUB_ACTION.findall(path.read_text(encoding="utf-8")):
            count += 1
            if not FULL_GIT_SHA.fullmatch(action_ref):
                issue(issues, "SUPPLY-ACTION-SHA", path, f"GitHub Action must use a full commit SHA: {name}@{action_ref}")
                continue
            expected = policy.get(name)
            if not expected:
                issue(issues, "SUPPLY-ACTION-POLICY", path, f"GitHub Action is missing from policy: {name}")
            elif action_ref != expected["ref"]:
                issue(issues, "SUPPLY-ACTION-POLICY", path, f"GitHub Action SHA differs from policy: {name}")
    return count


def write_osv_config(
    path: Path,
    overrides: list[dict[str, str]],
    scan_packages: set[tuple[str, str, str]],
    issues: list[dict[str, Any]],
    policy_path: Path,
) -> None:
    lines = [
        "# Generated from quality/supply-chain-policy.json.",
        "# Exact versions and expiry dates force a new review when the dependency or terms change.",
    ]
    seen: set[tuple[str, str, str]] = set()
    for override in overrides:
        key = (override["ecosystem"], override["name"], override["version"])
        if key in seen:
            issue(issues, "SUPPLY-LICENSE-OVERRIDE", policy_path, f"Duplicate license override: {key}")
            continue
        seen.add(key)
        if key not in scan_packages:
            issue(
                issues,
                "SUPPLY-LICENSE-OVERRIDE",
                policy_path,
                f"License override does not match a scanned dependency: {override['name']}@{override['version']}",
            )
        if not override["license"].startswith("LicenseRef-"):
            issue(issues, "SUPPLY-LICENSE-OVERRIDE", policy_path, "Non-standard terms require a LicenseRef identifier")
        if not override.get("source", "").startswith("https://"):
            issue(issues, "SUPPLY-LICENSE-OVERRIDE", policy_path, "License override requires an HTTPS source")
        lines.extend(
            [
                "",
                "[[PackageOverrides]]",
                f'name = {json.dumps(override["name"])}',
                f'version = {json.dumps(override["version"])}',
                f'ecosystem = {json.dumps(override["ecosystem"])}',
                "license.ignore = true",
                f'effectiveUntil = {override["effectiveUntil"]}',
                f'reason = {json.dumps(override["reason"] + " Source: " + override["source"])}',
            ]
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def merge_component(target: dict[str, dict[str, Any]], component: dict[str, Any]) -> None:
    existing = target.get(component["bom-ref"])
    if existing is None:
        target[component["bom-ref"]] = component
    elif existing != component:
        raise ValueError(f"Conflicting component metadata for {component['bom-ref']}")


def check(root: Path, output_dir: Path) -> dict[str, Any]:
    policy_path = root / "quality" / "supply-chain-policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    issues: list[dict[str, Any]] = []
    allowed_licenses = set(policy["allowedLicenses"])
    app = policy["application"]
    components: dict[str, dict[str, Any]] = {}
    dependencies: dict[str, set[str]] = defaultdict(set)
    scan_packages: dict[tuple[str, str, str], dict[str, str]] = {}
    platform_records: dict[str, dict[tuple[str, str, str], set[str]]] = {}

    maven_records: dict[tuple[str, str, str], set[str]] = defaultdict(set)
    license_overrides = {
        (override["ecosystem"], override["name"], override["version"]): override
        for override in policy["licenseOverrides"]
    }
    for platform, value in policy["runtimePlatforms"].items():
        records = parse_gradle_runtime(root, platform, value, issues)
        platform_records[platform] = records
        for coordinate, platforms in records.items():
            maven_records[coordinate].update(platforms)

    platform_refs: list[str] = []
    for platform, records in sorted(platform_records.items()):
        component = internal_component(f"hengji-{platform}", app["version"])
        component["properties"] = [{"name": "hengji:release-platform", "value": platform}]
        merge_component(components, component)
        platform_refs.append(component["bom-ref"])
        for group, name, version in sorted(records):
            dependencies[component["bom-ref"]].add(maven_purl(group, name, version))

    for (group, name, version), platforms in sorted(maven_records.items()):
        purl = maven_purl(group, name, version)
        component = {
            "type": "library",
            "bom-ref": purl,
            "group": group,
            "name": name,
            "version": version,
            "scope": "required",
            "purl": purl,
            "properties": [{"name": "hengji:runtime-platforms", "value": ",".join(sorted(platforms))}],
        }
        license_override = license_overrides.get(("Maven", f"{group}:{name}", version))
        if license_override:
            component["licenses"] = [{"expression": license_override["license"]}]
            component["properties"].extend(
                [
                    {"name": "hengji:license-terms", "value": license_override["source"]},
                    {"name": "hengji:license-review-by", "value": license_override["effectiveUntil"]},
                ]
            )
        merge_component(components, component)
        scan_packages[("Maven", f"{group}:{name}", version)] = {
            "name": f"{group}:{name}",
            "version": version,
            "ecosystem": "Maven",
        }

    service_refs: list[str] = []
    for value in policy["npmProjects"]:
        service, packages, scans = parse_npm_project(root, value, allowed_licenses, issues)
        merge_component(components, service)
        service_refs.append(service["bom-ref"])
        for component in packages:
            merge_component(components, component)
            dependencies[service["bom-ref"]].add(component["bom-ref"])
        for package in scans:
            scan_packages[(package["ecosystem"], package["name"], package["version"])] = package

    for value in policy["pythonProjects"]:
        service, packages, scans = parse_python_project(
            root,
            value,
            policy["pythonPackageLicenses"],
            allowed_licenses,
            issues,
        )
        merge_component(components, service)
        service_refs.append(service["bom-ref"])
        for component in packages:
            merge_component(components, component)
            dependencies[service["bom-ref"]].add(component["bom-ref"])
        for package in scans:
            scan_packages[(package["ecosystem"], package["name"], package["version"])] = package

    root_component = internal_component(app["name"], app["version"])
    root_component["licenses"] = [{"expression": app["license"]}]
    root_ref = root_component["bom-ref"]
    dependencies[root_ref].update(platform_refs + service_refs)
    action_count = check_action_pins(root, policy["githubActions"], issues)

    component_values = [components[key] for key in sorted(components)]
    identity = "\n".join([root_ref, *(component["bom-ref"] for component in component_values)])
    serial = uuid.uuid5(uuid.NAMESPACE_URL, identity)
    dependency_values = [
        {"ref": ref, "dependsOn": sorted(depends_on)}
        for ref, depends_on in sorted(dependencies.items())
    ]
    for component in component_values:
        if component["bom-ref"] not in dependencies:
            dependency_values.append({"ref": component["bom-ref"], "dependsOn": []})
    dependency_values.sort(key=lambda item: item["ref"])

    sbom = {
        "$schema": "https://cyclonedx.org/schema/bom-1.6.schema.json",
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "authors": [{"name": "HENGJI Engineering"}],
            "component": root_component,
            "properties": [
                {"name": "hengji:release-scope", "value": "windows,android"},
                {"name": "hengji:apple-platforms", "value": "deferred"},
            ],
        },
        "components": component_values,
        "dependencies": dependency_values,
    }
    scan_values = [scan_packages[key] for key in sorted(scan_packages)]
    osv_input = {"results": [{"packages": [{"package": package} for package in scan_values]}]}
    output_dir.mkdir(parents=True, exist_ok=True)
    sbom_path = output_dir / "hengji-windows-android.cdx.json"
    osv_path = output_dir / "osv-scanner-custom.json"
    osv_config_path = output_dir / "osv-scanner.toml"
    write_json(sbom_path, sbom)
    write_json(osv_path, osv_input)
    write_osv_config(
        osv_config_path,
        policy["licenseOverrides"],
        set(scan_packages),
        issues,
        policy_path,
    )
    sbom_hash = hashlib.sha256(sbom_path.read_bytes()).hexdigest().upper()

    return {
        "gate": "supply-chain-inventory",
        "status": "failed" if issues else "passed",
        "testCount": len(component_values) + action_count + 8,
        "componentCount": len(component_values),
        "mavenRuntimeComponentCount": len(maven_records),
        "windowsRuntimeComponentCount": len(platform_records.get("windows", {})),
        "androidRuntimeComponentCount": len(platform_records.get("android", {})),
        "scanPackageCount": len(scan_values),
        "githubActionReferenceCount": action_count,
        "sbom": relative(sbom_path, root),
        "sbomSha256": sbom_hash,
        "scannerInput": relative(osv_path, root),
        "scannerConfig": relative(osv_config_path, root),
        "allowedLicenses": policy["allowedLicenses"],
        "licenseOverrideCount": len(policy["licenseOverrides"]),
        "issues": issues,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate and validate the Windows/Android HENGJI SBOM inventory")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    root = args.project.resolve()
    output_dir = args.output_dir or root / "quality" / "evidence" / "supply-chain"
    if not output_dir.is_absolute():
        output_dir = root / output_dir
    result = check(root, output_dir.resolve())
    print_result(result, args.json)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
