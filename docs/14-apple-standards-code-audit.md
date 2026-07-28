# 恒迹 Apple 标准代码审计与优化报告

日期：2026-07-28（Asia/Shanghai）

## 1. 结论与边界

本轮停止攻击性测试，只进行代码审计、重构和正常工程回归。共享层、Windows、Android 以及可在 Windows 上静态检查的 iOS 源码，已按 Apple 的应用完整性、隐私、系统设置、无障碍、响应性和安全存储原则完成仓库内整改。

当前结论是：**Apple 质量标准静态审计通过，Windows/Android 回归通过；不等于 iOS/macOS、App Store 或法律合规通过。**

Apple 原生仍需 macOS、当前 Xcode、Archive Privacy Report、签名、模拟器/真机、VoiceOver、Dynamic Type、Instruments 和 App Review 证据。Windows 上的 Kotlin/Native 编译还被本机 Application Control 阻断，不能用历史构建替代当前源码结果。

## 2. 审计依据

- [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)：1.6 数据安全、2.1 完整性、2.2 非 Beta/演示提交、2.3 准确元数据、4.2 最低功能、5.1 隐私，以及金融服务提交主体要求。
- [App Review](https://developer.apple.com/app-store/review/)：崩溃、占位内容、支持链接和隐私政策等常见审核问题。
- [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)：一致层级、可预测行为和平台惯例。
- [Settings](https://developer.apple.com/design/human-interface-guidelines/settings)、[Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)、[Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility/)：尊重系统设置、44×44 pt 交互区、破坏性动作角色和辅助功能。
- [App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/)、[Privacy Manifest](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)、[Required Reason API](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api) 和 [第三方 SDK 要求](https://developer.apple.com/support/third-party-SDK-requirements/)。
- [Improving app responsiveness](https://developer.apple.com/documentation/xcode/improving-app-responsiveness) 与 [Apple Platform Security](https://developer.apple.com/security/)。

## 3. 已关闭的不符合项

| ID | 级别 | 原问题 | 整改与证据 |
| --- | --- | --- | --- |
| APPLE-001 | Medium | 发布设置页出现 preview、Beta、等待真实平台和未配置模型提供方等不可执行入口，存在 2.1/2.2 完整性风险 | 删除占位连接器和无提供方支撑的模型同意开关；账本出现授权实时来源时才显示价格通知入口，但已启用用户始终保留关闭入口（`App.kt:537`）。离线解释继续可用，网络外发默认 0 |
| APPLE-002 | Medium | 主题为不可逆布尔开关，不能回到系统模式；Reduce Motion 未统一服从平台设置 | 新增 `SYSTEM/LIGHT/DARK` 三态与可逆解析（`AppAppearance.kt:8`），系统与用户 Reduce Motion 采用 OR（`AppAppearance.kt:21`）；Android 读取动画缩放（`MainActivity.kt:293`），iOS 读取 `UIAccessibilityIsReduceMotionEnabled`（`MainViewController.kt:89`） |
| APPLE-003 | Medium | 本地数据、OCR、第三方组件、保留/删除和未来联网边界缺少应用内可达说明；iOS host 缺 Privacy Manifest | 新增可滚动、带标题语义的隐私说明（`PrivacyNoticeDialog.kt:20-88`）和设置页入口（`SettingsScreen.kt:139`）；加入当前本地模式的 `PrivacyInfo.xcprivacy`，声明不跟踪、不收集、无 Required Reason API |
| APPLE-004 | Medium | 清除/删除确认动作缺少破坏性角色；后台 Worker 与若干平台适配器使用阻塞或过宽异常边界 | 清除/删除按钮使用 error 色（`SettingsScreen.kt:128`、`App.kt:633,768`）；Worker 改为 `CoroutineWorker`（`PriceTargetNotificationWorker.kt:27`）；Android/iOS 文件适配器显式保留取消并只捕获 `Exception`；系统通知权限撤回会同步取消后台评估，已启用用户始终保留应用内关闭入口 |
| APPLE-005 | Medium | `commonMain` 授权报价缓存使用 JVM-only `Math.addExact`，Apple metadata 编译失败 | 改为先验证非负时间和 `Long` 上界，再执行公共 Kotlin 加法（`AuthorizedQuoteCache.kt:54-63`）；新增溢出回归测试 |
| APPLE-006 | Low | quality harness 在 `subst` 驱动器根目录产生空 composite build path，连续 harness 又会复用 Daemon 累积 Metaspace | 显式命名 composite build（`quality/harness/settings.gradle.kts:22-25`），每个 Gradle 门禁启用严格校验、禁用配置缓存并使用单次 Daemon（`run_quality.py:68-72`） |

## 4. Apple 质量要求映射

| 要求 | 当前仓库状态 | 仍需外部完成 |
| --- | --- | --- |
| 完整且可审核 | 占位/Beta/未配置入口已从发布 UI 移除；示例行情始终标为非实时；核心记账、资产、导入、导出、删除和解释功能可执行 | App Store 元数据、截图、审核说明、支持 URL |
| 隐私清晰 | 本地优先、无账号、文件/OCR、ML Kit、保留、导出、删除、撤回与金融服务边界已在应用内披露 | 公开隐私政策 URL、App Privacy 表单、法务签字、Archive Privacy Report |
| 尊重系统 | 主题默认跟随系统且可逆；Reduce Motion 合并平台设置；破坏性动作有区别 | Apple 真机深浅色、Dynamic Type、VoiceOver、后台隐私遮罩 |
| 响应性与恢复 | Durable I/O 保持在网关/后台；Android Worker 使用结构化协程；平台选择器取消不再被通用异常吞掉 | Instruments hangs/hitches、低端 Apple 设备、系统中断恢复 |
| 数据安全 | iOS 静态代码使用 device-only、unlocked Keychain 属性、完整文件保护并排除设备绑定密文备份；共享账本使用 AES-256-GCM | macOS/Xcode 编译、Keychain/文件保护真机往返、锁屏/升级/密钥丢失演练 |
| 金融与法律边界 | 应用内明确只是个人记录和本地分析，不提供银行、支付、信贷、交易、投资、税务或受托理财服务 | 发布主体、许可范围及 Apple 金融条款仍须法务和 App Review 确认 |

## 5. 本轮验证

```powershell
python scripts\quality\run_quality.py --project T:\ `
  --gates formatting architecture release-guards apple-readiness `
  reproducibility supply-chain-inventory coverage malformed-import large-ledger

python skills\build-local-first-finance-app\scripts\validate_finance_app.py --project T:\

.\gradlew.bat desktopTest :apps:client:proguardReleaseJars `
  :apps:client:packageReleaseUberJarForCurrentOS `
  --dependency-verification strict --no-configuration-cache --no-daemon

.\gradlew.bat :modules:core-data:testAndroidHostTest `
  :apps:client:androidApp:lintDebug `
  :apps:client:androidApp:assembleDebug `
  :apps:client:androidApp:assembleRelease `
  :apps:client:androidApp:assembleDebugAndroidTest `
  --dependency-verification strict --no-configuration-cache --no-daemon
```

- Desktop Kotlin：193/193，0 failure/error/skip。
- Android host：63/63；lint、Debug APK、R8 Release APK、AndroidTest APK 通过。
- Android Debug/Release 权限：均不含 `INTERNET`、`READ_SMS`、`RECEIVE_SMS`。
- Apple readiness：13 个文件、42 条静态不变量通过。
- 财务应用校验器：0 error、0 warning（扫描文件数会随 ignored 构建证据变化，不作为完成指标）。
- Connector Gateway：4/4；TypeScript 与 Pyright strict 通过；npm audit 0 vulnerability。
- Price Intelligence：3/3；compileall 和隔离 wheel 构建通过。
- 供应链：276 个包，OSV Scanner 2.3.8 为 0 vulnerability、0 license violation；CycloneDX 1.6 SBOM SHA-256 为 `0600986717420E3B8FD41F717831F018E07A3F88F8C44574B21CC68991856358`。
- 畸形导入：8/8；10 万流水：4/4，97 ms，内存增量 43.82 MiB。后者只是开发机内存基线。

当前重建工件：

| 工件 | SHA-256 | 签名/限制 |
| --- | --- | --- |
| Android Debug APK | `BB941D97E9EBEC0EF7E6063E8908E45DEBD14A46EE110D6A150FABF4761DAB1F` | Android Debug 签名；非商店构建 |
| Android Release APK | `D418A0949AE9C24FE66C90463B10509818304DA8E661B0D1336A5F1F38703BAB` | R8；未签名 |
| AndroidTest APK | `311FF1325379DBF90866EF028B624078B721785DA90AE644AAF3C4D427471718` | 测试工件 |
| Windows Release JAR | `9740BD8C2587345657D0FD682DC81B066EDAB2CE9A2136BF74C0CA85DA8874FA` | Release 混淆构建；不是签名安装包 |

## 6. 未验证与阻塞项

1. 本轮 `compileIosMainKotlinMetadata` 已先发现并修复 `commonMain` 的 JVM-only `Math`；重跑后进入 `core-data` iOS metadata，随后被 Windows Application Control 阻断 Kotlin/Native 临时 DLL。当前 Apple 源码必须在 macOS/Xcode 重新编译。
2. `PrivacyInfo.xcprivacy` 反映当前本地代码边界，但 Xcode 归档可能合并第三方 SDK 的 manifest 和 Required Reason API；必须以最终 Archive Privacy Report 为准。
3. 需要公开隐私政策、支持联系方式、App Privacy/Accessibility Nutrition Labels、审核说明、截图和完整元数据。
4. 需要 Apple 真机/模拟器验证文件选择、导入/导出取消和超限、Keychain、文件保护、锁屏、卸载/恢复、VoiceOver、Dynamic Type、Reduce Motion、深浅色、键盘和 Instruments。
5. 需要确认发布主体与 Apple 金融服务条款；仓库内的“非银行/支付/投资服务”说明不能替代法律意见或 App Review 决定。
6. iOS/macOS 签名、entitlement、Sandbox、Hardened Runtime、公证、TestFlight/App Store 和回滚演练均未完成。
