# 测试与构建报告

验证日期：2026-07-25（Asia/Shanghai）。环境：Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13。本文只记录实际执行结果，不把工程入口、调试签名或未做生产签名的产物等同于商店发布。

## 自动化结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Kotlin Desktop | 77/77 通过，0 failure/error/skip | client 20、core-domain 12、core-data 20、core-insights 17、connectors 8 |
| Room 持久层 | core-data 20/20；其中 Room Desktop 6/6 | 9 表 schema v2、事务写入、显式 1→2 迁移、洞察偏好覆盖/重置/跨重启、25 MiB 上限、production fail-closed |
| Android | Debug APK 构建通过 | `:apps:client:androidApp:assembleDebug` |
| Android 签名 | v2 验证通过，1 个 signer | `CN=Android Debug`；不是生产发布签名，未做设备安装/启动 |
| iOS 元数据 | 编译通过 | client/core-data 的 iOS source-set 元数据；不是 Xcode 原生构建证据 |
| 架构与发布守卫 | 30/30、178/178 通过 | 依赖方向、secret、沙箱/production 标签与禁止行为扫描 |
| 畸形导入 | 8/8 通过 | 引号未闭合、错列、重复表头、嵌套 JSON、行/文件上限、空必填、BOM/Unicode |
| 10 万流水开发基线 | 4/4 通过 | 100,000 行，121 ms，内存增量 43.21 MiB；不是代表性设备/加密数据库证据 |
| Connector gateway | 4/4 通过；`npm audit` 0 vulnerability | state 一次性/过期、沙箱非实时、production fail-closed |
| Price intelligence | 3/3 通过 | 中位数/四分位、离群过滤、新鲜度与低置信度行为 |
| Release 混淆 | 本轮 `proguardReleaseJars` 构建通过；既有 Release 实跑通过 | 保留 Room/领域 ABI 与 SQLite JNI；修复了仅 Release 暴露的三类崩溃 |

主要命令：

```powershell
.\gradlew.bat desktopTest --no-daemon
.\gradlew.bat :apps:client:androidApp:assembleDebug --no-daemon
.\gradlew.bat :apps:client:compileIosMainKotlinMetadata :modules:core-data:compileIosMainKotlinMetadata --no-daemon
python scripts/quality/run_quality.py --output-dir quality/evidence
.\gradlew.bat :apps:client:proguardReleaseJars --no-daemon
Push-Location services\connector-gateway; npm test; npm audit --audit-level=high; Pop-Location
Push-Location services\price-intelligence; python -m pytest -q; Pop-Location
apksigner verify --verbose --print-certs artifacts\hengji-android-debug.apk
```

本工作树路径包含中文字符，当前 Windows Gradle Test Worker 会把该路径错误编码并导致测试类加载失败；因此本轮 `desktopTest --rerun-tasks` 在同一源码状态的 ASCII 隔离副本中执行。生产源码路径上的 Desktop/Android/iOS 元数据编译及 Android APK 构建均直接通过。

Windows MSI 在 21:07（Asia/Shanghai）用 JDK 21 `jpackage` 与便携 WiX Toolset 3.14 重建；本轮实际命令为：

```powershell
$env:PATH="<work>\wix3-portable\WiX Toolset v3.14\bin;$env:PATH"
jpackage --type msi --app-image <work>\hengji-repackage\Hengji --dest <work>\hengji-repackage\msi
msiexec /a artifacts\hengji-windows-0.1.0.msi /qn TARGETDIR=<work>\hengji-repackage-msi-extract
```

`msiexec` 返回 0；从行政解包结果启动的进程保持运行。这里的 `<work>` 是本次会话的隔离工作目录，不是源码或发行包的一部分。

## 真实 UI 验收

1. 使用隔离 `HENGJI_DATA_DIR` 启动桌面开发运行，新增“UI持久化测试 / ¥12.34”。
2. 关闭并重启同一路径：首页本月支出从 ¥261.80 变为 ¥274.14，流水列表仍显示该记录。
3. 打开导入中心，选择明确标注“沙箱·非生产”的 CSV；自动映射字段、预览 3 笔、确认原子写入。
4. 在完成页整批撤销，界面显示已移除 3 笔且不影响导入前流水。
5. 在新 MSI 对应的 ProGuard Release app-image 中查看物品页：净日均成本 ¥4.32/¥5.48、单次使用成本和“示例行情·非实时”区间可见。
6. 查看洞察页：可见本月可优化空间、证据、95% 置信度、预估影响和采纳/稍后动作。
7. 21:12–21:15 使用独立空 `HENGJI_DATA_DIR` 启动同一 Release，新增“Release持久化验收 / ¥23.45”，关闭并重启；首页 ¥261.80→¥285.25，流水仍为 6 笔且记录可见。
8. 将新 MSI 行政解包并从解包结果启动，进程保持运行。
9. 2026-07-25 使用新的隔离 `HENGJI_DATA_DIR` 启动当前 Desktop 开发构建，在洞察页采纳第一条建议；关闭并重启后仍显示“已采纳”。
10. 忽略第二条品类建议后列表即时从 3 条变为 2 条；Room 中保存稳定键 `category:transport:concentration`，再次重启后仍为 2 条且该建议不再出现。
11. 将商户集中建议“稍后 7 天”；列表变为 1 条，Room 截止时间与更新时间差值精确为 7.0 天。
12. “恢复默认”会先显示确认框，并明确说明只清除采纳/稍后/忽略状态、不修改账本数据；本次 UI 验证选择取消，实际重置行为由 reducer、gateway 与 Room 自动化测试覆盖。

这轮 Release 冒烟先后捕获并修复：Room 生成数据库类被移除、SQLite JNI 方法被改名、领域枚举被优化失去枚举形态。最终规则保留 `com.hengji.**` ABI 和 `androidx.sqlite.driver.bundled.**` native 符号，证明“编译成功”不能替代发行二进制启动测试。

## 交付物

| 文件 | 大小 | SHA-256 | 说明 |
| --- | ---: | --- | --- |
| `hengji-android-debug.apk` | 25,492,005 | `6A9E6401A768AAFCF11CBB70047B43AA6ADCA99CB461528548959837CB734056` | Android Debug 证书 v2 签名；未做生产签名或设备安装/启动 |
| `hengji-windows-0.1.0.msi` | 82,158,957 | `262C4B9ABF764C7512E6A5C69A044E051548DD824F650A1A5869F7DAC894437A` | 未签名；已行政解包并启动，未做真实安装/升级/卸载 |
| `hengji-windows-portable.zip` | 81,198,888 | `C0A1B0CEC9293DC3EA5FA075D4521B00343594B5C5A2237DABF4A478F54670D6` | 自带运行时，Release 实跑通过 |

## 仍未完成，不能宣称 Beta/上线

- 当前 Room 数据库是可跨重启的明文开发存储；应用层加密、Keychain/Keystore/DPAPI 和加密迁移/恢复未完成。
- iOS/macOS 原生编译、真机、签名、公证和商店流程需要 macOS + Xcode；Windows 不能提供该证据。
- 支付/电商/二手平台尚未取得生产 scope 或合同；沙箱和示例报价不是一键实时同步。
- Windows 产物未签名；Android 只有 Debug 签名、没有生产发布签名；真实安装、升级、卸载、SmartScreen/Play 流程未验证。
- iOS 的系统文件选择器与落盘导出适配器尚未实现；当前只有共享沙箱导入和屏内导出预览。
- `org.jetbrains.compose.material3:material3:1.11.0-alpha07` 是预发布依赖；升级到稳定兼容版本及回归验证属于 Beta 依赖门禁。
- 全量 UI 自动化、屏幕阅读器、代表性低端设备性能、渗透测试、账户验证和加密同步仍是后续门禁。
