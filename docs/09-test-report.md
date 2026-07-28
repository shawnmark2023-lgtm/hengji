# 测试与构建报告

## 2026-07-28 Windows/Android 全量工程与安全验收

本轮在当前未提交源码状态上重新执行所有本机可运行门禁；iOS/macOS 不在范围内。结论为 **Windows/Android 本地代码与工程验收通过**，不等同于 Beta、生产、签名或商店发布。完整命令、工件哈希、安全发现与未验证边界见 `docs/13-engineering-security-acceptance.md`。

| 门禁 | 本轮结果 | 关键证据 |
| --- | --- | --- |
| 静态工程 | 通过 | 格式 183、架构 38、发布守卫 293、依赖复现 18、供应链清单 352；`git diff --check` 通过 |
| 覆盖率 | 通过 | core-domain 94.76%/61.90%，core-insights 91.91%/59.05%，connectors 91.83%/52.94% |
| 输入/性能 | 通过 | 畸形导入 8/8；100,000 流水 4/4，106 ms、43.30 MiB；后者仅为开发机内存基线 |
| Desktop | 通过 | Kotlin 193/193；ProGuard Release 与 Release uber JAR 通过 |
| Windows MSI | 通过（发布边界保留） | 行政解包、连续启动、DPAPI 密文、0.0.9→0.1.0 安装升级、两次已安装 EXE 启动、卸载与数据保留通过；MSI/EXE 未签名，SmartScreen 未验收 |
| Android host/build | 通过 | host 63/63；lint 0 error/13 warning；Debug、R8 Release、AndroidTest APK 通过 |
| Android API 36 | 通过 | 官方 Google APIs x86_64 AVD instrumentation 3/3；真实 AndroidKeyStore 创建/重开、平台权限与 Compose Accessibility Test Framework |
| Android 权限 | 通过 | Debug/Release APK 均不含 `INTERNET`、`READ_SMS`、`RECEIVE_SMS` |
| 辅助服务 | 通过 | connector 4/4、TypeScript 与价格服务严格 Pyright 0 error/0 warning、price pytest 3/3、隔离 wheel 构建、npm audit 0 |
| 供应链 | 通过 | CycloneDX 1.6：280 组件；OSV Scanner 2.3.8：276 包、0 已知漏洞、0 许可证违规；64 个 Action 引用均为完整 SHA |
| 财务应用校验器 | 通过 | 0 error、0 warning |

本轮重建工件：

- Android Debug APK：71,238,563 bytes，SHA-256 `63DF0AD1B53FFAF053CA872A537C469465C4E3A8962DF9338605C465335D5294`。
- Android 未签名 Release APK：50,416,072 bytes，SHA-256 `B218B9EE61DE2EEEA63C006AC39DBE09804262CD99C53033960B2EAD10942C87`。
- Android instrumentation APK：4,430,821 bytes，SHA-256 `311FF1325379DBF90866EF028B624078B721785DA90AE644AAF3C4D427471718`。
- Windows ProGuard Release JAR：30,592,366 bytes，SHA-256 `19A728714E4A2DF81081660ED6ACCB5F80879C9F41BC43B2687C07C5FAB0B13B`。
- Windows 未签名 MSI 0.1.0：108,695,964 bytes，SHA-256 `837D1DEB11E1CC9A4F32D98C5F37D3A71881D1CCF23C3BC70F518ECE5B1386F3`。
- 价格服务 wheel：8,195 bytes，SHA-256 `8DADB21902D4550B8556623F55E3D8981D1FABE1D7284A33427C2A3021A69095`。
- CycloneDX 1.6 SBOM：193,049 bytes，SHA-256 `0600986717420E3B8FD41F717831F018E07A3F88F8C44574B21CC68991856358`。

2026-07-27 及更早章节保留为历史证据；其中旧工件哈希、组件数及 Application Control 结果不再代表本轮当前状态。

## 2026-07-27 Windows MSI 发布门禁增量

在提交 `d319eb6ba7b60909ad2f74e809888fc491533344` 的干净跟踪源码上，Compose Desktop `packageMsi` 已生成每用户安装的 `Hengji-0.0.9.msi` 与 `Hengji-0.1.0.msi`。本机损坏且截断的 Gradle WiX 缓存已隔离，随后使用 WiX 官方发布的便携二进制恢复构建工具链；该下载只进入用户级 Gradle 缓存，没有写入仓库或绕过依赖/源码门禁。程序安装目录固定为 `%LOCALAPPDATA%\Programs\Hengji`，与既有默认加密数据目录 `%LOCALAPPDATA%\Hengji` 分离。

| 门禁 | 结果 | 证据与边界 |
| --- | --- | --- |
| MSI 构建 | 通过 | 0.1.0 为 108,695,964 bytes；SHA-256 `D0A334B3FD0B93F60EC8D0514BB2407403C4C86B4A046489670BB899A909D281`；Product `Hengji`，Manufacturer `HENGJI`；Authenticode `NotSigned` |
| Windows Installer 行政解包 | 通过 | `msiexec /a ... /qn` 退出码 0；解包得到 `Programs/Hengji/Hengji.exe` |
| 解包后二进制首次启动 | 通过 | 实际 `Hengji.exe` 保持 2 个进程存活；创建 24,294 bytes 加密信封与 3 个 DPAPI 保护物；没有 `hengji.db`，密文未命中 `asset-headphones` |
| 同账本重开 | 通过 | 第二次启动同样保持 2 个实际进程存活；信封 SHA-256 `7241A8914AFF8A549F32FBDE9112F62B1870B7A1D08041F5BBF4F816A3BC2A17`、大小与最后写入时间均未变化 |
| 每用户真实安装 | 通过 | 0.0.9 安装退出码 0；落在 `%LOCALAPPDATA%\Programs\Hengji`，创建 1 个当前用户 Start Menu 快捷方式，不要求管理员权限 |
| 0.0.9 → 0.1.0 升级 | 通过 | 两包共享 UpgradeCode、ProductCode 不同；升级退出码 0，旧 ProductCode 被移除，64-byte 隔离数据探针哈希与写入时间不变 |
| 卸载与数据策略 | 通过 | 卸载退出码 0；产品注册、程序目录和 Start Menu 快捷方式均移除；安装器保留隔离用户数据探针，测试框架在取证后删除该一次性探针 |
| 已安装 EXE 启动 | 本机阻断 / 严格 CI 已配置 | 本机 Application Control 阻止从 `%LOCALAPPDATA%\Programs\Hengji` 运行未签名 EXE；同一 MSI 行政解包后的 EXE 已通过两次启动。CI 生命周期脚本默认不允许跳过已安装 EXE 启动 |
| 可重复门禁 | 通过 | `verify-windows-msi.ps1` 验证行政解包/加密账本；`verify-windows-msi-lifecycle.ps1` 验证安装、升级、严格启动、卸载和数据保留，并拒绝覆盖任何既有 Hengji 安装 |

这项证据关闭了每用户 MSI 生成、行政解包、0.0.9 安装、0.1.0 升级、卸载清理和数据保留的本地门禁。两个版本包含相同应用代码，因此只能证明安装器版本迁移，不能替代历史应用 schema 迁移测试。MSI 与 EXE 均未签名；本机已安装 EXE 启动、SmartScreen 信誉和生产签名仍未通过。

## 2026-07-27 Windows/Android P1 收口

本轮继续只收口 Windows + Android；iOS/macOS 延期。P1 代码侧已完成密钥轮换、Android 快捷记账入口和本地 OCR/PDF 导入、聚合解释同意门控、授权报价缓存，以及系统通知的本地周期评估边界。依赖生产合同、Google Play 审批、生产签名或代表性实体设备的事项标记为 `READY_EXTERNAL`，不冒充已经获得外部验收。

| 门禁 | 结果 | 证据与边界 |
| --- | --- | --- |
| Desktop 单元测试 | 193/193，0 failure/error/skip | client 57、core-domain 17、core-data 79、core-insights 25、connectors 15；新增覆盖主题/Reduce Motion、通知同意撤回入口、密钥轮换、OCR 文本解析和授权报价缓存溢出边界 |
| Android host | 63/63，0 failure/error/skip | 包含共同受保护账本测试及新增轮换路径 |
| Android API 36 | 3/3 | 真实 AndroidKeyStore 账本创建/重开；清单断言小组件、快捷动作、文本/图片/PDF 分享和权限；真实 `MainActivity` Compose 层级通过 Accessibility Test Framework |
| Android 构建 | 通过 | `lintDebug` 0 error/13 warning；Debug APK、未签名 R8 Release APK 构建通过。APK 实际权限包含通知与 WorkManager 所需能力，不含 `INTERNET` 或短信读取/接收权限；这会阻断 ML Kit 诊断/使用指标外发，未来授权行情联网必须单独重新审查 |
| Windows 构建 | 通过 | ProGuard Release JAR 与未签名 MSI 构建通过；MSI 行政解包后的真实 EXE 首次创建/同账本重开 smoke 保持密文哈希、字节数与写入时间一致，磁盘不含明文数据库 |
| 完整质量门禁 | 通过 | 格式 180/180；架构 38/38；发布守卫 283/283；依赖/可复现策略 18/18；畸形导入 8/8 |
| 供应链 | 通过 | CycloneDX 1.6 SBOM 共 278 个组件；Windows 137、Android release 211 个运行坐标，去重后 270 个 Maven 组件；连同 Node/Python 构建工具共 274 个包经 OSV Scanner 2.3.8 审计为 0 已知漏洞、0 许可证违规。11 个 ML Kit 非标准条款组件采用精确版本、官方条款 URL 与 2027-07-27 复审日期；64 处 GitHub Action 引用固定到完整提交 SHA |
| 覆盖率 | 通过 | core-domain 行/分支 94.76%/61.90%；core-insights 91.91%/59.05%；connectors 91.83%/52.94%，均达到阻断阈值 |
| 10 万流水基线 | 4/4 | 106 ms、内存增量 43.46 MiB；这是开发机内存基线，不代表低端实体设备的加密持久层或完整 UI 性能 |
| 两次隔离构建 | Windows/Android 均通过 | Windows 73 个 Release JAR 路径/权限/内容规范化差异为 0；Android 两次 Debug APK 原始 SHA-256 与规范化内容均一致 |

当前重建产物：

- Android Debug APK：71,238,591 bytes，SHA-256 `0EC8E43B185896E013860409791520098609B29F1D68AF56F911B858356E4015`。
- Android 未签名 Release APK：50,416,092 bytes，SHA-256 `52C0A7B8D056B2F3D400716DDD265FC50C8EB347393A837CBCFD245FD7245074`。
- Windows ProGuard Release JAR：30,592,366 bytes，SHA-256 `19A728714E4A2DF81081660ED6ACCB5F80879C9F41BC43B2687C07C5FAB0B13B`。
- Windows 未签名 MSI 0.1.0：108,695,964 bytes，SHA-256 `D0A334B3FD0B93F60EC8D0514BB2407403C4C86B4A046489670BB899A909D281`。

P1 尚需外部条件而不能在本仓库中宣称完成的事项：

- 直接读取短信方案需要 Google Play 权限审批；当前实现改为用户主动系统分享，未申请敏感短信权限。
- 生产报价与后台提醒需要官方 API 或授权聚合合同；当前缓存拒绝演示/手工数据冒充实时源，未配置提供方时后台网络调用为 0。
- Android 锁屏、卸载、系统备份/回滚、密钥丢失和代表性低端实体设备性能仍需设备矩阵；当前只有 API 36 AVD 与 Windows 证据。
- Android Release APK 尚未生产签名；iOS/macOS 依产品指令延期。
- Windows MSI 与 EXE 尚未生产签名；本地每用户安装、同代码版本升级、卸载和数据保留已通过，但已安装 EXE 启动被本机 Application Control 阻断，仍需独立 runner、SmartScreen 和签名候选包矩阵。

## 2026-07-27 Windows/Android P0 增量

本轮按产品决定只收口 Windows + Android；iOS/macOS 延期，未运行其编译、签名或设备任务，也不把它们作为本轮阻断。环境为 Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13、Android API 36 AVD。

| 门禁 | 结果 | 证据与边界 |
| --- | --- | --- |
| Desktop Kotlin | 178/178，0 failure/error/skip | client 53、core-domain 17、core-data 77、core-insights 23、connectors 8；client 包含 5 个共享关键 UI 用例和 1 个 Desktop Tab/Enter 用例 |
| Android host | 61/61，0 failure/error/skip | Android 文件语义改为 `lstat` 判型与受锁保护的原子 rename，覆盖密文账本、Keystore 保护物和 Room 明文退役 |
| Android API 36 | 共享 UI 52/52；instrumentation 3/3 | UI 套件中 5 个关键流程与 Desktop 复用；独立 instrumentation 覆盖平台入口、真实 AndroidKeyStore/no-backup 创建重开和 Compose 自动无障碍分析 |
| Android 应用冒烟 | 首次冷启动和同一账本重启通过 | 修复 `/data/user/0` 合法路径被 canonical equality 误拒绝，以及 Android/SELinux 拒绝硬链接发布导致的启动失败；磁盘只保留 no-backup 加密账本和 Keystore 包装保护物 |
| 关键 UI | Windows/Android 通过 | CSV 沙箱来源→自动映射→预览去重→确认→整批撤销；JSON/CSV 导出、清除取消/确认、空账本、JSON 恢复；删除取消/确认、8 秒撤销/超时；360dp/200%、深色、Reduce Motion、语义；Desktop Tab/Enter |
| 构建 | 通过 | Android lint、Debug APK、未签名 R8 Release APK、Desktop ProGuard Release JAR；MSI/Compose 分发任务仍因本机 WiX ZIP 不可用而失败，不计为通过 |
| 格式与静态门禁 | 通过 | 格式 163/163；架构 35/35；发布守卫 256/256；可复现/依赖策略 18/18 |
| 依赖完整性 | 通过 | 11 个必需 lockfile、1,455 个锁定坐标、3 份 verification metadata、3,516 个 SHA-256 校验工件 |
| 覆盖率 | 通过 | core-domain 行/分支 94.76%/61.90%；core-insights 91.95%/59.39%；connectors 90.95%/50.36%，均达到各自阻断阈值 |
| 两次隔离构建 | Windows/Android 均通过 | Windows 73 个 Release JAR 的路径、权限和内容规范化比较差异为 0；Android 两次 Debug APK 的原始及规范化 SHA-256 均一致 |
| 输入与性能 | 通过 | 8/8 畸形导入；100,000 流水 4/4，112 ms、内存增量 43.49 MiB；后者只是开发/CI 内存基线 |
| 辅助服务 | 通过 | connector gateway 4/4 且 `npm audit` 0 vulnerability；price intelligence 3/3 |
| 最终应用校验器 | 通过 | 扫描 916 个文件，0 error、0 warning |

本轮仍不能关闭的 P0 只有外部证据项：

- `FND-003`：仓库没有 remote；CI 门禁已配置，但没有独立远端 runner 的通过记录。
- `UX-008` / `QA-005`：自动化已覆盖语义、键盘、重排、主题、Reduce Motion，以及 Android Accessibility Test Framework 的标签、触摸目标、对比度和遍历检查；TalkBack、Windows Narrator 与真实硬件键盘仍需专项设备/辅助技术验收。

这些阻断不会被描述为“已通过”。Android AOSP 镜像不含 TalkBack；未用自动化结果替代真实屏幕阅读器。iOS/macOS 是明确延期范围，不代表已经通过。

当前交付物由干净提交 `15b3b45ac018002e2a9c0007b8a8ea321f64544b` 重建，精确大小、SHA-256、签名和运行边界见 `artifacts/manifest.json`。Windows `jpackage` app-image 生成及解压校验通过，但本机 Application Control 会阻止执行新生成的未签名 `Hengji.exe`；同一份 ProGuard Release JAR 已通过首次受保护账本创建和同账本重开。Android Debug APK 与两次隔离构建哈希一致，并保持 API 36 启动/重开与设备测试证据。

---

以下为 2026-07-25 历史基线，保留用于追溯；其中旧提交、旧交付物哈希和旧测试数量不再代表 2026-07-27 的当前状态。

验证日期：2026-07-25（Asia/Shanghai）。环境：Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13。本文只记录实际执行结果，不把工程入口、调试签名或未做生产签名的产物等同于商店发布。

## 自动化结果

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Gradle 依赖完整性 | 主构建与 quality harness 的 strict 全配置解析通过；Desktop Release 与 Android lint/Debug/R8 Release 严格解析并构建通过 | 14 个 lockfile、2 份 SHA-256 verification metadata；本轮新增的 18 个 Android lint JAR/POM 均从 Google Maven 或 Maven Central 官方仓库独立下载并复算 SHA-256 一致；远端 Linux/macOS CI 尚未产生通过记录 |
| Kotlin Desktop | 172/172 通过，0 failure/error/skip | client 47、core-domain 17、core-data 77、core-insights 23、connectors 8 |
| Room 持久层 | core-data 77/77；其中 Room Desktop 14/14 | 9 表 schema v3、事务写入、显式 1→2→3 迁移、出售目标/洞察偏好/手工报价跨重启、删除/精确 token 恢复、墓碑指纹保留、退款引用保护、单调 revision、清除后高 revision 空账本、报价币种不变量、25 MiB 上限、production fail-closed |
| Android | lint 0 issue；Debug APK 与 R8 Release APK 构建通过 | `lintDebug`、`assembleDebug`、`assembleRelease`；Release APK 未签名，仅作为混淆/构建证据 |
| Android 签名 | Debug APK v2 验证通过，1 个 signer | `C=US, O=Android, CN=Android Debug`；证书 SHA-256 `d740d66c573b4954e0a78e3a97034a45fd50e69310a0b289c4f9135f0ff4542b`；不是生产发布签名，未做设备安装/启动 |
| iOS 交叉编译 | 元数据、arm64 与 simulator arm64 Kotlin klib 编译通过 | 覆盖系统文件选择、协调有界读取与临时导出；不是 Xcode 链接、模拟器/真机或签名证据 |
| 受保护账本 | Desktop 36/36 通过 | 6 个密码边界、20 个 copy-on-write/CAS/迁移/初始化 journal/孤立密钥/删除恢复/目标价/报价/清除跨重启用例、4 个 JVM 原子文件用例、3 个 Desktop Room 退役恢复用例、3 个 Desktop 工厂真实 DPAPI 跨实例往返、删除恢复与清除重开用例；删除和成功恢复均跨真实重开持久化，清除会移除全部快照字段、revision 精确 +1，迁移标记不能变成空账本，就绪但信封缺失 fail-closed |
| Windows 密钥保护 | DPAPI 4/4 通过；Desktop 工厂与混淆后发布 JAR 真实往返通过 | 当前用户绑定、并发首次创建收敛、跨实例重载、别名/格式 entropy 绑定、保护物交换拒绝、磁盘无原始密钥、损坏不覆盖、非法别名拒绝；删除密文但保留 DPAPI 保护物后重开会 fail-closed 且不创建空账本 |
| Android 受保护存储 | Android host 61/61，0 failure/error/skip | 原子密文文件 3、Keystore 保护物 4、Room 明文迁移 5、公共仓储/密码边界 49；覆盖双快照、文件锁、硬链接退役、中断恢复、sidecar/来源冲突、墓碑/恢复/refund 约束、目标价/手工报价持久化、报价币种不变量和迁移标记 fail-closed；真实 AndroidKeyStore、Room 升级与文件系统语义仍须设备验证 |
| Apple 密钥保护 | iOS arm64/simulator arm64 与 macOS JVM 源码编译通过；macOS 混淆产物符号/非宿主保护检查通过 | iOS/macOS 使用不同步、`WhenUnlockedThisDeviceOnly` Generic Password；iOS 入口、原子协调密文文件、双快照 Room 迁移、Complete File Protection、备份排除与安全重试 UI 已交叉编译；Windows 未执行真实 `SecItem*`、文件协调/保护、迁移、签名身份、锁屏或卸载验证 |
| 代码级无障碍 | Desktop/Android/iOS 公共 UI 编译通过 | 导航/表单/开关/导入/状态语义、大字体重排与 Reduce Motion；不是 VoiceOver/TalkBack/Narrator 或仅键盘实机证据 |
| 架构与发布守卫 | 35/35、237/237 通过 | 在不含构建产物与分发 artifact 的 ASCII 源码镜像运行；依赖方向、secret、沙箱/production 标签与禁止行为扫描通过 |
| 畸形导入 | 8/8 通过 | 引号未闭合、错列、重复表头、嵌套 JSON、行/文件上限、空必填、BOM/Unicode |
| 10 万流水开发基线 | 4/4 通过 | 100,000 行，101 ms，内存增量 43.58 MiB；不是代表性设备或加密持久仓储证据 |
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

依赖锁由 Gradle 内建 dependency locking 生成并以 `STRICT` 模式执行；verification metadata 校验依赖和插件工件的 SHA-256。`apps/client` 的发行桌面依赖按 `windows-x64`、`linux-x64`、`linux-arm64`、`macos-x64`、`macos-arm64` 分档，避免 `desktop-jvm-*` 与 Skiko 原生运行时在不同宿主间互相污染锁状态。Compose Hot Reload 自动创建的宿主开发配置不参与版本锁，但下载工件仍受 verification metadata 约束；它们不是发行或测试运行时。校验元数据证明内容完整性，不证明发布者身份；Windows/Android 发行范围另由 CycloneDX SBOM、OSV 许可证/漏洞审计和 GitHub Action SHA 门禁覆盖。当前只在 Windows 完成严格解析及实际 Desktop/Android/iOS 交叉编译；仓库已配置 Linux/macOS CI，但尚无本轮远端通过记录，因此 `FND-003` 仍保持 `PARTIAL`。

本工作树路径包含中文字符，Android Gradle Plugin 9.1.1 会在 Windows 配置阶段直接拒绝该项目路径；因此本轮 Gradle 构建通过映射到同一工作树的 ASCII 盘符执行，quality harness 在同一源码状态的 ASCII 隔离副本执行。原工作树 finance-app validator 检查 253 个源码/文档文件，0 error/0 warning；隔离副本通过架构/发布守卫、畸形导入、10 万流水与 `git diff --check`。这条宿主路径限制不应通过关闭 AGP 检查来掩盖。

当前 Windows Release 由提交 `324f8434b247` 的严格校验构建生成。ProGuard Release uber JAR 为 30,358,813 bytes，SHA-256 `C64A695BA435B65576057079C2993886E64CF58CB6D05A18853C7DDC20C0ABBF`；JDK 21 `jpackage` app-image 为 184,072,805 bytes，其中 `Hengji.exe` SHA-256 `0224147E6EC74BA7A1A2D9F593DEE833D04849C9554737999803A84351F21585`，Authenticode 状态为 `NotSigned`。

最终便携 ZIP 解压后，以独立空 `HENGJI_DATA_DIR` 启动实际 `Hengji.exe`。首次启动生成 24,147 bytes 的 `hengji.ledger.hjenc` 与 DPAPI 保护物，没有生成 `hengji.db`；关闭后以同一目录再次启动，信封 SHA-256 `05B819755AC6BB21B6601D5A72A734E22C06A6A83E5B970447C72A6A5B0016E7`、大小与最后写入时间均未变化。信封文本未命中演示资产 sentinel `asset-headphones`。这份便携 ZIP 证据本身不覆盖安装生命周期；当前 MSI 安装/升级/卸载结果见本文顶部。代码签名、已安装位置启动、SmartScreen 和 macOS Keychain 仍是独立门禁。

本轮最新源码另以独立 `HENGJI_DATA_DIR` 连续两次执行实际 `:apps:client:runRelease`。两次均启动 Desktop 主进程；24,147 bytes 的信封 SHA-256 均为 `6FF0D6537BBF75B38ECF1E53E66DA18D389234BE4DFD4101B920E1E156B96B36`，写入时间不变，没有 `hengji.db`，DPAPI vault 同时存在主数据密钥、全新初始化与就绪标记。该证据验证本轮初始化 journal 的正常首次启动/重开路径，但不是新的便携 ZIP，也不覆盖崩溃注入或系统级回滚攻击。

无障碍本轮取得代码审查、公共源码编译、语义/布局自动化，以及 API 36 上真实 `MainActivity` 的 Compose Accessibility Test Framework 证据。该自动分析不会操作或聆听屏幕阅读器；尚未在 Android 上运行 TalkBack、在 Windows 上运行 Narrator 或完成真实硬件键盘矩阵，因此 `UX-008` 与 `QA-005` 均保持 `PARTIAL`。Apple 无障碍依产品指令延期。

2026-07-27 已把 API 36 `connectedDebugAndroidTest` 配置为 GitHub-hosted 模拟器独立作业；本地 API 36 AVD 已通过 3/3，CI 成功运行后会上传 JUnit、HTML 报告和带限制说明的证据 JSON。Apple 编译与 DMG 作业迁到仅手动触发的延期工作流，不参与当前 Windows/Android PR 门禁。仓库仍未配置 remote，因此 CI 状态只能写为 `CONFIGURED_CI`，不能写为独立 runner 已通过。

代表性 Android 实体机、锁屏/重启/卸载/系统恢复、TalkBack、Windows Narrator、硬件键盘和高对比度的固定矩阵与证据模板见 `docs/11-device-accessibility-validation.md`；当前各实体设备行仍为 `NOT_RUN`。

当前主机已使用 WiX 官方便携二进制恢复 Compose 所需的 WiX 3.11 工具链；`packageMsi`、Windows Installer 行政解包、解包后实际 EXE 首次启动/重开、每用户安装、同代码版本升级和卸载均已通过，精确证据见本文顶部增量。已安装位置的未签名 EXE 被本机 Application Control 阻断；SmartScreen 与生产签名仍未验证。

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

本轮新增的流水删除尚未执行真实 UI 点击验收：自动化已覆盖二次删除所需的严格单调 token、8 秒边界、过期/错误 token 拒绝、退款引用约束、墓碑导出/去重、删除资产购买流水不级联，以及真实 Windows DPAPI 账本“删除→重开→恢复→再重开”。因此不能把确认框、Snackbar 焦点或屏幕阅读器播报宣称为实机通过。

这轮 Release 冒烟先后捕获并修复：Room 生成数据库类被移除、SQLite JNI 方法被改名、领域枚举被优化失去枚举形态。最终规则保留 `com.hengji.**` ABI 和 `androidx.sqlite.driver.bundled.**` native 符号，证明“编译成功”不能替代发行二进制启动测试。

## 交付物

| 文件 | 大小 | SHA-256 | 说明 |
| --- | ---: | --- | --- |
| `hengji-windows-portable.zip` | 83,602,469 | `6CF9AA770C7116E488993C12762E52E763673233EBBAC95FC997CF84AE90BBFA` | 自带运行时；从 ZIP 解压的受保护 Release 入口首次启动/重启通过；未签名 |
| `hengji-android-debug.apk` | 25,858,975 | `5C7A74014ABC515DFB9C632AEE0A48EF6A71EAA1E6BABD9A3884F6E710B6D935` | v2 Android Debug 签名；包含应用内出售目标价；lint/构建通过；未做设备安装或启动 |

## 仍未完成，不能宣称 Beta/上线

- Desktop、Android 与 iOS 入口均已切换到 AES-256-GCM 受保护仓储；Desktop 已实跑，Android 已通过 47 个 host 用例、lint 与 APK 构建，iOS 只完成 Kotlin/Native 交叉编译。Android/iOS 仍没有真机平台密钥、旧库升级、锁屏、卸载/恢复或代表性数据量性能证据。
- “清除本机数据”已证明应用层不可再读取旧记录且重启不会恢复示例；它通过原子替换唯一加密信封实现，不宣称能物理擦除 SSD/文件系统已回收块。面向高对手取证的密码学擦除仍依赖后续原子密钥轮换与灾难恢复设计。
- 出售目标价目前只在打开应用时从本地报价重算；尚无系统通知权限、后台刷新或授权实时行情源，因此不能描述为后台价格提醒。
- 受平台保护的初始化标记已覆盖“数据密钥落盘后、初始信封提交前崩溃”的新安装恢复，并阻止迁移中断静默变成空账本；但 v1 标记不是数据密钥与 bootstrap record 的单一原子平台事务，也没有系统级抗回滚/重放与完整轮换恢复流程。旧版本遗留的“有密钥、无信封、无标记”仍按 fail-closed 处理。
- iOS/macOS 原生编译、真机、签名、公证和商店流程需要 macOS + Xcode；Windows 不能提供该证据。
- 支付/电商/二手平台尚未取得生产 scope 或合同；沙箱和示例报价不是一键实时同步。
- 当前 Windows 便携包和 MSI 均未签名；Android APK 仅为 Debug v2 签名，R8 Release APK 未签名。MSI 行政解包/启动与每用户安装/升级/卸载已通过，但仍没有生产发布签名、已安装位置启动或 SmartScreen/Play 流程证据。
- iOS 文件适配器尚未在 Xcode、模拟器或真机运行；Windows 上的 Kotlin/Native 交叉编译不能证明 File Provider/iCloud、取消、超限、临时清理和跨重启流程正确。
- `org.jetbrains.compose.material3:material3:1.11.0-alpha07` 是预发布依赖；升级到稳定兼容版本及回归验证属于 Beta 依赖门禁。
- 全量 UI 自动化、屏幕阅读器、代表性低端设备性能、渗透测试、账户验证和加密同步仍是后续门禁。

## 2026-07-28 Apple 标准代码审计增量

本轮不再执行攻击性测试，只进行 Apple 应用质量静态审计、代码优化和正常工程回归。完整发现、源码位置、官方标准映射、命令、工件哈希和未验证边界见 `docs/14-apple-standards-code-audit.md`。

- 关闭发布 UI 占位/未配置入口、不可逆主题、系统 Reduce Motion 未合并、隐私说明/Privacy Manifest 缺失、破坏性动作弱提示、宽泛异常边界和阻塞 Worker 等问题。
- `compileIosMainKotlinMetadata` 发现并修复 `commonMain` 的 JVM-only `Math.addExact`；重跑继续进入 iOS metadata 后，被本机 Application Control 阻断 Kotlin/Native 临时 DLL。不能据此声称 Apple 编译通过。
- Desktop Kotlin 193/193；Android host 63/63，lint、Debug/R8 Release/AndroidTest APK 通过；财务应用校验器 0 error/0 warning。
- Apple readiness 13 files/42 checks；供应链 276 包 0 vulnerability/0 license violation；Connector 4/4，Price 3/3。
