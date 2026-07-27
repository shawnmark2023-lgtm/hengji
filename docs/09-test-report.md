# 测试与构建报告

## 2026-07-27 Windows/Android P1 收口

本轮继续只收口 Windows + Android；iOS/macOS 延期。P1 代码侧已完成密钥轮换、Android 快捷记账入口和本地 OCR/PDF 导入、聚合解释同意门控、授权报价缓存，以及系统通知的本地周期评估边界。依赖生产合同、Google Play 审批、生产签名或代表性实体设备的事项标记为 `READY_EXTERNAL`，不冒充已经获得外部验收。

| 门禁 | 结果 | 证据与边界 |
| --- | --- | --- |
| Desktop 单元测试 | 188/188，0 failure/error/skip | client 53、core-domain 17、core-data 79、core-insights 25、connectors 14；新增覆盖密钥轮换成功/提交冲突、OCR 文本解析、授权报价缓存和模型解释同意撤回 |
| Android host | 63/63，0 failure/error/skip | 包含共同受保护账本测试及新增轮换路径 |
| Android API 36 | 2/2 | 真实 AndroidKeyStore 账本创建/重开；清单断言小组件、快捷动作、文本/图片/PDF 分享、`POST_NOTIFICATIONS`，并确认无 `READ_SMS`/`RECEIVE_SMS` |
| Android 构建 | 通过 | `lintDebug` 0 error/13 warning；Debug APK、未签名 R8 Release APK 构建通过。APK 实际权限包含通知与 WorkManager 所需能力，不含短信读取/接收权限 |
| Windows 构建 | 通过 | ProGuard Release JAR 构建通过；受保护账本首次创建/同账本重开 smoke 的密文哈希、字节数与写入时间保持一致，磁盘不含明文数据库 |
| 完整质量门禁 | 通过 | 格式 179/179；架构 38/38；发布守卫 277/277；依赖/可复现策略 18/18；畸形导入 8/8 |
| 覆盖率 | 通过 | core-domain 行/分支 94.76%/61.90%；core-insights 91.91%/59.05%；connectors 91.83%/52.94%，均达到阻断阈值 |
| 10 万流水基线 | 4/4 | 106 ms、内存增量 43.46 MiB；这是开发机内存基线，不代表低端实体设备的加密持久层或完整 UI 性能 |
| 两次隔离构建 | Windows/Android 均通过 | Windows 73 个 Release JAR 路径/权限/内容规范化差异为 0；Android 两次 Debug APK 原始 SHA-256 与规范化内容均一致 |

当前重建产物：

- Android Debug APK：71,238,591 bytes，SHA-256 `0EC8E43B185896E013860409791520098609B29F1D68AF56F911B858356E4015`。
- Android 未签名 Release APK：50,416,092 bytes，SHA-256 `52C0A7B8D056B2F3D400716DDD265FC50C8EB347393A837CBCFD245FD7245074`。
- Windows ProGuard Release JAR：30,592,366 bytes，SHA-256 `19A728714E4A2DF81081660ED6ACCB5F80879C9F41BC43B2687C07C5FAB0B13B`。

P1 尚需外部条件而不能在本仓库中宣称完成的事项：

- 直接读取短信方案需要 Google Play 权限审批；当前实现改为用户主动系统分享，未申请敏感短信权限。
- 生产报价与后台提醒需要官方 API 或授权聚合合同；当前缓存拒绝演示/手工数据冒充实时源，未配置提供方时后台网络调用为 0。
- Android 锁屏、卸载、系统备份/回滚、密钥丢失和代表性低端实体设备性能仍需设备矩阵；当前只有 API 36 AVD 与 Windows 证据。
- Android Release APK 尚未生产签名；iOS/macOS 依产品指令延期。

## 2026-07-27 Windows/Android P0 增量

本轮按产品决定只收口 Windows + Android；iOS/macOS 延期，未运行其编译、签名或设备任务，也不把它们作为本轮阻断。环境为 Windows 11、JDK 21.0.2、Gradle 9.3.1、Python 3.12.13、Android API 36 AVD。

| 门禁 | 结果 | 证据与边界 |
| --- | --- | --- |
| Desktop Kotlin | 178/178，0 failure/error/skip | client 53、core-domain 17、core-data 77、core-insights 23、connectors 8；client 包含 5 个共享关键 UI 用例和 1 个 Desktop Tab/Enter 用例 |
| Android host | 61/61，0 failure/error/skip | Android 文件语义改为 `lstat` 判型与受锁保护的原子 rename，覆盖密文账本、Keystore 保护物和 Room 明文退役 |
| Android API 36 | 共享 UI 52/52；AndroidKeyStore 启动 1/1 | UI 套件中 5 个关键流程与 Desktop 复用；独立 instrumentation 在真实 AndroidKeyStore/no-backup 目录创建并重开受保护账本 |
| Android 应用冒烟 | 首次冷启动和同一账本重启通过 | 修复 `/data/user/0` 合法路径被 canonical equality 误拒绝，以及 Android/SELinux 拒绝硬链接发布导致的启动失败；磁盘只保留 no-backup 加密账本和 Keystore 包装保护物 |
| 关键 UI | Windows/Android 通过 | CSV 沙箱来源→自动映射→预览去重→确认→整批撤销；JSON/CSV 导出、清除取消/确认、空账本、JSON 恢复；删除取消/确认、8 秒撤销/超时；360dp/200%、深色、Reduce Motion、语义；Desktop Tab/Enter |
| 构建 | 通过 | Android lint、Debug APK、未签名 R8 Release APK、Desktop ProGuard Release JAR；MSI/Compose 分发任务仍因本机 WiX ZIP 不可用而失败，不计为通过 |
| 格式与静态门禁 | 通过 | 格式 163/163；架构 35/35；发布守卫 256/256；可复现/依赖策略 18/18 |
| 依赖完整性 | 通过 | 11 个必需 lockfile、1,455 个锁定坐标、3 份 verification metadata、3,516 个 SHA-256 校验工件 |
| 覆盖率 | 通过 | core-domain 行/分支 94.76%/61.90%；core-insights 91.95%/59.39%；connectors 90.95%/50.36%，均达到各自阻断阈值 |
| 两次隔离构建 | Windows/Android 均通过 | Windows 73 个 Release JAR 的路径、权限和内容规范化比较差异为 0；Android 两次 Debug APK 的原始及规范化 SHA-256 均一致 |
| 输入与性能 | 通过 | 8/8 畸形导入；100,000 流水 4/4，112 ms、内存增量 43.49 MiB；后者只是开发/CI 内存基线 |
| 辅助服务 | 通过 | connector gateway 4/4 且 `npm audit` 0 vulnerability；price intelligence 3/3 |
| 最终应用校验器 | 通过 | 扫描 865 个文件，0 error、0 warning |

本轮仍不能关闭的 P0 只有外部证据项：

- `FND-003`：仓库没有 remote；CI 门禁已配置，但没有独立远端 runner 的通过记录。
- `UX-008` / `QA-005`：自动化已覆盖语义、键盘、重排、主题和 Reduce Motion；Android TalkBack、Windows Narrator、真实硬件键盘与视觉对比度仍需专项设备/辅助技术验收。

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

依赖锁由 Gradle 内建 dependency locking 生成并以 `STRICT` 模式执行；verification metadata 校验依赖和插件工件的 SHA-256。`apps/client` 的发行桌面依赖按 `windows-x64`、`linux-x64`、`linux-arm64`、`macos-x64`、`macos-arm64` 分档，避免 `desktop-jvm-*` 与 Skiko 原生运行时在不同宿主间互相污染锁状态。Compose Hot Reload 自动创建的宿主开发配置不参与版本锁，但下载工件仍受 verification metadata 约束；它们不是发行或测试运行时。校验元数据证明内容完整性，不证明发布者身份，也不替代 SBOM、许可证或漏洞审查。当前只在 Windows 完成严格解析及实际 Desktop/Android/iOS 交叉编译；仓库已配置 Linux/macOS CI，但尚无本轮远端通过记录，因此 `FND-003` 仍保持 `PARTIAL`。

本工作树路径包含中文字符，Android Gradle Plugin 9.1.1 会在 Windows 配置阶段直接拒绝该项目路径；因此本轮 Gradle 构建通过映射到同一工作树的 ASCII 盘符执行，quality harness 在同一源码状态的 ASCII 隔离副本执行。原工作树 finance-app validator 检查 253 个源码/文档文件，0 error/0 warning；隔离副本通过架构/发布守卫、畸形导入、10 万流水与 `git diff --check`。这条宿主路径限制不应通过关闭 AGP 检查来掩盖。

当前 Windows Release 由提交 `324f8434b247` 的严格校验构建生成。ProGuard Release uber JAR 为 30,358,813 bytes，SHA-256 `C64A695BA435B65576057079C2993886E64CF58CB6D05A18853C7DDC20C0ABBF`；JDK 21 `jpackage` app-image 为 184,072,805 bytes，其中 `Hengji.exe` SHA-256 `0224147E6EC74BA7A1A2D9F593DEE833D04849C9554737999803A84351F21585`，Authenticode 状态为 `NotSigned`。

最终便携 ZIP 解压后，以独立空 `HENGJI_DATA_DIR` 启动实际 `Hengji.exe`。首次启动生成 24,147 bytes 的 `hengji.ledger.hjenc` 与 DPAPI 保护物，没有生成 `hengji.db`；关闭后以同一目录再次启动，信封 SHA-256 `05B819755AC6BB21B6601D5A72A734E22C06A6A83E5B970447C72A6A5B0016E7`、大小与最后写入时间均未变化。信封文本未命中演示资产 sentinel `asset-headphones`。这证明当前混淆、打包后的 Windows 入口能安全重开已有账本，但不替代代码签名、真实安装/升级/卸载、SmartScreen 或 macOS Keychain 验证。

本轮最新源码另以独立 `HENGJI_DATA_DIR` 连续两次执行实际 `:apps:client:runRelease`。两次均启动 Desktop 主进程；24,147 bytes 的信封 SHA-256 均为 `6FF0D6537BBF75B38ECF1E53E66DA18D389234BE4DFD4101B920E1E156B96B36`，写入时间不变，没有 `hengji.db`，DPAPI vault 同时存在主数据密钥、全新初始化与就绪标记。该证据验证本轮初始化 journal 的正常首次启动/重开路径，但不是新的便携 ZIP，也不覆盖崩溃注入或系统级回滚攻击。

无障碍本轮只取得代码审查、公共源码编译和既有单元测试证据。尚未在 macOS/iOS 上运行 VoiceOver，也未在 Android 上运行 TalkBack、在 Windows 上运行 Narrator 或完成仅键盘/高对比度矩阵，因此 `UX-008` 与 `QA-005` 均保持 `PARTIAL`。

2026-07-27 已把 API 36 `connectedDebugAndroidTest` 配置为 GitHub-hosted 模拟器独立作业；成功运行后会上传 JUnit、HTML 报告和带限制说明的证据 JSON。Apple 编译与 DMG 作业迁到仅手动触发的延期工作流，不参与当前 Windows/Android PR 门禁。仓库仍未配置 remote，因此这些状态只能写为 `CONFIGURED_CI`，不能写为独立 runner 已通过。

代表性 Android 实体机、锁屏/重启/卸载/系统恢复、TalkBack、Windows Narrator、硬件键盘和高对比度的固定矩阵与证据模板见 `docs/11-device-accessibility-validation.md`；当前各实体设备行仍为 `NOT_RUN`。

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
- 当前 Windows 便携包未签名且 MSI 未生成；Android APK 仅为 Debug v2 签名，R8 Release APK 未签名。没有生产发布签名、真实安装/升级/卸载或 SmartScreen/Play 流程证据。
- iOS 文件适配器尚未在 Xcode、模拟器或真机运行；Windows 上的 Kotlin/Native 交叉编译不能证明 File Provider/iCloud、取消、超限、临时清理和跨重启流程正确。
- `org.jetbrains.compose.material3:material3:1.11.0-alpha07` 是预发布依赖；升级到稳定兼容版本及回归验证属于 Beta 依赖门禁。
- 全量 UI 自动化、屏幕阅读器、代表性低端设备性能、渗透测试、账户验证和加密同步仍是后续门禁。
