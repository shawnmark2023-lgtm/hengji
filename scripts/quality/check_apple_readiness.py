from __future__ import annotations

import argparse
import plistlib
from pathlib import Path
from typing import Any

from common import print_result, project_root, relative


def check(root: Path) -> dict[str, Any]:
    issues: list[dict[str, str]] = []
    checked_paths: set[Path] = set()
    check_count = 0

    def issue(rule: str, path: Path, message: str) -> None:
        issues.append({"rule": rule, "path": relative(path), "message": message})

    def read(relative_path: str) -> tuple[Path, str]:
        path = root / relative_path
        checked_paths.add(path)
        if not path.is_file():
            issue("APPLE-REQUIRED-FILE", path, "Required Apple-readiness source is missing")
            return path, ""
        return path, path.read_text(encoding="utf-8")

    def require_text(relative_path: str, needle: str, rule: str, message: str) -> None:
        nonlocal check_count
        check_count += 1
        path, content = read(relative_path)
        if needle not in content:
            issue(rule, path, message)

    def forbid_text(relative_path: str, needle: str, rule: str, message: str) -> None:
        nonlocal check_count
        check_count += 1
        path, content = read(relative_path)
        if needle in content:
            issue(rule, path, message)

    privacy_path = root / "apps/client/iosApp/iosApp/PrivacyInfo.xcprivacy"
    checked_paths.add(privacy_path)
    check_count += 1
    if not privacy_path.is_file():
        issue("APPLE-PRIVACY-MANIFEST", privacy_path, "The iOS host privacy manifest is missing")
    else:
        try:
            manifest = plistlib.loads(privacy_path.read_bytes())
        except (plistlib.InvalidFileException, ValueError) as error:
            issue("APPLE-PRIVACY-MANIFEST", privacy_path, f"Privacy manifest is invalid: {error}")
        else:
            expected = {
                "NSPrivacyTracking": False,
                "NSPrivacyTrackingDomains": [],
                "NSPrivacyCollectedDataTypes": [],
                "NSPrivacyAccessedAPITypes": [],
            }
            for key, value in expected.items():
                check_count += 1
                if manifest.get(key) != value:
                    issue(
                        "APPLE-PRIVACY-MANIFEST",
                        privacy_path,
                        f"{key} must match the current local-only app behavior",
                    )
            unexpected = sorted(set(manifest) - set(expected))
            if unexpected:
                issue(
                    "APPLE-PRIVACY-MANIFEST",
                    privacy_path,
                    f"Unexpected privacy-manifest keys require explicit review: {', '.join(unexpected)}",
                )

    settings = "apps/client/src/commonMain/kotlin/com/hengji/app/ui/screens/SettingsScreen.kt"
    require_text(settings, "AppAppearanceMode.SYSTEM", "APPLE-SYSTEM-SETTINGS", "Appearance must offer a system-following mode")
    require_text(settings, "查看隐私说明", "APPLE-PRIVACY-DISCLOSURE", "Privacy disclosure must be reachable in the app")
    require_text(
        settings,
        "MaterialTheme.colorScheme.error",
        "APPLE-DESTRUCTIVE-ACTION",
        "The clear-data action must use destructive styling",
    )
    for placeholder in (
        "Local-first preview",
        "Beta ·",
        "等待平台真实",
        "Apple FinanceKit",
        "可选模型解释",
    ):
        forbid_text(
            settings,
            placeholder,
            "APPLE-APP-COMPLETENESS",
            f"Shipping settings contain incomplete or beta placeholder content: {placeholder}",
        )

    notice = "apps/client/src/commonMain/kotlin/com/hengji/app/ui/screens/PrivacyNoticeDialog.kt"
    for disclosure in (
        "不要求账户",
        "原文件和 OCR 原文不保存",
        "Google ML Kit",
        "保留、导出与删除",
        "未来任何联网、账户或同步功能都必须另行说明用途并重新取得同意",
        "不提供银行、支付、信贷、证券交易、投资、税务或受托理财服务",
    ):
        require_text(
            notice,
            disclosure,
            "APPLE-PRIVACY-DISCLOSURE",
            f"Privacy notice is missing a required disclosure: {disclosure}",
        )

    app = "apps/client/src/commonMain/kotlin/com/hengji/app/App.kt"
    require_text(
        app,
        "AppAppearanceMode.SYSTEM",
        "APPLE-SYSTEM-SETTINGS",
        "The app must default to the platform appearance",
    )
    require_text(
        app,
        "shouldReduceMotion",
        "APPLE-SYSTEM-SETTINGS",
        "The app must combine system and app-specific reduced-motion preferences",
    )
    require_text(
        app,
        "ButtonDefaults.textButtonColors",
        "APPLE-DESTRUCTIVE-ACTION",
        "Destructive confirmation buttons must be visually distinguished",
    )
    require_text(
        app,
        "priceNotificationControl?.takeIf",
        "APPLE-APP-COMPLETENESS",
        "Price notifications must remain hidden until actionable authorized quotes exist",
    )
    require_text(
        app,
        "control.shouldDisplay",
        "APPLE-PRIVACY-CONTROL",
        "An enabled notification consent must keep its revocation control reachable",
    )
    forbid_text(
        app,
        "catch (error: Throwable)",
        "APPLE-FAULT-ISOLATION",
        "UI operations must not swallow fatal runtime failures",
    )

    dormant_model_control = root / (
        "apps/client/src/commonMain/kotlin/com/hengji/app/application/ModelExplanationControl.kt"
    )
    checked_paths.add(dormant_model_control)
    check_count += 1
    if dormant_model_control.exists():
        issue(
            "APPLE-APP-COMPLETENESS",
            dormant_model_control,
            "Unimplemented model-provider consent must not ship as a dormant feature",
        )

    worker = "apps/client/androidApp/src/main/kotlin/com/hengji/app/PriceTargetNotificationWorker.kt"
    require_text(worker, "CoroutineWorker", "APPLE-RESPONSIVENESS", "Background work must use a coroutine-first worker")
    for blocking in ("runBlocking", "runCatching", "catch (error: Throwable)"):
        forbid_text(
            worker,
            blocking,
            "APPLE-RESPONSIVENESS",
            f"Background worker contains a blocking or overbroad failure boundary: {blocking}",
        )

    android_activity = "apps/client/androidApp/src/main/kotlin/com/hengji/app/MainActivity.kt"
    require_text(
        android_activity,
        "Settings.Global.ANIMATOR_DURATION_SCALE",
        "APPLE-SYSTEM-SETTINGS",
        "Android must honor the platform reduced-animation preference",
    )
    require_text(
        android_activity,
        "系统通知权限已撤回；本地后台评估已取消。",
        "APPLE-PRIVACY-CONTROL",
        "Revoking the system notification permission must also cancel local background evaluation",
    )
    require_text(
        "apps/client/src/iosMain/kotlin/com/hengji/app/MainViewController.kt",
        "UIAccessibilityIsReduceMotionEnabled()",
        "APPLE-SYSTEM-SETTINGS",
        "iOS must honor the platform Reduce Motion preference",
    )
    for ios_adapter in (
        "apps/client/src/iosMain/kotlin/com/hengji/app/IosImportDocumentPicker.kt",
        "apps/client/src/iosMain/kotlin/com/hengji/app/IosLedgerExportWriter.kt",
    ):
        for broad_boundary in ("runCatching", "catch (failure: Throwable)"):
            forbid_text(
                ios_adapter,
                broad_boundary,
                "APPLE-FAULT-ISOLATION",
                f"iOS adapter contains an overbroad failure boundary: {broad_boundary}",
            )

    require_text(
        "modules/core-data/src/iosMain/kotlin/com/hengji/data/IosKeychainDatabaseKeyProvider.kt",
        "kSecAttrAccessibleWhenUnlockedThisDeviceOnly",
        "APPLE-KEYCHAIN",
        "iOS ledger keys must be device-only and available only while unlocked",
    )
    require_text(
        "modules/core-data/src/iosMain/kotlin/com/hengji/data/IosKeychainDatabaseKeyProvider.kt",
        "kSecAttrSynchronizable, kCFBooleanFalse",
        "APPLE-KEYCHAIN",
        "iOS ledger keys must not synchronize through iCloud Keychain",
    )
    require_text(
        "modules/core-data/src/iosMain/kotlin/com/hengji/data/IosAtomicProtectedLedgerStore.kt",
        "NSFileProtectionComplete",
        "APPLE-DATA-PROTECTION",
        "The iOS encrypted ledger must use complete file protection",
    )
    require_text(
        "modules/core-data/src/iosMain/kotlin/com/hengji/data/IosAtomicProtectedLedgerStore.kt",
        "NSURLIsExcludedFromBackupKey",
        "APPLE-DATA-PROTECTION",
        "Device-bound encrypted ledger data must be excluded from backup",
    )
    require_text(
        "apps/client/iosApp/README.md",
        "PrivacyInfo.xcprivacy",
        "APPLE-PRIVACY-MANIFEST",
        "The iOS host instructions must include the privacy manifest in target resources",
    )

    return {
        "gate": "apple-readiness",
        "status": "failed" if issues else "passed",
        "checkedFiles": len(checked_paths),
        "testCount": check_count,
        "issues": issues,
        "limitations": [
            "Static source audit only; Xcode archive privacy report, signing, App Review metadata, and Apple-device validation require macOS and external credentials",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Check Apple application-quality readiness invariants")
    parser.add_argument("--project", type=Path, default=project_root())
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = check(args.project.resolve())
    print_result(result, args.json)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
