# 恒迹 Windows/Android 工程与安全验收

## 2026-08-12 桌面 UI、安装卸载与全量复验

本轮在不改变领域模型、金额精度、账本格式、网络权限或 Apple 延期边界的前提下，完成 Windows 桌面 UI 第二轮精简和全部本机可运行门禁。使用隔离数据启动真实 Release 分发目录，逐页走查首页、账单、物品、智能分析和设置；桌面侧栏从 272 dp 收至 232 dp，选择态改为细强调线，空首页不再展示无意义的零值卡片，账单/物品使用语义正确的空状态，卡片边界进一步降噪。

- 静态门禁：formatting 195、architecture 40、release guards 353、Apple readiness 42、reproducibility 18、供应链 inventory 352，全部通过。
- 覆盖率：core-domain line/branch 94.7619%/61.9048%；core-insights 93.5053%/60.7914%；connectors 92.9078%/56.1769%，全部高于策略阈值。
- Desktop：216/216；UI 文案变化引起的 2 个旧断言已更新后完成全量复跑。Release/ProGuard 与 MSI 均构建通过。
- Android：host 63/63；lint 0 fatal/0 error/20 warning；Debug、R8 Release、AndroidTest APK 构建通过；Debug/Release 均无 `INTERNET`、`READ_SMS`、`RECEIVE_SMS`。隔离 API 36 Google APIs x86_64 模拟器 instrumentation 5/5，执行后关闭。
- 输入/性能：畸形导入 8/8；100,000 行 4/4，115 ms、内存增量 43.67 MiB，仅为开发机内存基线。
- 辅助服务：Connector 4/4、Price 3/3、TypeScript 与 Pyright strict 通过、npm audit 0；隔离 wheel 构建通过。
- 财务验证：731 files，0 error，0 warning。供应链：276 个锁定包，0 已知漏洞、0 许可证违规；64 个 Actions 引用继续固定完整 SHA。
- MSI：当前源码 0.0.9 → 0.1.0 每用户安装、已安装 EXE 启动、升级、升级后启动、卸载、注册/安装目录/快捷方式清理和隔离加密数据保留全部通过。最终系统状态为恒迹产品注册 0、`%LOCALAPPDATA%\HengjiApp` 不存在、快捷方式 0。
- 用户真实账本未参与测试且未被修改：`%LOCALAPPDATA%\Hengji\hengji.ledger.hjenc` 24,147 bytes，操作前后 SHA-256 均为 `E09D0859572F945F86498AEE60306A8CC2273FFBE1DEAAF43C7CB5954FB4BD78`。

本轮最终源码工件：Windows MSI 449,704,712 bytes，SHA-256 `BAC0E4AF3324B232DC397840120FDD95C26A9FC8EF4F4D978A5A3133D225ED28`；Android Debug APK 481,402,079 bytes，SHA-256 `2F9BCD13185196AF4C095F8843C08274D07074C2B92ECE589370038C46EB1E5D`；Android R8 Release unsigned APK 387,074,852 bytes，SHA-256 `A44CF9DA852A0ED367D393D250F765FA7B3F0E4D535436FBBD2C452D481C6AD6`；AndroidTest APK 4,452,035 bytes，SHA-256 `7CE8D5E646D65CEF3C61776257FB1DA21FC16FA97AB200626FB59FC16001B881`。MSI 与 Release APK 均未生产签名，不作为公开发布包。

当前仓库内 Critical/High/Medium 未关闭安全问题仍为 0。生产签名、SmartScreen、商店发布、远端 runner、代表性实体设备与真实辅助技术仍按第 9 节保持外部边界；不宣称 Beta、生产或商店发布完成。

日期：2026-07-28（Asia/Shanghai）

范围：Windows、Android、共享 Kotlin 模块、Connector Gateway、Price Intelligence、构建与供应链。iOS/macOS 明确延期。本报告记录当前源码在本机的实际执行结果，不把历史结果、模拟器或未签名工件冒充真机、生产、Beta、商店或远端 CI 证据。

## 1. 结论

**Windows/Android 本地代码与工程验收通过。**

- 本机可运行的格式、架构、发布守卫、依赖复现、覆盖率、畸形导入、10 万流水、Desktop、Android host/build/API 36 instrumentation、Windows MSI、辅助服务、供应链和财务应用校验器均通过。
- 当前扫描未发现 Critical/High/Medium 未关闭的仓库内安全问题；本轮关闭 3 个 Medium 安全/隐私/供应链问题及 1 个工程正确性问题。
- Android Debug/Release APK 均不声明 `INTERNET`、`READ_SMS`、`RECEIVE_SMS`；OCR 输入在本机处理，账本不保存原文件或 OCR 原文。
- Windows 受保护账本使用 AES-256-GCM 与当前用户 DPAPI；Android 使用 AES-256-GCM、Android Keystore 和 no-backup 保护物。Windows 安装、升级、已安装入口启动、卸载及加密数据保留通过。
- 仍需外部完成的门禁集中在生产签名/商店/远端 runner、实体设备与辅助技术、正式授权行情源、第三方渗透测试；这些不影响本报告的“本地代码与工程”结论。

## 2. Task List 关闭状态

| Task | 状态 | 本轮结论 |
| --- | --- | --- |
| T01 当前改动审查 | `DONE_LOCAL` | 逐项审查供应链脚本、CI SHA、Python 构建依赖、Android 权限及文档；清除无用参数，`git diff --check` 通过 |
| T02 工作树卫生 | `DONE_LOCAL` | `*.egg-info/` 已忽略，生成目录已移除；证据、缓存与构建产物保持 ignored |
| T03 进度清单校准 | `DONE_LOCAL` | `docs/02-development-list.md` 使用 `DONE_WIN_ANDROID`、`READY_EXTERNAL`、Apple deferred 边界；本报告只记录本轮实跑 |
| T04 静态工程门禁 | `DONE_LOCAL` | 全部门禁退出码 0，结果见第 4 节 |
| T05 Desktop 工程验收 | `DONE_LOCAL` | 193/193 测试、ProGuard Release、Release uber JAR 通过 |
| T06 Windows 安装包验收 | `DONE_LOCAL` / `READY_EXTERNAL` | 本机 MSI 全生命周期和受保护账本通过；签名、SmartScreen、组织 Application Control 签名候选包为外部事项 |
| T07 Android 构建验收 | `DONE_LOCAL` | host、lint、Debug/R8 Release/AndroidTest APK 与权限检查通过 |
| T08 Android API 36 设备验收 | `DONE_LOCAL_AVD` / `READY_EXTERNAL` | 本轮官方 API 36 x86_64 AVD 3/3；没有冒充真机，实体设备矩阵仍外部 |
| T09 辅助服务验收 | `DONE_LOCAL` | connector、price、严格类型检查、wheel、audit 全通过 |
| T10 供应链安全验收 | `DONE_LOCAL` | SBOM schema、固定校验和 OSV、许可证与 Action SHA 门禁通过 |
| T11 应用安全验收 | `DONE_LOCAL` | 加密、平台密钥、迁移、轮换、导入/导出、OAuth、生产 fail-closed、日志/secret 边界复核通过 |
| T12 隐私与权限验收 | `DONE_LOCAL` / `READY_EXTERNAL` | 默认无网络、OCR/短信边界和 APK 权限通过；未来联网、Data Safety 与商店披露为外部事项 |
| T13 安全问题修复闭环 | `DONE_LOCAL` | 3 个 Medium 已修复并复跑，当前 Critical/High/Medium 未关闭数为 0 |
| T14 财务应用总验收 | `DONE_LOCAL` | finance-app validator：0 error、0 warning |
| T15 证据与报告 | `DONE_LOCAL` | 本报告与开发清单、发布清单、测试报告、设备矩阵同步 |
| T16 Git 收口 | `DONE_LOCAL` | 本报告及对应源码由同一收口提交承载；仓库无 remote，不宣称推送或远端 CI |

## 3. 环境与证据原则

- Windows 11 `10.0.26200`，JDK 21.0.2，Gradle 9.3.1，Node 22.23.0，npm 10.9.8，Python 3.12.13。
- Android SDK API 36、Build Tools 36.0.0、Platform Tools 37.0.1；官方 Emulator 36.6.11 与 Google APIs API 36 x86_64 r7。
- 中文工作树通过同一源码的 ASCII 盘符运行主 Gradle 构建；quality harness 已为 Windows 驱动器根目录显式设置 composite build 名称，可在同一 `subst` 源码上直接执行，不再需要复制源码。
- 所有计数均来自本轮 JUnit、门禁 JSON 或命令退出码；旧报告只用于历史追溯。
- 仓库无 remote；GitHub Actions 只验证配置与不可变引用，不能标记为远端 `passed`。

## 4. 实际命令与结果

### 4.1 静态工程门禁

```powershell
python scripts\quality\run_quality.py --project T:\ `
  --gates formatting architecture release-guards apple-readiness reproducibility supply-chain-inventory coverage `
  --output-dir T:\quality\evidence\final-static
```

结果：

| 门禁 | 结果 |
| --- | --- |
| formatting | 187 files：162 Kotlin、20 Python、5 TypeScript |
| architecture | 39/39 |
| release guards | 299/299 |
| Apple readiness | 13 files、42 条静态不变量 |
| reproducibility | 18/18；1,519 locked coordinates；3,639 verified artifacts |
| supply-chain inventory | 352/352；280 components；276 scan packages；64 Action refs |
| core-domain coverage | line 94.7619%，branch 61.9048% |
| core-insights coverage | line 91.9054%，branch 59.0517% |
| connectors coverage | line 91.8619%，branch 53.2338% |

同一 ASCII `subst` 源码：

```powershell
python scripts\quality\run_quality.py --project T:\ `
  --gates malformed-import large-ledger
```

- 畸形导入 8/8：未闭合引号、错列、重复表头、嵌套 JSON、行数超限、文件超限、必填空值、BOM/Unicode。
- 100,000 流水 4/4：97 ms，内存增量 43.82 MiB，金额聚合使用 minor-unit `Long`；仅为开发机内存基线。

### 4.2 Desktop 与 Windows

```powershell
.\gradlew.bat desktopTest :apps:client:proguardReleaseJars `
  :apps:client:packageReleaseUberJarForCurrentOS `
  --dependency-verification strict --no-configuration-cache --no-daemon --console=plain
```

- Desktop 193/193，0 failure/error/skip：client 57、core-domain 17、core-data 79、core-insights 25、connectors 15。
- ProGuard Release 与 Release uber JAR 通过。

```powershell
.\scripts\quality\verify-windows-msi.ps1 -MsiPath <Hengji-0.1.0.msi>
.\scripts\quality\verify-windows-msi-lifecycle.ps1 `
  -BaselineMsiPath <Hengji-0.0.9.msi> -UpgradeMsiPath <Hengji-0.1.0.msi>
```

- 行政解包退出码 0；实际 `Hengji.exe` 首次启动与重开各保持 2 个进程存活。
- 加密账本 24,294 bytes，3 个 DPAPI 文件，0 个明文数据库，密文不含样例 sentinel；重开未改写密文。
- 0.0.9 安装、已安装 EXE 启动、0.1.0 升级、升级后 EXE 启动、旧产品移除、卸载、快捷方式清理与加密用户数据保留均通过。
- 两个 MSI 含相同应用代码，只证明安装器版本迁移，不替代历史 schema 迁移。

### 4.3 Android

```powershell
.\gradlew.bat :modules:core-data:testAndroidHostTest `
  :apps:client:androidApp:lintDebug `
  :apps:client:androidApp:assembleDebug `
  :apps:client:androidApp:assembleRelease `
  :apps:client:androidApp:assembleDebugAndroidTest `
  --dependency-verification strict --no-configuration-cache --no-daemon --console=plain
```

- Android host 63/63，0 failure/error/skip。
- lint：0 fatal/error，13 warning；6 个 API/min 提示、5 个 UseKtx、2 个 UseTomlInstead，均非安全错误。
- Debug APK、R8 Release APK、AndroidTest APK 构建通过。
- `aapt2 dump permissions` 验证 Debug/Release 不含 `INTERNET`、`READ_SMS`、`RECEIVE_SMS`。
- Debug APK 为 Android Debug 证书 v2 签名；Release APK 未签名，符合本地验收边界。

```powershell
.\gradlew.bat :apps:client:androidApp:connectedDebugAndroidTest `
  --dependency-verification strict --no-configuration-cache --no-daemon --console=plain
```

- 官方 API 36 Google APIs x86_64 AVD：3/3。
- 覆盖平台入口/权限、真实 AndroidKeyStore 受保护账本创建—关闭—重开、真实 `MainActivity` Compose Accessibility Test Framework。
- 本轮主机 WHPX 可用；模拟器已在执行后关闭。

### 4.4 辅助服务

```powershell
cd services\connector-gateway
npm ci --ignore-scripts
npm run typecheck
npm run typecheck:price
npm test
npm audit --audit-level=high
```

- TypeScript typecheck 通过；Connector 4/4；Price Pyright strict 0 error/0 warning；npm audit 0 vulnerability。

```powershell
python -m pytest services\price-intelligence
python -m compileall services\price-intelligence\src
python -m pip wheel services\price-intelligence --no-deps --wheel-dir <isolated-dir>
```

- Price pytest 3/3；compileall 与隔离 PEP 517 wheel 构建通过。

### 4.5 供应链

```powershell
python scripts\quality\install_osv_scanner.py --output .tools\osv-scanner.exe
python scripts\quality\verify_supply_chain.py `
  --scanner .tools\osv-scanner.exe --output-dir quality\evidence\supply-chain
npx --yes ajv-cli@5.0.0 validate `
  -s <official-cyclonedx-1.6-schema> `
  -d quality\evidence\supply-chain\hengji-windows-android.cdx.json
```

- OSV Scanner 2.3.8 二进制由版本和 SHA-256 双重固定。
- 276 个锁定包：0 已知漏洞、0 许可证违规。
- 11 个 Google ML Kit/Play Services 非标准条款组件采用精确包名、精确版本、官方条款 URL 和 2027-07-27 复审日期的显式例外。
- CycloneDX 1.6 JSON 通过官方 schema；280 个组件。
- 64 个 GitHub Action `uses:` 全部固定为策略允许的 40 位提交 SHA。
- 当前跟踪内容与完整 Git 历史的高置信 secret 模式扫描均为 0 命中。

## 5. 工件清单

| 工件 | Bytes | SHA-256 | 发布边界 |
| --- | ---: | --- | --- |
| Android Debug APK | 71,238,563 | `63DF0AD1B53FFAF053CA872A537C469465C4E3A8962DF9338605C465335D5294` | Debug v2 签名 |
| Android Release APK | 50,416,072 | `B218B9EE61DE2EEEA63C006AC39DBE09804262CD99C53033960B2EAD10942C87` | R8，未签名 |
| Android instrumentation APK | 4,430,821 | `311FF1325379DBF90866EF028B624078B721785DA90AE644AAF3C4D427471718` | 测试工件 |
| Windows Release uber JAR | 30,592,366 | `19A728714E4A2DF81081660ED6ACCB5F80879C9F41BC43B2687C07C5FAB0B13B` | ProGuard |
| Windows MSI 0.0.9 | 108,695,964 | `6E286B1FE75655FE57A67F1D77E98CED9D1784274C16A5221D72F682374531C8` | 同代码升级基线，未签名 |
| Windows MSI 0.1.0 | 108,695,964 | `837D1DEB11E1CC9A4F32D98C5F37D3A71881D1CCF23C3BC70F518ECE5B1386F3` | 当前安装包，未签名 |
| MSI 解包 `Hengji.exe` | 545,280 | `4FAEC71D47090EB22A45C2F061F3D3A3FE60B7E2B0357BC658766ADC6F5315A8` | Authenticode `NotSigned` |
| Price Intelligence wheel | 8,195 | `8DADB21902D4550B8556623F55E3D8981D1FABE1D7284A33427C2A3021A69095` | `--no-deps` 隔离构建 |
| CycloneDX 1.6 SBOM | 193,049 | `0600986717420E3B8FD41F717831F018E07A3F88F8C44574B21CC68991856358` | Windows/Android + Node/Python build tools |
| OSV JSON | 68,706 | `F7DC4FA35C2C63B2D7F15D3A73074590C0C937C5736194F348C4539C2CA4C7AA` | 276 包 |

## 6. 安全发现与修复

| ID | 严重度 | 状态 | 发现与修复 | 证据 |
| --- | --- | --- | --- | --- |
| SEC-R01 | Medium | `RESOLVED` | Python 构建后端 `setuptools==81.0.0` 受 CVE-2026-59890 影响；升级并精确固定到 83.0.0 | `services/price-intelligence/pyproject.toml:2`；当前 OSV 0 |
| SEC-R02 | Medium | `RESOLVED` | ML Kit/Google Data Transport 传递清单可带入 `INTERNET`，与默认无网络隐私边界冲突；用 manifest merger 显式移除并在 instrumentation/APK 双重断言 | `apps/client/androidApp/src/main/AndroidManifest.xml:5-8`；`P1PlatformSurfaceInstrumentedTest.kt:25-27` |
| SEC-R03 | Medium | `RESOLVED` | GitHub Actions 使用可变标签会扩大供应链替换风险；64 个引用固定为策略允许的完整提交 SHA，新增门禁阻止回退 | `.github/workflows/*.yml`；`quality/supply-chain-policy.json` |
| ENG-R01 | Engineering | `RESOLVED` | 严格 Pyright 在不可信 JSON 边界发现 6 个 unknown-type 问题；完成对象/键/嵌套报价显式验证与类型收窄，并把 Pyright 1.1.403 固定为 CI 门禁 | `services/price-intelligence/src/hengji_price_intelligence/contracts.py`；`server.py`；`services/connector-gateway/package.json:13,19` |

当前未关闭仓库内发现：Critical 0、High 0、Medium 0。

## 7. 应用安全复核

- **受保护账本**：32-byte key、96-bit nonce、AES-GCM tag、算法/nonce/密文长度校验与认证失败 fail-closed，见 `modules/core-data/src/commonMain/kotlin/com/hengji/data/StorageSecurity.kt:10-15,36,77-137`。
- **Windows 密钥**：当前用户 DPAPI `cryptProtectData`/`cryptUnprotectData`，UI 禁止、格式/大小/普通文件检查，见 `WindowsDpapiDatabaseKeyProvider.kt:32-75,91-115`。
- **Android 密钥**：AndroidKeyStore AES/GCM、随机加密、AAD、不可导出 wrapping key、no-backup 文件与原子发布，见 `AndroidKeystoreDatabaseKeyProvider.kt:130-169,245-296`。
- **明文迁移与轮换**：明文与密文冲突时 fail-closed；新 key alias 加密后以 envelope CAS 提交，提交冲突保留旧信封可读并验证新快照，见 `ProtectedLedgerKeyRotation.kt:27-58`。
- **不可信导入**：内容/单元格/列/行上限，结构/表头/错列/BOM/Unicode/嵌套 JSON 拒绝，金额仅转 minor-unit `Long` 且显式溢出检查，见 `modules/connectors/.../ImportParsing.kt:85-177`。
- **CSV 公式注入**：去除前导空白后以 `= + - @` 开头的字段加前置单引号并双引号转义，见 `modules/core-data/.../LedgerCsvExporter.kt:43-44`。
- **OAuth**：S256 PKCE、32-byte state、64-byte verifier、5 分钟 TTL、单次消费、常量时间 state 比较、redirect URI 精确白名单，见 `services/connector-gateway/src/oauth-state.ts:24-81` 与 `server.ts:76-107,144-147`。
- **生产连接器**：未配置审查后的 provider registry/token vault 时启动即 fail-closed；JSON body 64 KiB 上限、内容类型与对象类型校验，见 `services/connector-gateway/src/server.ts:16,30-36,119-147`。
- **行情来源**：只有 `OFFICIAL_OR_CONTRACTED_API` 可进入授权缓存/实时估值；演示和手工数据不可冒充实时来源，见 `modules/connectors/.../AuthorizedQuoteCache.kt:18,51`。
- **日志与 secret**：发布守卫、当前跟踪内容和 Git 历史扫描未发现高置信凭据；证据 JSON 不写入真实金融原文、token 或个人联系/支付标识。

## 8. 隐私与权限复核

- 默认网络权限为 0；当前 APK 没有 `INTERNET`。WorkManager 保留 `ACCESS_NETWORK_STATE`、通知/唤醒/开机能力，但未配置授权行情源时不会进行后台网络请求。
- ML Kit 识别输入在设备内处理；官方数据披露同时说明 SDK 可能收集设备/应用信息及性能/使用指标，因此本项目不作“SDK 天生零遥测”声明，而以移除 `INTERNET` 阻断当前应用级传输。
- 图片/PDF 仅在短生命周期对象解析；候选字段必须经用户确认才入账，原文件和 OCR 原文不进入账本。
- 短信仅由用户主动系统分享；当前 APK 不请求读取/接收短信权限，非金融文本本地拒绝且原文不持久化。
- 未来正式行情联网必须独立取得授权、恢复最小网络权限、更新 Data Safety/隐私披露、域名白名单、字段清单、撤权和删除门禁；不得沿用当前离线验收结论。

## 9. 未验证与外部事项

以下项目不能由当前仓库/主机独立完成，保持 `READY_EXTERNAL` 或延期：

- 远端独立 CI runner 与隔离复现通过记录；仓库当前无 remote。
- Windows 生产代码签名、SmartScreen 信誉，以及组织 Application Control 管控位置的签名候选包。
- Android AAB、Play App Signing、内部测试轨、Data Safety 与商店审核。
- Android 代表性实体设备上的 TalkBack、硬件键盘、低端性能、锁屏、强停/重启、卸载/重装、系统备份/迁移、密钥丢失和系统回滚。
- Windows Narrator、高对比度、125%–300% 缩放与真实辅助技术矩阵。
- 正式行情 API/授权聚合合同、SMS 直接读取权限审批、生产通知验证。
- 第三方渗透测试与生产发布/回滚演练。
- 全部 iOS/macOS 开发、签名、设备与商店验收。

这些事项不以历史证据或模拟器结果替代，也不在本报告中声明完成。
