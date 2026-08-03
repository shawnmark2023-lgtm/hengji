from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from common import IGNORED_PARTS, iter_source_files, print_result, project_root, relative


RISKY_SUFFIXES = {".p12", ".pfx", ".jks", ".keystore", ".pem", ".key"}
RISKY_NAMES = {".env", "id_rsa", "id_ed25519"}
SECRET_PATTERNS = {
    "SECRET-PRIVATE-KEY": re.compile(r"BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY"),
    "SECRET-AWS-ACCESS": re.compile(r"AKIA[0-9A-Z]{16}"),
    "SECRET-GITHUB-TOKEN": re.compile(r"gh[oprsu]_[A-Za-z0-9]{36,}"),
    "SECRET-GOOGLE-API": re.compile(r"AIza[0-9A-Za-z_-]{35}"),
    "SECRET-SLACK-TOKEN": re.compile(r"xox[baprs]-[0-9A-Za-z-]{20,}"),
}
TEXT_SUFFIXES = {
    ".kt", ".kts", ".swift", ".ts", ".tsx", ".js", ".py", ".rs", ".json",
    ".yaml", ".yml", ".toml", ".properties", ".xml", ".plist", ".xcconfig",
}
PRODUCTION_PARTS = {"prod", "production", "release"}
PRODUCTION_FORBIDDEN = re.compile(
    r"(?i)(?:localhost|127\.0\.0\.1|\.invalid(?:[/:]|$)|/sandbox(?:[/?]|$)|demo[_-]?non[_-]?live)"
)


def source_files(root: Path) -> list[Path]:
    return list(iter_source_files(root, TEXT_SUFFIXES))


def add_issue(issues: list[dict[str, Any]], rule: str, path: Path, message: str, line: int | None = None) -> None:
    issue: dict[str, Any] = {"rule": rule, "path": relative(path), "message": message}
    if line is not None:
        issue["line"] = line
    issues.append(issue)


def check(root: Path) -> dict[str, Any]:
    issues: list[dict[str, Any]] = []
    all_files = [
        path for path in root.rglob("*")
        if path.is_file() and not any(part in IGNORED_PARTS for part in path.parts)
    ]

    for path in all_files:
        if path.name.lower() in RISKY_NAMES or path.suffix.lower() in RISKY_SUFFIXES:
            add_issue(issues, "SECRET-FILE", path, "Credential-like file is committed in the project tree")

    texts = source_files(root)
    for path in texts:
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            add_issue(issues, "SOURCE-UTF8", path, "Source/configuration file is not valid UTF-8")
            continue
        for rule, pattern in SECRET_PATTERNS.items():
            for match in pattern.finditer(content):
                add_issue(
                    issues,
                    rule,
                    path,
                    "Credential signature found",
                    content.count("\n", 0, match.start()) + 1,
                )

        relative_parts = {part.lower() for part in path.relative_to(root).parts}
        if relative_parts & PRODUCTION_PARTS:
            for match in PRODUCTION_FORBIDDEN.finditer(content):
                add_issue(
                    issues,
                    "PRODUCTION-SANDBOX-ENDPOINT",
                    path,
                    "Production/release source contains a sandbox, demo, localhost, or reserved endpoint",
                    content.count("\n", 0, match.start()) + 1,
                )

        if "test" not in relative_parts:
            lines = content.splitlines()
            for index, line in enumerate(lines):
                if re.search(r"QuoteProvenance\.DEMO|DEMO_NON_LIVE", line):
                    window = "\n".join(lines[index:index + 14])
                    if re.search(r"isLive\s*=\s*true", window):
                        add_issue(
                            issues,
                            "DEMO-LIVE-CONTRADICTION",
                            path,
                            "Demo quote is marked live",
                            index + 1,
                        )
                if re.search(r"Availability\.SANDBOX|availability\s*=\s*[^,]*SANDBOX", line, re.IGNORECASE):
                    window = "\n".join(lines[index:index + 14])
                    if re.search(r"(?:production|isLive\s*=\s*true)", window, re.IGNORECASE):
                        add_issue(
                            issues,
                            "SANDBOX-PRODUCTION-CONTRADICTION",
                            path,
                            "Sandbox connector is marked production/live",
                            index + 1,
                        )

    gateway = root / "services" / "connector-gateway" / "src" / "server.ts"
    if gateway.is_file():
        content = gateway.read_text(encoding="utf-8")
        if not re.search(r"mode\s*===\s*[\"']production[\"'][\s\S]{0,300}?throw\s+new\s+Error", content):
            add_issue(
                issues,
                "PRODUCTION-FAIL-CLOSED",
                gateway,
                "Connector gateway does not visibly reject unconfigured production mode",
            )

    token_vault = root / "modules" / "connectors" / "src" / "commonMain" / "kotlin" / "com" / "hengji" / "connectors" / "TokenVault.kt"
    if not token_vault.is_file():
        add_issue(issues, "TOKEN-VAULT-PORT", token_vault, "Token vault port is missing")
    else:
        content = token_vault.read_text(encoding="utf-8")
        if "interface TokenVault" not in content or "DisabledTokenVault" not in content:
            add_issue(issues, "TOKEN-VAULT-PORT", token_vault, "Token vault or fail-closed prototype implementation is missing")

    desktop_build = root / "apps" / "client" / "build.gradle.kts"
    if not desktop_build.is_file():
        add_issue(issues, "WINDOWS-PACKAGING", desktop_build, "Desktop packaging configuration is missing")
    else:
        content = desktop_build.read_text(encoding="utf-8")
        required_packaging = {
            "per-user installation": r"perUserInstall\s*=\s*true",
            "install/data directory separation": r'installationPath\s*=\s*"HengjiApp"',
            "stable upgrade UUID": r'upgradeUuid\s*=\s*"b2248acb-5ced-48a7-b69f-3b4f34571acf"',
            "version override": r'gradleProperty\("hengji\.packageVersion"\)',
        }
        for requirement, pattern in required_packaging.items():
            if not re.search(pattern, content):
                add_issue(
                    issues,
                    "WINDOWS-PACKAGING",
                    desktop_build,
                    f"Windows packaging must preserve {requirement}",
                )

    ci_state = root / "quality" / "ci-gates.json"
    if not ci_state.is_file():
        add_issue(issues, "CI-TRUTH", ci_state, "Configured CI truth registry is missing")
    else:
        value = json.loads(ci_state.read_text(encoding="utf-8"))
        for gate in value.get("gates", []):
            if gate.get("status") == "passed":
                add_issue(issues, "CI-TRUTH", ci_state, f"Repository configuration falsely marks {gate.get('id')} passed")
            if gate.get("status") != "configured":
                add_issue(issues, "CI-TRUTH", ci_state, f"CI registry entry must remain configured until run evidence exists: {gate.get('id')}")
            workflow = root / str(gate.get("workflow", ""))
            if not workflow.is_file():
                add_issue(issues, "CI-CONFIGURATION", ci_state, f"Configured workflow is missing: {gate.get('workflow')}")
                continue
            workflow_text = workflow.read_text(encoding="utf-8")
            for job_id in gate.get("jobIds", []):
                if not re.search(rf"(?m)^  {re.escape(str(job_id))}:\s*$", workflow_text):
                    add_issue(
                        issues,
                        "CI-CONFIGURATION",
                        ci_state,
                        f"Configured job id is missing from {gate.get('workflow')}: {job_id}",
                    )

    return {
        "gate": "release-guards",
        "status": "failed" if issues else "passed",
        "checkedFiles": len(all_files),
        "testCount": len(all_files),
        "issues": issues,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = check(args.project.resolve())
    print_result(result, args.json)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
