from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from common import IGNORED_PARTS, project_root, relative


SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".ts"}
LEADING_TABS = re.compile(r"^(?P<indent>[ \t]*\t[ \t]*)")


def source_files(root: Path) -> list[Path]:
    return sorted(
        (
            path
            for path in root.rglob("*")
            if path.is_file()
            and path.suffix.lower() in SOURCE_SUFFIXES
            and not any(part in IGNORED_PARTS for part in path.parts)
        ),
        key=lambda path: relative(path, root),
    )


def normalize(content: str) -> str:
    content = content.removeprefix("\ufeff").replace("\r\n", "\n").replace("\r", "\n")
    normalized: list[str] = []
    blank_count = 0
    for original in content.split("\n"):
        line = original.rstrip(" \t")
        match = LEADING_TABS.match(line)
        if match:
            indent = match.group("indent").expandtabs(4)
            line = indent + line[len(match.group("indent")) :]
        if line:
            blank_count = 0
            normalized.append(line)
        elif blank_count < 2:
            normalized.append("")
            blank_count += 1
    while normalized and normalized[-1] == "":
        normalized.pop()
    return "\n".join(normalized) + "\n"


def inspect(path: Path, root: Path) -> list[dict[str, Any]]:
    raw = path.read_bytes()
    issues: list[dict[str, Any]] = []
    location = relative(path, root)
    try:
        content = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        return [
            {
                "rule": "FORMAT-UTF8",
                "path": location,
                "line": None,
                "message": f"Source is not valid UTF-8: {error}",
            }
        ]

    if content.startswith("\ufeff"):
        issues.append(
            {
                "rule": "FORMAT-BOM",
                "path": location,
                "line": 1,
                "message": "UTF-8 BOM is not allowed",
            }
        )
    if b"\r" in raw:
        first = content[: raw.index(b"\r")].count("\n") + 1
        issues.append(
            {
                "rule": "FORMAT-LF",
                "path": location,
                "line": first,
                "message": "Use LF line endings",
            }
        )
    if content and not content.endswith("\n"):
        issues.append(
            {
                "rule": "FORMAT-FINAL-NEWLINE",
                "path": location,
                "line": content.count("\n") + 1,
                "message": "Source must end with exactly one newline",
            }
        )

    blank_count = 0
    for line_number, line in enumerate(content.replace("\r\n", "\n").replace("\r", "\n").split("\n"), 1):
        if line.rstrip(" \t") != line:
            issues.append(
                {
                    "rule": "FORMAT-TRAILING-WHITESPACE",
                    "path": location,
                    "line": line_number,
                    "message": "Trailing whitespace is not allowed",
                }
            )
        if LEADING_TABS.match(line):
            issues.append(
                {
                    "rule": "FORMAT-INDENT",
                    "path": location,
                    "line": line_number,
                    "message": "Indent with spaces, not tabs",
                }
            )
        if line:
            blank_count = 0
        else:
            blank_count += 1
            if blank_count == 3:
                issues.append(
                    {
                        "rule": "FORMAT-BLANK-LINES",
                        "path": location,
                        "line": line_number,
                        "message": "At most two consecutive blank lines are allowed",
                    }
                )
    return issues


def check(root: Path, fix: bool) -> dict[str, Any]:
    files = source_files(root)
    changed: list[str] = []
    for path in files:
        if fix:
            content = path.read_text(encoding="utf-8")
            normalized = normalize(content)
            if normalized != content:
                path.write_text(normalized, encoding="utf-8", newline="\n")
                changed.append(relative(path, root))
    issues = [issue for path in files for issue in inspect(path, root)]
    return {
        "gate": "formatting",
        "status": "failed" if issues else "passed",
        "checkedFiles": len(files),
        "testCount": len(files),
        "languages": {
            "kotlin": sum(path.suffix.lower() in {".kt", ".kts"} for path in files),
            "python": sum(path.suffix.lower() == ".py" for path in files),
            "typescript": sum(path.suffix.lower() == ".ts" for path in files),
        },
        "fixedFiles": changed,
        "issues": issues,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Check the shared HENGJI source formatting contract")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--fix", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = check(args.project.resolve(), args.fix)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    else:
        print(f"{result['status'].upper()} formatting ({result['checkedFiles']} files)")
        for issue in result["issues"]:
            line = f":{issue['line']}" if issue["line"] else ""
            print(f"  {issue['rule']} {issue['path']}{line} {issue['message']}")
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
