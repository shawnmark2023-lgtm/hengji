# 测试与构建报告

验证日期：2026-07-25（Asia/Shanghai）。环境：Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13。本文只记录实际执行结果，不把工程入口、调试签名或未做生产签名的产物等同于商店发布。

## 自动化结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Gradle 依赖完整性 | 主构建与 quality harness 的 strict 全配置解析通过 | 14 个 lockfile、2 份 SHA-256 verification metadata；`apps/client` 按 5 个桌面 OS/架构 profile 锁定；远端 Linux/macOS CI 尚未产生通过记录 |
| Kotlin Desktop | 95/95 通过，0 failure/error/skip | client 30、core-domain 12、core-data 28、core-insights 17、connectors 8 |
| Room 持久层 | core-data 20/20；其中 Room Desktop 6/6 | 9 表 schema v2、事务写入、显式 1→2 迁移、洞察偏好覆盖/重置/跨重启、25 MiB 上限、production fail-closed |
| Android | Debug APK 构建通过 | `:apps:client:androidApp:assembleDebug` |
| Android 签名 | v2 验证通过，1 个 signer | `CN=Android Debug`；不是生产发布签名，未做设备安装/启动 |
| iOS 交叉编译 | 元数据、arm64 与 simulator arm64 Kotlin klib 编译通过 | 覆盖系统文件选择、协调有界读取与临时导出；不是 Xcode 链接、模拟器/真机或签名证据 |
| 受保护账本 | core-data 6/6 安全边界用例通过 | 未认证算法/缺钥 fail-closed，AES-256-GCM 往返、随机 nonce、密文/错钥/AAD 篡改拒绝、版本/算法/Base64 拒绝与密钥材料清零；当前 Room 仍为开发明文存储 |
| Windows 密钥保护 | DPAPI 4/4 通过；混淆后发布 JAR 真实往返通过 | 当前用户绑定、并发首次创建收敛、跨实例重载、别名/格式 entropy 绑定、保护物交换拒绝、磁盘无原始密钥、损坏不覆盖、非法别名拒绝；尚未接入 Room |
| Android 密钥保护 | Android host 22/22，其中保护物生命周期 4/4；Android 主代码编译通过 | 非导出 Keystore AES-256-GCM 包装密钥、no-backup 保护物、版本/别名 AAD、并发首建、损坏/串换/缺钥不覆盖；host 使用可注入 JCA 保护器，不是真实设备 Keystore 证据，尚未接入 Room |
| Apple 密钥保护 | iOS arm64/simulator arm64 与 macOS JVM 源码编译通过；macOS 混淆产物符号/非宿主保护检查通过 | iOS/macOS 使用不同步、`WhenUnlockedThisDeviceOnly` Generic Password；macOS 使用 data-protection Keychain；Windows 未执行真实 `SecItem*` 往返、签名身份、锁屏或卸载验证，尚未接入 Room |
| 代码级无障碍 | Desktop/Android/iOS 公共 UI 编译通过 | 导航/表单/开关/导入/状态语义、大字体重排与 Reduce Motion；不是 VoiceOver/TalkBack/Narrator 或仅键盘实机证据 |
| 架构与发布守卫 | 30/30、206/206 通过 | 依赖方向、secret、沙箱/production 标签与禁止行为扫描；生成的 `quality/evidence` 已从源码计数排除，重复运行计数稳定 |
| 畸形导入 | 8/8 通过 | 引号未闭合、错列、重复表头、嵌套 JSON、行/文件上限、空必填、BOM/Unicode |
| 10 万流水开发基线 | 4/4 通过 | 100,000 行，90 ms，内存增量 43.78 MiB；不是代表性设备/加密数据库证据 |
| Connector gateway | 4/4 通过；`npm audit` 0 vulnerability | state 一次性/过期、沙箱非实时、production fail-closed |
| Price intelligence | 3/3 通过 | 中位数/四分位、离群过滤、新鲜度与低置信度行为 |
| Release 混淆 | 本轮 `proguardReleaseJars` 构建通过；DPAPI 从混淆后 JAR 往返通过；macOS Keychain/CoreFoundation ABI 名称保留；既有 Release 实跑通过 | 保留 Room/领域 ABI、SQLite JNI、加密 provider 服务及 Windows/macOS JNA native 符号；macOS 真实往返仍需 macOS |

主要命令：

```powershell
.\gradlew.bat resolveAllDependencies --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat -p quality\harness resolveAllDependencies --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat desktopTest --no-daemon
.\gradlew.bat :modules:core-data:testAndroidHostTest :modules:core-data:compileAndroidMain --dependency-verification strict --no-daemon
.\gradlew.bat :apps:client:androidApp:assembleDebug :apps:client:proguardReleaseJars :apps:client:compileKotlinIosArm64 :apps:client:compileKotlinIosSimulatorArm64 --dependency-verification strict --no-configuration-cache --no-daemon
.\gradlew.bat :apps:client:compileIosMainKotlinMetadata --no-daemon --rerun-tasks
python scripts/quality/run_quality.py --output-dir quality/evidence
Push-Location services\connector-gateway; npm test; npm audit --audit-level=high; Pop-Location
Push-Location services\price-intelligence; python -m pytest -q; Pop-Location
apksigner verify --verbose --print-certs artifacts\hengji-android-debug.apk
```

依赖锁由 Gradle 内建 dependency locking 生成并以 `STRICT` 模式执行；verification metadata 校验依赖和插件工件的 SHA-256。`apps/client` 的发行桌面依赖按 `windows-x64`、`linux-x64`、`linux-arm64`、`macos-x64`、`macos-arm64` 分档，避免 `desktop-jvm-*` 与 Skiko 原生运行时在不同宿主间互相污染锁状态。Compose Hot Reload 自动创建的宿主开发配置不参与版本锁，但下载工件仍受 verification metadata 约束；它们不是发行或测试运行时。校验元数据证明内容完整性，不证明发布者身份，也不替代 SBOM、许可证或漏洞审查。当前只在 Windows 完成严格解析及实际 Desktop/Android/iOS 交叉编译；仓库已配置 Linux/macOS CI，但尚无本轮远端通过记录，因此 `FND-003` 仍保持 `PARTIAL`。

本工作树路径包含中文字符，当前 Windows Gradle Test Worker 会把该路径错误编码并导致测试类加载失败；因此本轮 `desktopTest --rerun-tasks` 在同一源码状态的 ASCII 隔离副本中执行。生产源码路径上的 Desktop/Android/iOS 元数据编译及 Android APK 构建均直接通过。

无障碍本轮只取得代码审查、公共源码编译和既有单元测试证据。尚未在 macOS/iOS 上运行 VoiceOver，也未在 Android 上运行 TalkBack、在 Windows 上运行 Narrator 或完成仅键盘/高对比度矩阵，因此 `UX-008` 与 `QA-005` 均保持 `PARTIAL`。

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

- 当前 Room 数据库仍是可跨重启的明文开发存储；AES-256-GCM 受保护账本原语及四平台密钥提供器源码已完成，但 Android/Apple 真实平台验收、Room 密文映射和加密迁移/恢复未完成。
- iOS/macOS 原生编译、真机、签名、公证和商店流程需要 macOS + Xcode；Windows 不能提供该证据。
- 支付/电商/二手平台尚未取得生产 scope 或合同；沙箱和示例报价不是一键实时同步。
- Windows 产物未签名；Android 只有 Debug 签名、没有生产发布签名；真实安装、升级、卸载、SmartScreen/Play 流程未验证。
- iOS 文件适配器尚未在 Xcode、模拟器或真机运行；Windows 上的 Kotlin/Native 交叉编译不能证明 File Provider/iCloud、取消、超限、临时清理和跨重启流程正确。
- `org.jetbrains.compose.material3:material3:1.11.0-alpha07` 是预发布依赖；升级到稳定兼容版本及回归验证属于 Beta 依赖门禁。
- 全量 UI 自动化、屏幕阅读器、代表性低端设备性能、渗透测试、账户验证和加密同步仍是后续门禁。
