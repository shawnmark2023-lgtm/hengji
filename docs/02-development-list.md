# 恒迹 HENGJI：开发 List

状态：`TODO` / `DOING` / `DONE` / `BLOCKED`。优先级：P0 首版必需，P1 Beta，P2 上线。

2026-07-28 P0/P1 收口范围：Windows + Android。iOS/macOS 依本轮产品指令延期，不再阻塞本轮勾选；其平台门禁保留在仅手动触发的 `apple-deferred.yml` 和发布清单中。Android API 36 instrumentation 已在本机官方 x86_64 模拟器通过 3/3 并加入自动 CI，完整工程/安全验收见 `docs/13-engineering-security-acceptance.md`，实体设备与辅助技术验收矩阵见 `docs/11-device-accessibility-validation.md`。依赖生产合同、商店审批、签名账号或代表性实体设备的事项，以 `READY_EXTERNAL` 标记为代码侧已就绪但不冒充外部验收完成。

## A. 工程基础

- [x] `FND-001` P0 建立 Kotlin Multiplatform + Compose Multiplatform 工程，目标 Android/iOS/Windows/macOS。
- [x] `FND-002` P0 分层与自动化依赖方向检查已建立，领域层禁止 Compose/SQL/HTTP/文件系统依赖。
- [ ] `FND-003` P0 版本目录、Gradle Wrapper、JDK 21、严格依赖锁、SHA-256 verification metadata、Windows/Android CycloneDX SBOM、许可证/漏洞审计及 GitHub Action SHA 固定已统一；两份隔离源码的规范化产物一致性门禁已建立，仍因仓库未配置 remote 而缺少独立 CI runner 通过记录。
- [x] `FND-004` P0 Kotlin/Kotlin DSL、TypeScript、Python 格式门禁和 JaCoCo 覆盖率阻断已接入；共享领域、洞察、连接器均达到约定阈值。
- [x] `FND-005` P0 建立 Windows/Linux 构建 CI；预留 macOS/iOS 签名 CI。
- [x] `FND-006` P0 建立设计 token、图标、排版、间距、深浅色主题。

## B. 领域与数据

- [x] `DOM-001` P0 金额、币种、日期区间、分类、商户等值对象。
- [x] `DOM-002` P0 流水实体与新增/编辑/退款/删除规则。
- [x] `DOM-003` P0 产品资产、维护成本、使用事件、价格报价与估值实体。
- [x] `DOM-004` P0 精确实现总拥有成本、日均成本、净日均成本、单次使用成本。
- [x] `DOM-005` P0 预算、月度汇总、分类占比、趋势和异常计算。
- [x] `DAT-001` P0 suspend 仓储接口、内存测试实现与 Room KMP/bundled SQLite 持久化实现。
- [x] `DAT-002` P0 schema v5、v0→v1→v2→v3→v4→v5 导出恢复、显式 Room 1→2→3→4→5 迁移、样例数据、幂等导入、去重和原子撤销批次。
- [x] `DAT-003` P0 完整 JSON 导出/恢复与防公式注入 CSV 导出。
- [x] `DAT-004` P1 Windows/Android 已实现 AES-256-GCM 账本封装、平台密钥保护、可恢复 Room 明文迁移、受保护初始化 journal，以及带认证 active-key alias 的 v2 信封和崩溃安全密钥轮换；轮换提交失败保持旧信封可读。系统级抗回滚、密钥丢失灾难演练和 Apple 验收保留为后续安全/平台门禁。

## C. 用户体验

- [x] `UX-001` P0 自适应应用壳：移动底栏、桌面侧栏、窗口尺寸断点。
- [x] `UX-002` P0 概览：首页总览、分类占比、预算进度、洞察列表。
- [x] `UX-003` P0 流水列表、搜索筛选、新增/编辑，以及二次确认软删除与 8 秒精确 token 撤销；删除/恢复即时重投影总览与洞察。
- [x] `UX-004` P0 物品库、物品详情、使用打卡、成本指标和价格历史。
- [x] `UX-005` P0 二手比价界面与资产详情手工报价入口；保存后即时重算区间、残值和成本指标，严格区分示例/手工/实时来源。
- [x] `UX-006` P0 Windows/Android 的来源 → 映射 → 预览去重 → 确认 → 可撤销结果已完成；Apple 系统文件选择验收依本轮范围延期。
- [x] `UX-007` P0 Windows/Android 本地模式、JSON/CSV 导出、JSON 恢复和清除流程已完成并进入共享 UI 自动化；Apple 适配器验收依本轮范围延期。
- [ ] `UX-008` P0 主要表单/导航/状态语义、360dp/200% 重排、深色主题、Reduce Motion、Windows Tab/Enter 和 Android Compose Accessibility Test Framework 已自动化通过；Android TalkBack、Windows Narrator 与真实硬件键盘仍待辅助技术验收。
- [x] `UX-009` P1 Android 小组件、启动器快捷记账、文本/图片/PDF 系统分享入口，以及 Windows 全局 `Ctrl+Shift+N` 与应用内回退快捷键已接入；全局冲突不覆盖其他应用，所有入口只打开可取消的确认流程。
- [x] `UX-010` P1 首次使用提供四步可跳过教程，可直接进入“记一笔”或“导入账单”，完成状态持久化并可从设置重新打开；顶层功能统一为“首页、账单、我的物品、智能分析、设置”。

## D. 智能分析

- [x] `INS-001` P0 分类占比、预算燃烧速度、月环比和大额异常规则。
- [x] `INS-002` P0 商户集中度、重复扣款/疑似订阅规则。
- [x] `INS-003` P0 低使用资产和建议出售候选，展示节省估算与依据。
- [x] `INS-004` P0 建议排序：影响 × 置信度 × 可执行性，避免重复和冲突。
- [x] `INS-005` P0 建议反馈（采纳/稍后 7 天/忽略/恢复默认）与本地学习偏好，按稳定去重键持久化。
- [x] `INS-006` P1 Windows/Android 内置 Qwen2.5-0.5B INT4 本机模型，默认开启且可关闭；有效消费记录覆盖 90 天后第一次分析，之后最多每 30 天重算。模型结合新聚合、最近三条分析和明确反馈逐步适配，最近 12 条结果随加密账本持久化；无远程推理、运行期下载或遥测。

## E. 导入与平台连接器

- [x] `IMP-001` P0 通用连接器协议、能力声明、授权状态、游标与错误模型。
- [x] `IMP-002` P0 CSV/JSON 解析器和可配置字段映射。
- [x] `IMP-003` P0 支付宝/微信/淘宝/京东沙箱样例连接器，明确标注非真实同步。
- [x] `IMP-004` P0 OAuth 回调、token vault 接口和 PKCE/state 设计；首版不保存真实 token。
- [x] `IMP-005` P1 Android 图片 OCR/PDF 设备内解析、大小/页数/文本上限和用户逐项确认已实现；20 份脱敏文本样本通过，原文件与 OCR 原文不进入账本。ML Kit 条款与数据披露已审查，发行清单显式移除其传递依赖带入的 `INTERNET` 权限，阻断诊断/使用指标外发。
- [ ] `IMP-006` P1 Android 采用用户主动系统分享的金融短信文本适配器，构建不声明 `READ_SMS`/`RECEIVE_SMS`，非金融内容本地拒绝且不保留原文；直接短信读取须等待 Google Play 审批，因此状态为 `READY_EXTERNAL`。
- [ ] `IMP-007` P1 Apple FinanceKit 适配器；受地区、eligible accounts 和 entitlement 限制。
- [ ] `IMP-008` P2 正式平台应用申请、scope 审核、隐私影响评估和连接器上线。

## F. 二手价格

- [x] `PRI-001` P0 报价提供器协议：查询、来源、时间、币种、成色、运费、置信度。
- [x] `PRI-002` P0 手工报价和演示报价提供器。
- [x] `PRI-003` P0 中位数、四分位区间、离群值过滤和新鲜度策略；客户端当前估值硬排除超过 90 天的报价。
- [x] `PRI-004` P0 产品规格归一化与匹配置信度；低置信度禁止给出单点价格。
- [ ] `PRI-005` P1 仅接受官方/签约 API 的有界报价缓存、TTL、来源与删除审计已实现；生产聚合服务仍等待官方 API 或合同，状态为 `READY_EXTERNAL`。
- [ ] `PRI-006` P1 应用内目标价、近期实时来源判断、建议去重/稍后/忽略、系统通知授权/撤回和 WorkManager 本地周期评估已实现；撤回会立即取消周期任务，未配置授权行情源时不做后台网络请求，生产提醒仍等待授权行情源，状态为 `READY_EXTERNAL`。

## G. 安全、隐私与上线

- [x] `SEC-001` P0 威胁模型：本地数据库、导入文件、OAuth token、备份、日志、模型调用。
- [x] `SEC-002` P0 日志脱敏、禁止明文 secret、依赖最小化和能力白名单。
- [x] `SEC-003` P0 Windows/Android 一键导出/恢复/清除、确认/取消和网络访问状态可见；共享 UI、Windows DPAPI 与 Android Keystore 设备测试已覆盖清除/恢复和安全重开。Apple 隐私清理验收依本轮范围延期。
- [ ] `SEC-004` P1 Windows/Android 平台密钥、受保护账本、v2 active-key alias 与崩溃安全轮换已完成并通过 Windows/API 36 往返；代表性实体设备的锁屏/卸载/备份/密钥丢失演练仍待完成，状态为 `READY_EXTERNAL`，Apple 延期。
- [ ] `SEC-005` P2 Passkey/Sign in with Apple、2FA 恢复流程、会话撤销。
- [ ] `SEC-006` P2 端到端加密同步、密钥轮换、冲突与灾难恢复演练。
- [ ] `REL-001` P2 四平台签名、公证、商店隐私清单、权限声明和发布回滚。

## H. 验收门禁

- [x] `QA-001` P0 领域单元测试：金额精度、跨月、退款、零使用、残值高于成本等边界。
- [x] `QA-002` P0 重复、公式注入、文件上限、BOM/Unicode、错列、空值、嵌套 JSON 和回滚矩阵通过。
- [x] `QA-003` P0 Windows/Android 关键流程已自动化：删除二次确认、8 秒 Snackbar 撤销/消失、JSON/CSV 导出、清除确认/取消、恢复、语义和安全重开；共享套件在 Desktop 与 API 36 同时通过。
- [x] `QA-004` P0 Desktop 编译/运行与 Android APK 通过；iOS/macOS 目标交由 macOS CI 验证。
- [ ] `QA-005` P0 代码语义、360dp/200% 重排、深色主题、Reduce Motion、Windows Tab/Enter 与 Android 自动标签/触摸目标/对比度/遍历检查已通过；TalkBack、Narrator 和真实硬件键盘仍待实体设备/辅助技术验收。
- [ ] `QA-006` P1 10 万流水开发/CI 基线已通过；代表性低端设备、加密持久层与完整 UI 性能仍待验证。
- [ ] `QA-007` P2 上线安全审查、渗透测试、备份恢复和商店审核演练。

## 未完成项交付契约

以下条目保留原 ID/优先级，并补齐依赖与可量化验收；部分完成也保持未勾选，直至全部平台范围满足。

| ID | 状态 | 关键依赖 | 可量化验收 |
| --- | --- | --- | --- |
| FND-003 | BLOCKED | Git remote、独立 CI runner | 本地 Windows/Android 各在两个隔离 ASCII 源码副本构建并比较规范化 archive 内容；280 组件 CycloneDX 1.6 SBOM、276 包许可证/漏洞扫描、64 处 Action SHA 固定通过。PR CI 已包含 Desktop、Android host/debug、API 36 instrumentation、供应链审计，以及 Windows MSI 行政解包/加密账本、每用户安装、版本升级、已安装入口启动和卸载作业。仓库无 remote，无法产生独立 runner 通过证据 |
| FND-004 | DONE | — | 格式门禁通过；core-domain 行/分支 94.76%/61.90%，core-insights 91.91%/59.05%，connectors 91.86%/53.23%，均达到 CI 阈值 |
| DAT-004 | DONE_WIN_ANDROID | Apple deferred；系统级抗回滚/密钥丢失演练后续 | v2 envelope 将 active-key alias 纳入认证数据；启动自动发现当前代，轮换先验证旧快照、以新别名加密、CAS 提交并重开校验，提交失败保持旧信封可读；Windows/API 36 受保护账本往返通过 |
| UX-006 | DONE_WIN_ANDROID | Apple runner deferred | Windows/Android 导入路径和整批撤销已完成；Apple 平台依本轮范围延期 |
| UX-007 | DONE_WIN_ANDROID | Apple runner deferred | Windows/Android JSON/CSV 导出、JSON 恢复与清除路径已完成；Apple 平台依本轮范围延期 |
| UX-008 | PARTIAL | TalkBack、Narrator、hardware keyboard | 共享语义、360dp/200%、深色主题、Reduce Motion、Desktop Tab/Enter 与 Android Compose Accessibility Test Framework 已自动化通过；不把自动分析宣称为真实屏幕阅读器验收 |
| UX-009 | DONE_WIN_ANDROID | Apple deferred | Android 小组件、静态启动器快捷方式、文本/图片/PDF 系统分享和 Windows 全局快捷键已接入；冲突时不覆盖并保留应用内快捷键，所有入口只打开确认/取消界面 |
| UX-010 | DONE_WIN_ANDROID | Apple deferred | 四步教程、跳过/返回/直达动作、完成状态持久化、设置内重新打开和通俗功能命名均有共享 UI 测试 |
| INS-006 | DONE_WIN_ANDROID | 代表性 Android 实体机性能仍待外部矩阵 | 固定模型与运行时随包交付；90 天资格和 30 天节流由本机代码控制，关闭时不加载模型。实际 Windows 推理测试通过；Android AAR 无遥测且所有 ELF LOAD 段为 16KB 对齐，APK 无 `INTERNET` |
| IMP-005 | DONE_ANDROID | Windows 保留既有文件导入；Apple deferred | 20 份脱敏文本解析样本通过；图片/PDF 在 Android 设备内识别，限制 20 MiB、20 页和 100,000 字符，候选字段必须进入人工确认后才可保存；合并清单断言不含 `INTERNET`，阻断 ML Kit 诊断/使用指标外发 |
| IMP-006 | READY_EXTERNAL | Google Play SMS declaration（仅直接读取方案需要） | 当前 APK 不声明读取/接收短信权限；用户主动分享的金融文本仅本地解析，非金融文本拒绝，原文不保留。直接读取不在未获批构建中启用 |
| IMP-007 | TODO | FinanceKit entitlement、eligible region/account | entitlement/地区/账户三重门控；不可用时功能隐藏且文件导入仍可用；真机授权/撤销通过 |
| IMP-008 | TODO | provider app、scope、DPA/合同 | 每个生产连接器有批准 scope、最小字段清单、撤权/过期/限流测试和上线回滚预案 |
| PRI-005 | READY_EXTERNAL | 官方 API 或授权聚合合同 | 代码只接受 `OFFICIAL_OR_CONTRACTED_API` 实时报价；缓存有容量/TTL、来源、运费、币种、成色与删除审计，演示/手工数据不能进入；候选准入与产品输入见 `docs/12-authorized-market-provider-decision.md` |
| PRI-006 | READY_EXTERNAL | authorized price feed | Android 仅在用户主动授权通知后调度 WorkManager，应用内撤回会清除 opt-in 并取消唯一周期任务；任务只评估账本中明确标记为实时来源的报价并发送不含流水原文的通用通知。未配置授权行情源时后台网络调用为 0 |
| SEC-003 | DONE_WIN_ANDROID | Apple privacy evidence deferred | Desktop/API 36 共享 UI 覆盖导出、恢复、清除及确认路径；Windows DPAPI 与 Android Keystore 设备往返通过，Apple 依本轮范围延期 |
| SEC-004 | READY_EXTERNAL | representative Android device；Apple deferred | Windows 当前用户 DPAPI 与 Android 不可导出 Keystore 往返通过；v2 信封与轮换成功/提交失败恢复均有测试，API 36 创建/重开通过。仍需实体 Android 的锁屏、卸载、备份、系统回滚与密钥丢失演练 |
| SEC-005 | TODO | account backend、Passkey/SIWA | 注册/验证/恢复/2FA/会话撤销/设备丢失演练通过，且不破坏无账号本地模式 |
| SEC-006 | TODO | E2EE protocol、sync engine | 双设备离线冲突、密钥轮换、恢复短语、灾难恢复和服务端不可读性测试通过 |
| REL-001 | TODO | Apple/Google/Microsoft signing accounts | 四平台生产签名、隐私声明、公证/商店审查、分阶段发布和一键回滚演练通过 |
| QA-003 | DONE_WIN_ANDROID | Apple runner deferred | Desktop client 57/57、Android 共享 UI 52/52、API 36 instrumentation 历史基线 3/3（平台入口、Keystore 启动、自动无障碍）；本轮 Apple 质量重构后的共享 UI 由 Desktop 复用测试覆盖，API 36 未复跑 |
| QA-005 | PARTIAL | TalkBack、Narrator、真实硬件键盘 | 自动化矩阵已覆盖共享语义、360dp/200%、深色主题、Reduce Motion、Desktop 键盘和 Android Accessibility Test Framework；固定实体设备/辅助技术矩阵与证据模板已写入 `docs/11-device-accessibility-validation.md`，尚未执行 |
| QA-006 | READY_EXTERNAL | representative low-end physical device | 开发机 100,000 流水基线 4/4 通过，97 ms、内存增量 43.82 MiB；实体机型号、环境、场景、指标和证据模板已固定，尚无代表性低端 Android 的加密持久层与完整 UI 性能结果 |
| QA-007 | TODO | external security review、store dry run | 高危漏洞为 0；加密备份恢复成功；四平台审核材料与回滚桌面演练签字完成 |

## 首轮开发完成定义

1. Windows 桌面开发版本可运行，包含样例数据和无网络核心体验。
2. 可以新增流水、关联物品、记录使用、查看四种成本指标。
3. 可以看到分类占比和至少三类可解释建议。
4. 可以查看手工/演示二手报价及置信度，不把演示数据伪装成实时数据。
5. CSV/JSON 导入协议、预览、去重和撤销路径有实现或可测试契约。
6. Android/iOS/macOS 目标与平台入口存在；受本机限制的构建在文档与 CI 中明确。
7. 核心测试、lint、构建脚本、架构文档和上线前路线齐全。

## 已完成项验收索引

只有打勾项进入此表；`依赖` 和 `验收证据` 是完成声明的一部分。

| ID | 依赖 | 验收证据 |
| --- | --- | --- |
| FND-001 | Kotlin 2.4 / Compose 1.11 | Android、Desktop、iOS 入口与目标存在；Desktop/Android 本机编译通过 |
| FND-005 | GitHub Actions | Windows/Linux Desktop、Android、macOS iOS 编译作业已配置；尚无远端 run 结果 |
| FND-006 | Compose UI | token、品牌标记、排版、间距、自适应深浅色主题实跑通过 |
| DOM-001..005 | core-domain / core-insights | 精确金额、日期、流水、资产、成本、预算、占比、趋势与异常测试通过 |
| DAT-001..003 | Room KMP / bundled SQLite | 9 表 schema v3、显式 1→2→3 迁移、跨重启持久化、完整备份恢复、幂等批次和回滚测试/实跑通过 |
| UX-001..002 | DomainDemoData / Compose | 宽屏侧栏、移动断点、同源总览/预算/占比/洞察编译并实跑 |
| UX-004..005 | repository / market estimate | 使用打卡写入仓储；成本指标与非实时二手来源实跑 |
| INS-001..004 | core-domain | 规则证据、阈值、影响、置信度、可执行性、去重排序测试通过 |
| INS-005 | core-insights / core-data / Compose | 采纳、忽略与精确 7 天稍后按稳定键持久化；跨重启 UI、Room、导出恢复和恢复默认测试通过 |
| IMP-001..004 | connectors / gateway | 协议、CSV/JSON、四个沙箱、PKCE/state、禁用 token vault 测试通过 |
| PRI-001..004 | domain / Python service | 来源、运费、四分位、离群值、新鲜度；低置信度单点隐藏测试通过 |
| SEC-001..002 | threat model / CI | 威胁模型、secret guard、脱敏 token、生产 fail-closed 测试通过 |
| QA-001 | domain tests | 金额、退款、零使用、残值、跨期、溢出和低置信度边界通过 |
| QA-004 | Gradle / Android SDK | Desktop 编译运行；Android lint、Debug APK 与未签名 R8 Release 构建通过，Debug APK 已保留；历史 iOS arm64/simulator arm64 Kotlin klib 曾通过，本轮源码在 Windows 上完成 common metadata 可移植性修复后被 Application Control 阻断于 Kotlin/Native DLL，Apple 原生仍需 macOS/Xcode 验证 |
| FND-002 | quality gates | 自动依赖方向与禁止依赖扫描通过，机器可读证据写入 `quality/evidence` |
| UX-003 | application gateway | 流水写入、确认删除和 8 秒撤销会同步刷新首页、列表与洞察；墓碑与成功恢复状态均跨重启持久化，旧 token 不能复活再次删除的记录 |
| QA-002 | export/import guards | 完整导出/恢复、公式中和及 8 类畸形输入契约通过 |
