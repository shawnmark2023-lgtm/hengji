#!/usr/bin/env python3
"""Static handoff checks for a local-first finance app repository."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


IGNORED_PARTS = {".git", ".gradle", "build", "dist", "node_modules", "__pycache__"}
TEXT_SUFFIXES = {".kt", ".kts", ".swift", ".ts", ".tsx", ".py", ".rs", ".md", ".yaml", ".yml", ".toml"}
SECRET_SUFFIXES = {".p12", ".pfx", ".jks", ".keystore", ".pem"}


def files(root: Path):
    for path in root.rglob("*"):
        if path.is_file() and not any(part in IGNORED_PARTS for part in path.parts):
            yield path


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, required=True)
    args = parser.parse_args()
    root = args.project.resolve()
    if not root.is_dir():
        print(f"ERROR project is not a directory: {root}")
        return 2

    all_files = list(files(root))
    text_files = [path for path in all_files if path.suffix.lower() in TEXT_SUFFIXES]
    corpus = "\n".join(read_text(path) for path in text_files)
    errors: list[str] = []
    warnings: list[str] = []

    risky = [path for path in all_files if path.suffix.lower() in SECRET_SUFFIXES or path.name == ".env"]
    if risky:
        errors.append("credential-like files present: " + ", ".join(str(path.relative_to(root)) for path in risky[:8]))

    private_key = re.compile(r"BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}")
    for path in text_files:
        if private_key.search(read_text(path)):
            errors.append(f"credential signature found in {path.relative_to(root)}")

    doc_names = " ".join(path.name.lower() for path in all_files if path.suffix.lower() == ".md")
    for label, variants in {
        "plan": ("plan", "方案"),
        "architecture": ("architecture", "架构"),
        "threat model": ("threat", "security", "威胁"),
        "test report": ("test", "qa", "测试"),
    }.items():
        if not any(variant in doc_names or variant in corpus.lower() for variant in variants):
            warnings.append(f"no obvious {label} documentation")

    lower = corpus.lower()
    if not ("minorunits" in lower or "minor_units" in lower or "minor units" in lower):
        warnings.append("no explicit integer minor-unit money representation found")
    if not all(token in lower for token in ("demo", "live")):
        warnings.append("demo/live source distinction is not obvious")
    if "pkce" not in lower or "state" not in lower:
        warnings.append("OAuth PKCE/state design is not obvious")
    if not any(token in lower for token in ("fail-closed", "fail closed", "fail_closed")):
        warnings.append("production fail-closed behavior is not documented")

    domain_float = re.compile(r"(?i)(money|amount|price|cost).{0,80}\b(double|float)\b|\b(double|float)\b.{0,80}(money|amount|price|cost)")
    for path in text_files:
        relative_path = path.relative_to(root)
        source_parts = {part.lower() for part in relative_path.parts}
        is_financial_core = "domain" in source_parts or "core-domain" in source_parts or "ledger" in path.stem.lower()
        if is_financial_core and domain_float.search(read_text(path)):
            warnings.append(f"review floating-point financial arithmetic in {path.relative_to(root)}")

    for message in errors:
        print(f"ERROR {message}")
    for message in sorted(set(warnings)):
        print(f"WARN  {message}")
    print(f"SUMMARY files={len(all_files)} errors={len(errors)} warnings={len(set(warnings))}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
