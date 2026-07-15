from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from common import print_result, project_root, relative


def load_policy(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("schemaVersion") != 1 or not isinstance(value.get("rules"), list):
        raise ValueError(f"Unsupported architecture policy: {path}")
    return value


def check(root: Path, policy_path: Path) -> dict[str, Any]:
    policy = load_policy(policy_path)
    issues: list[dict[str, Any]] = []
    checked_files: set[str] = set()
    for rule in policy["rules"]:
        patterns = [re.compile(pattern, re.MULTILINE) for pattern in rule["forbiddenRegex"]]
        for include in rule["include"]:
            for path in root.glob(include):
                if not path.is_file():
                    continue
                checked_files.add(relative(path, root))
                content = path.read_text(encoding="utf-8")
                for pattern in patterns:
                    for match in pattern.finditer(content):
                        line = content.count("\n", 0, match.start()) + 1
                        snippet = content.splitlines()[line - 1].strip()
                        issues.append(
                            {
                                "rule": rule["id"],
                                "path": relative(path, root),
                                "line": line,
                                "message": f"{rule['description']} Matched: {snippet}",
                            }
                        )
    return {
        "gate": "architecture",
        "status": "failed" if issues else "passed",
        "checkedFiles": len(checked_files),
        "testCount": len(checked_files),
        "issues": issues,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--policy", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    root = args.project.resolve()
    policy = (args.policy or root / "quality" / "architecture-policy.json").resolve()
    result = check(root, policy)
    print_result(result, args.json)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
