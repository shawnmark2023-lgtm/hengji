from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

from common import command_text, project_root, run_command


def ratio(counter: ElementTree.Element | None) -> tuple[int, int, float]:
    if counter is None:
        return 0, 0, 1.0
    missed = int(counter.attrib["missed"])
    covered = int(counter.attrib["covered"])
    total = missed + covered
    return covered, total, covered / total if total else 1.0


def gradle_executable(root: Path) -> Path:
    return root / ("gradlew.bat" if sys.platform == "win32" else "gradlew")


def java_executable() -> str:
    executable = shutil.which("java")
    if executable is None:
        raise RuntimeError("Java is required for the JaCoCo coverage gate")
    return executable


def run_checked(command: list[str], cwd: Path) -> tuple[bool, str]:
    completed = run_command(command, cwd)
    return completed.returncode == 0, completed.stdout


def report_target(
    *,
    root: Path,
    cli: Path,
    execution_files: list[Path],
    target: dict[str, Any],
    report_root: Path,
) -> tuple[dict[str, Any], str]:
    target_id = str(target["id"])
    class_directory = root / str(target["classDirectory"])
    source_directory = root / str(target["sourceDirectory"])
    xml_report = report_root / f"{target_id}.xml"
    html_report = report_root / target_id
    xml_report.parent.mkdir(parents=True, exist_ok=True)
    command = [java_executable(), "-jar", str(cli), "report"]
    command.extend(str(path) for path in execution_files)
    command.extend(
        [
            "--name",
            f"HENGJI {target_id}",
            "--classfiles",
            str(class_directory),
            "--sourcefiles",
            str(source_directory),
            "--xml",
            str(xml_report),
            "--html",
            str(html_report),
        ]
    )
    completed = run_command(command, root)
    if completed.returncode != 0:
        return (
            {
                "id": target_id,
                "status": "failed",
                "command": command_text(command),
                "error": completed.stdout.splitlines()[-40:],
            },
            completed.stdout,
        )

    report = ElementTree.parse(xml_report).getroot()
    line = ratio(report.find("./counter[@type='LINE']"))
    branch = ratio(report.find("./counter[@type='BRANCH']"))
    line_minimum = float(target["minimumLineRatio"])
    branch_minimum = float(target["minimumBranchRatio"])
    passed = line[2] >= line_minimum and branch[2] >= branch_minimum
    return (
        {
            "id": target_id,
            "status": "passed" if passed else "failed",
            "line": {"covered": line[0], "total": line[1], "ratio": round(line[2], 6), "minimum": line_minimum},
            "branch": {
                "covered": branch[0],
                "total": branch[1],
                "ratio": round(branch[2], 6),
                "minimum": branch_minimum,
            },
            "xmlReport": xml_report.relative_to(root).as_posix(),
            "htmlReport": html_report.relative_to(root).as_posix(),
        },
        completed.stdout,
    )


def check(root: Path, report_root: Path) -> tuple[dict[str, Any], str]:
    policy_path = root / "quality" / "coverage-policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    targets = policy["targets"]
    tool_project = root / "quality" / "coverage-tools"
    gradle = gradle_executable(root)
    prepare_command = [
        str(gradle),
        "-p",
        str(tool_project),
        "prepareCoverageTools",
        "--dependency-verification",
        "strict",
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
    ]
    ok, output = run_checked(prepare_command, root)
    if not ok:
        return (
            {
                "gate": "coverage",
                "status": "failed",
                "testCount": 0,
                "stage": "prepare-tools",
                "command": command_text(prepare_command),
                "issues": [{"message": "Could not prepare locked JaCoCo tools"}],
            },
            output,
        )

    tools = tool_project / "build" / "tools"
    agent = tools / "jacocoagent.jar"
    cli = tools / "jacococli.jar"
    execution_root = report_root / "execution"
    if execution_root.exists():
        shutil.rmtree(execution_root)
    execution_root.mkdir(parents=True)
    covered_tasks = [str(target["testTask"]) for target in targets]
    test_command = [
        str(gradle),
        "--init-script",
        str(tool_project / "instrument-tests.init.gradle.kts"),
        *covered_tasks,
        f"-PhengjiCoverageAgent={agent}",
        f"-PhengjiCoverageDirectory={execution_root}",
        f"-PhengjiCoverageTasks={','.join(covered_tasks)}",
        "--rerun-tasks",
        "--dependency-verification",
        "strict",
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
    ]
    test_ok, test_output = run_checked(test_command, root)
    output += test_output
    execution_files = sorted(execution_root.glob("*.exec"))
    if not test_ok or len(execution_files) != len(targets):
        return (
            {
                "gate": "coverage",
                "status": "failed",
                "testCount": 0,
                "stage": "tests",
                "command": command_text(test_command),
                "executionFiles": [path.relative_to(root).as_posix() for path in execution_files],
                "issues": [{"message": "Instrumented tests failed or did not produce one execution file per target"}],
            },
            output,
        )

    results: list[dict[str, Any]] = []
    for target in targets:
        result, report_output = report_target(
            root=root,
            cli=cli,
            execution_files=execution_files,
            target=target,
            report_root=report_root,
        )
        output += report_output
        results.append(result)
    status = "passed" if all(result["status"] == "passed" for result in results) else "failed"
    return (
        {
            "gate": "coverage",
            "status": status,
            "testCount": len(results) * 2,
            "engine": policy["engine"],
            "scope": policy["scope"],
            "testCommand": command_text(test_command),
            "targets": results,
            "issues": [
                {
                    "target": result["id"],
                    "message": "Line or branch coverage is below its committed threshold",
                }
                for result in results
                if result["status"] != "passed"
            ],
        },
        output,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Run shared Windows/Android coverage thresholds")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--report-dir", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    root = Path(os.path.abspath(args.project))
    report_root = Path(os.path.abspath(args.report_dir or root / "quality" / "evidence" / "coverage"))
    try:
        result, output = check(root, report_root)
    except (OSError, RuntimeError, ValueError, ElementTree.ParseError, subprocess.SubprocessError) as error:
        result = {
            "gate": "coverage",
            "status": "failed",
            "testCount": 0,
            "issues": [{"message": str(error)}],
        }
        output = ""
    if not args.json and output:
        print(output, end="" if output.endswith("\n") else "\n")
    if args.json:
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
    else:
        print(f"{result['status'].upper()} coverage")
        for target in result.get("targets", []):
            print(
                f"  {target['id']}: line {target['line']['ratio']:.1%} "
                f"(min {target['line']['minimum']:.0%}), branch {target['branch']['ratio']:.1%} "
                f"(min {target['branch']['minimum']:.0%})"
            )
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
