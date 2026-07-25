# 测试与构建报告

验证日期：2026-07-25（Asia/Shanghai）。环境：Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13。本文只记录实际执行结果，不把工程入口、调试签名或未做生产签名的产物等同于商店发布。

## 自动化结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Gradle 依赖完整性 | 主构建与 quality harness 的 strict 全配置解析通过；Desktop Release 与 Android lint/Debug/R8 Release 严格解析并构建通过 | 14 个 lockfile、2 份 SHA-256 verification metadata；本轮新增的 18 个 Android lint JAR/POM 均从 Google Maven 或 Maven Central 官方仓库独立下载并复算 SHA-256 一致；远端 Linux/macOS CI 尚未产生通过记录 |
| Kotlin Desktop | 143/143 通过，0 failure/error/skip | client 39、core-domain 17、core-data 56、core-insights 23、connectors 8 |
| Room 持久层 | core-data 56/56；其中 Room Desktop 9/9 | 9 表 schema v3、事务写入、显式 1→2→3 迁移、出售目标/洞察偏好/手工报价跨重启、报价币种不变量、25 MiB 上限、production fail-closed |
| Android | lint 0 issue；Debug APK 与 R8 Release APK 构建通过 | `lintDebug`、`assembleDebug`、`assembleRelease`；Release APK 未签名，仅作为混淆/构建证据 |
| Android 签名 | Debug APK v2 验证通过，1 个 signer | `C=US, O=Android, CN=Android Debug`；证书 SHA-256 `d740d66c573b4954e0a78e3a97034a45fd50e69310a0b289c4f9135f0ff4542b`；不是生产发布签名，未做设备安装/启动 |
| iOS 交叉编译 | 元数据、arm64 与 simulator arm64 Kotlin klib 编译通过 | 覆盖系统文件选择、协调有界读取与临时导出；不是 Xcode 链接、模拟器/真机或签名证据 |
| 受保护账本 | Desktop 28/28 通过 | 6 个密码边界、14 个 copy-on-write/CAS/迁移/初始化 journal/孤立密钥/目标价与报价跨重启用例、4 个 JVM 原子文件用例、3 个 Desktop Room 退役恢复用例、1 个 Desktop 工厂真实 DPAPI 跨实例往返；迁移标记不能变成空账本，就绪但信封缺失 fail-closed |
| Windows 密钥保护 | DPAPI 4/4 通过；Desktop 工厂与混淆后发布 JAR 真实往返通过 | 当前用户绑定、并发首次创建收敛、跨实例重载、别名/格式 entropy 绑定、保护物交换拒绝、磁盘无原始密钥、损坏不覆盖、非法别名拒绝；删除密文但保留 DPAPI 保护物后重开会 fail-closed 且不创建空账本 |
| Android 受保护存储 | Android host 47/47，0 failure/error/skip | 原子密文文件 3、Keystore 保护物 4、Room 明文迁移 5、公共仓储/密码边界 35；覆盖双快照、文件锁、硬链接退役、中断恢复、sidecar/来源冲突、目标价/手工报价持久化、报价币种不变量和迁移标记 fail-closed；真实 AndroidKeyStore、Room 升级与文件系统语义仍须设备验证 |
| Apple 密钥保护 | iOS arm64/simulator arm64 与 macOS JVM 源码编译通过；macOS 混淆产物符号/非宿主保护检查通过 | iOS/macOS 使用不同步、`WhenUnlockedThisDeviceOnly` Generic Password；iOS 入口、原子协调密文文件、双快照 Room 迁移、Complete File Protection、备份排除与安全重试 UI 已交叉编译；Windows 未执行真实 `SecItem*`、文件协调/保护、迁移、签名身份、锁屏或卸载验证 |
| 代码级无障碍 | Desktop/Android/iOS 公共 UI 编译通过 | 导航/表单/开关/导入/状态语义、大字体重排与 Reduce Motion；不是 VoiceOver/TalkBack/Narrator 或仅键盘实机证据 |
| 架构与发布守卫 | 33/33、236/236 通过 | 依赖方向、secret、沙箱/production 标签与禁止行为扫描；生成的 `quality/evidence` 已从源码计数排除，重复运行计数稳定 |
| 畸形导入 | 8/8 通过 | 引号未闭合、错列、重复表头、嵌套 JSON、行/文件上限、空必填、BOM/Unicode |
| 10 万流水开发基线 | 4/4 通过 | 100,000 行，97 ms，内存增量 43.59 MiB；不是代表性设备或加密持久仓储证据 |
| Connector gateway | 4/4 通过；`npm audit` 0 vulnerability | state 一次性/过期、沙箱非实时、production fail-closed |
| Price intelligence | 4/4 通过 | 中位数/四分位、5 点起离群过滤、新鲜度与低置信度行为；4 个合理报价不会被离散四分位误删端点 |
| Release 混淆与打包 | 当前源码的 `proguardReleaseJars` 与实际 `runRelease` 首次启动/重启通过；较早提交的自带运行时便携包已从 ZIP 解压验证 | 最新源码启动后信封大小/哈希/写入时间不变且无明文 Room；便携 ZIP 仍对应提交 `324f8434b247`，不是本轮源码的重新打包产物；macOS 真实往返仍需 macOS |

主要命令：

```powershell
.\gradlew.bat resolveAllDependencies --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat -p quality\harness resolveAllDependencies --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat desktopTest --no-daemon
.\gradlew.bat :modules:core-data:testAndroidHostTest :modules:core-data:compileAndroidMain --dependency-verification strict --no-daemon
.\gradlew.bat :apps:client:androidApp:lintDebug :apps:client:androidApp:assembleDebug :apps:client:androidApp:assembleRelease --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat :apps:client:proguardReleaseJars :apps:client:compileKotlinIosArm64 :apps:client:compileKotlinIosSimulatorArm64 --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat :apps:client:packageReleaseUberJarForCurrentOS --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat :apps:client:compileIosMainKotlinMetadata --no-daemon --rerun-tasks
python scripts/quality/run_quality.py --output-dir quality/evidence
Push-Location services\connector-gateway; npm test; npm audit --audit-level=high; Pop-Location
Push-Location services\price-intelligence; python -m pytest -q; Pop-Location
```

依赖锁由 Gradle 内建 dependency locking 生成并以 `STRICT` 模式执行；verification metadata 校验依赖和插件工件的 SHA-256。`apps/client` 的发行桌面依赖按 `windows-x64`、`linux-x64`、`linux-arm64`、`macos-x64`、`macos-arm64` 分档，避免 `desktop-jvm-*` 与 Skiko 原生运行时在不同宿主间互相污染锁状态。Compose Hot Reload 自动创建的宿主开发配置不参与版本锁，但下载工件仍受 verification metadata 约束；它们不是发行或测试运行时。校验元数据证明内容完整性，不证明发布者身份，也不替代 SBOM、许可证或漏洞审查。当前只在 Windows 完成严格解析及实际 Desktop/Android/iOS 交叉编译；仓库已配置 Linux/macOS CI，但尚无本轮远端通过记录，因此 `FND-003` 仍保持 `PARTIAL`。

本工作树路径包含中文字符，Android Gradle Plugin 9.1.1 会在 Windows 配置阶段直接拒绝该项目路径；因此本轮所有 Gradle 构建、测试与 quality harness 均在同一源码状态的 ASCII 隔离副本中执行。ASCII 副本完成了 finance-app validator（232 个源码文件，0 error/0 warning）、架构/发布守卫、畸形导入、10 万流水与 `git diff --check`；原工作树也直接通过不依赖 Gradle 的 validator、架构/发布守卫和 diff 检查。这条宿主路径限制不应通过关闭 AGP 检查来掩盖。

当前 Windows Release 由提交 `324f8434b247` 的严格校验构建生成。ProGuard Release uber JAR 为 30,358,813 bytes，SHA-256 `C64A695BA435B65576057079C2993886E64CF58CB6D05A18853C7DDC20C0ABBF`；JDK 21 `jpackage` app-image 为 184,072,805 bytes，其中 `Hengji.exe` SHA-256 `0224147E6EC74BA7A1A2D9F593DEE833D04849C9554737999803A84351F21585`，Authenticode 状态为 `NotSigned`。

最终便携 ZIP 解压后，以独立空 `HENGJI_DATA_DIR` 启动实际 `Hengji.exe`。首次启动生成 24,147 bytes 的 `hengji.ledger.hjenc` 与 DPAPI 保护物，没有生成 `hengji.db`；关闭后以同一目录再次启动，信封 SHA-256 `05B819755AC6BB21B6601D5A72A734E22C06A6A83E5B970447C72A6A5B0016E7`、大小与最后写入时间均未变化。信封文本未命中演示资产 sentinel `asset-headphones`。这证明当前混淆、打包后的 Windows 入口能安全重开已有账本，但不替代代码签名、真实安装/升级/卸载、SmartScreen 或 macOS Keychain 验证。

本轮最新源码另以独立 `HENGJI_DATA_DIR` 连续两次执行实际 `:apps:client:runRelease`。两次均启动 Desktop 主进程；24,147 bytes 的信封 SHA-256 均为 `6FF0D6537BBF75B38ECF1E53E66DA18D389234BE4DFD4101B920E1E156B96B36`，写入时间不变，没有 `hengji.db`，DPAPI vault 同时存在主数据密钥、全新初始化与就绪标记。该证据验证本轮初始化 journal 的正常首次启动/重开路径，但不是新的便携 ZIP，也不覆盖崩溃注入或系统级回滚攻击。

无障碍本轮只取得代码审查、公共源码编译和既有单元测试证据。尚未在 macOS/iOS 上运行 VoiceOver，也未在 Android 上运行 TalkBack、在 Windows 上运行 Narrator 或完成仅键盘/高对比度矩阵，因此 `UX-008` 与 `QA-005` 均保持 `PARTIAL`。

当前主机没有可用的 WiX 工具链；Compose `downloadWix` 尝试下载 WiX 3.11 时连接被重置，因此本轮没有生成 MSI，也没有执行行政解包或真实安装/升级/卸载。旧文档记录的 MSI 文件当前不存在，其大小、哈希与启动结论不作为本次提交的交付证据。

## 真实 UI 验收

1. 使用隔离 `HENGJI_DATA_DIR` 启动桌面开发运行，新增“UI持久化测试 / ¥12.34”。
2. 关闭并重启同一路径：首页本月支出从 ¥261.80 变为 ¥274.14，流水列表仍显示该记录。
3. 打开导入中心，选择明确标注“沙箱·非生产”的 CSV；自动映射字段、预览 3 笔、确认原子写入。
4. 在完成页整批撤销，界面显示已移除 3 笔且不影响导入前流水。
5. 查看物品页：净日均成本 ¥4.32/¥5.48、单次使用成本和“示例行情·非实时”区间可见。
6. 查看洞察页：可见本月可优化空间、证据、95% 置信度、预估影响和采纳/稍后动作。
7. 使用新的隔离 `HENGJI_DATA_DIR` 启动当前 Desktop 开发构建，在洞察页采纳第一条建议；关闭并重启后仍显示“已采纳”。
8. 忽略第二条品类建议后列表即时从 3 条变为 2 条；Room 中保存稳定键 `category:transport:concentration`，再次重启后仍为 2 条且该建议不再出现。
9. 将商户集中建议“稍后 7 天”；列表变为 1 条，Room 截止时间与更新时间差值精确为 7.0 天。
10. “恢复默认”会先显示确认框，并明确说明只清除采纳/稍后/忽略状态、不修改账本数据；本次 UI 验证选择取消，实际重置行为由 reducer、gateway 与 Room 自动化测试覆盖。
11. 从当前 `hengji-windows-portable.zip` 解压启动未签名的 ProGuard Release `Hengji.exe`；首次启动与重启均保持两个实际进程存活，受保护账本完整性检查见上文。
12. 使用全新隔离账本打开“降噪耳机”资产详情，添加规格“UI验证 · 良好”、标价 ¥1,850.00、预计运费 ¥20.00 的手工报价；界面明确说明只写入本机、不访问二手平台且不标记为实时行情。
13. 保存后资产报价从 3 条变为 4 条，当前残值从 ¥1,820.00 更新为 ¥1,870.00，总资产估值从 ¥3,870.00 更新为 ¥3,920.00，净日均成本与单次使用成本同步重算；来源显示“混合来源·含示例/手工·非实时·7 月 25 日”。
14. 关闭并以同一隔离目录重启，概览和物品页仍显示 ¥3,920.00 总估值、¥1,870.00 耳机残值及手工来源日期，证明真实 Desktop 入口的报价持久化与重投影成功。
15. 使用另一全新隔离账本为“降噪耳机”依次添加 ¥1,800.00、¥1,850.00、¥1,900.00 三条同币种本机手工报价；非示例中位数显示 ¥1,850.00，总资产残值从 ¥3,870.00 更新为 ¥3,900.00，网络访问计数保持 0。
16. 设置出售目标 ¥1,860.00 后详情显示“等待达到”；修改为 ¥1,850.00 后即时显示“已达到”。详情同时明确“仅在打开衡记时更新；不会发送系统通知，也不会在后台联网”。
17. 洞察页生成“出售目标价已达到”，证据显示可信报价中位数 ¥1,850.00、有效报价数 3、最新报价距今 0 天、来源为本机手工报价；示例报价没有参与触发。
18. 关闭并以同一隔离目录重启，概览仍为 ¥3,900.00，资产详情仍显示 6 条历史报价、可呈现中位数 ¥1,850.00、目标 ¥1,850.00 与“已达到”，证明目标价、报价历史和投影跨重启保留。

这轮 Release 冒烟先后捕获并修复：Room 生成数据库类被移除、SQLite JNI 方法被改名、领域枚举被优化失去枚举形态。最终规则保留 `com.hengji.**` ABI 和 `androidx.sqlite.driver.bundled.**` native 符号，证明“编译成功”不能替代发行二进制启动测试。

## 交付物

| 文件 | 大小 | SHA-256 | 说明 |
| --- | ---: | --- | --- |
| `hengji-windows-portable.zip` | 83,602,469 | `6CF9AA770C7116E488993C12762E52E763673233EBBAC95FC997CF84AE90BBFA` | 自带运行时；从 ZIP 解压的受保护 Release 入口首次启动/重启通过；未签名 |
| `hengji-android-debug.apk` | 25,858,975 | `5C7A74014ABC515DFB9C632AEE0A48EF6A71EAA1E6BABD9A3884F6E710B6D935` | v2 Android Debug 签名；包含应用内出售目标价；lint/构建通过；未做设备安装或启动 |

## 仍未完成，不能宣称 Beta/上线

- Desktop、Android 与 iOS 入口均已切换到 AES-256-GCM 受保护仓储；Desktop 已实跑，Android 已通过 47 个 host 用例、lint 与 APK 构建，iOS 只完成 Kotlin/Native 交叉编译。Android/iOS 仍没有真机平台密钥、旧库升级、锁屏、卸载/恢复或代表性数据量性能证据。
- 出售目标价目前只在打开应用时从本地报价重算；尚无系统通知权限、后台刷新或授权实时行情源，因此不能描述为后台价格提醒。
- 受平台保护的初始化标记已覆盖“数据密钥落盘后、初始信封提交前崩溃”的新安装恢复，并阻止迁移中断静默变成空账本；但 v1 标记不是数据密钥与 bootstrap record 的单一原子平台事务，也没有系统级抗回滚/重放与完整轮换恢复流程。旧版本遗留的“有密钥、无信封、无标记”仍按 fail-closed 处理。
- iOS/macOS 原生编译、真机、签名、公证和商店流程需要 macOS + Xcode；Windows 不能提供该证据。
- 支付/电商/二手平台尚未取得生产 scope 或合同；沙箱和示例报价不是一键实时同步。
- 当前 Windows 便携包未签名且 MSI 未生成；Android APK 仅为 Debug v2 签名，R8 Release APK 未签名。没有生产发布签名、真实安装/升级/卸载或 SmartScreen/Play 流程证据。
- iOS 文件适配器尚未在 Xcode、模拟器或真机运行；Windows 上的 Kotlin/Native 交叉编译不能证明 File Provider/iCloud、取消、超限、临时清理和跨重启流程正确。
- `org.jetbrains.compose.material3:material3:1.11.0-alpha07` 是预发布依赖；升级到稳定兼容版本及回归验证属于 Beta 依赖门禁。
- 全量 UI 自动化、屏幕阅读器、代表性低端设备性能、渗透测试、账户验证和加密同步仍是后续门禁。
