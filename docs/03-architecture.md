# 衡记 HENGJI：分层架构

## 1. 技术决策

客户端选择 Kotlin Multiplatform + Compose Multiplatform。官方已将 Android、iOS 和 Desktop 标为 Stable，能共享领域逻辑与大部分 UI，同时允许 Swift/Kotlin 原生逃生口；相较 Tauri 2 的移动端 WebView 路线，更适合本项目对移动端交互、原生权限和长期稳定性的要求。

语言不设单一限制：共享客户端与 Android 使用 Kotlin，iOS 平台入口/扩展使用 Swift，持久化使用 SQL，未来连接器网关使用 TypeScript，价格/分析服务可使用 Python。跨语言边界必须以 OpenAPI/JSON Schema 和契约测试约束，不能共享数据库表或隐式对象。

## 2. 逻辑分层

```text
┌──────────────────────────────────────────────────────────┐
│ Presentation: Compose screens, adaptive shell, a11y      │
├──────────────────────────────────────────────────────────┤
│ Application: use cases, orchestration, view state        │
├──────────────────────────────────────────────────────────┤
│ Domain: Money, Transaction, Asset, Usage, Insight        │
├──────────────────────────────────────────────────────────┤
│ Ports: repositories, importer, quote provider, clock     │
├──────────────────────────────────────────────────────────┤
│ Data/Infra: SQLite, files, platform vault, HTTP adapters  │
├──────────────────────────────────────────────────────────┤
│ External: official OAuth APIs, FinanceKit, price service │
└──────────────────────────────────────────────────────────┘
```

依赖只能向内：UI 不直接执行 SQL/HTTP，领域层不引用 Compose、数据库或平台 SDK，外部返回值必须先转换为内部模型。

## 3. 工程结构

```text
hengji/
├─ apps/client/                 # Compose Multiplatform 入口与 UI
│  ├─ commonMain/               # 共享 UI、状态、导航
│  ├─ androidMain/              # Android 权限/入口
│  ├─ iosMain/                  # iOS 入口桥接
│  └─ desktopMain/              # Windows/macOS 桌面入口
├─ modules/core-domain/         # 纯 Kotlin 领域模型与计算
├─ modules/core-data/           # 仓储、导入、样例数据
├─ modules/core-insights/       # 可解释分析规则
├─ modules/connectors/          # 连接器端口与沙箱适配器
├─ services/connector-gateway/  # 后续 OAuth/token/平台代理
├─ services/price-intelligence/ # 后续报价聚合、归一化
├─ docs/                        # 产品、架构、ADR、合规
└─ skills/                      # 最终可复用 Agent Skill
```

## 4. 关键模型

- `Money(minorUnits: Long, currency: CurrencyCode)`：禁止使用 `Double` 保存金额。
- `Transaction`：类型、金额、时间、分类、商户、来源、导入指纹、可选关联资产。
- `Asset`：产品名、购买价、日期、状态、使用次数、维护成本、当前估值、可选出售目标价；目标价必须为正且与购买币种一致。
- `UsageEvent`：资产、时间、数量、备注；同一天可按需求合并展示但保留原始事件。
- `MarketQuote`：提供器、规格、成色、价格、运费、采集时间、URL、置信度。
- `Insight`：类型、标题、证据、估计影响、置信度、动作、反馈状态。
- `ImportBatch`：来源、哈希、状态、条目、错误和可逆操作。

## 5. 数据与同步

首版采用 Local-first：UI 只依赖 suspend 仓储/应用网关，本地测试仍可注入内存仓储，网络连接器不是核心体验前置条件。Desktop、Android 与 iOS 入口使用平台密钥保护的 AES-256-GCM 账本信封；Room KMP 2.8.4 + bundled SQLite 2.7.0 仅保留为旧库迁移源和可注入测试实现。持久化工厂均在平台组合根创建，领域层不引用 SQL、文件系统或平台 SDK。

- 每个写操作由应用网关/use case 管理；受保护仓储在认证加密与原子 CAS 成功后才发布内存状态，Room 开发/迁移实现则用 `@Transaction` 原子提交导入与资产+购买流水等复合操作。
- 手工二手报价由应用层工厂校验规格、成色、金额、运费和日期，只能生成 `MANUAL`、`manual-local`、`isLive=false` 且无来源 URL 的报价；保存后从仓储快照重新投影估值和成本，不让 UI 伪造实时来源。
- 内存、受保护账本、Room、JSON 恢复都校验报价币种必须等于资产购买币种；已有报价时也禁止把资产改成不兼容币种，避免持久层绕过领域投影。
- 导入数据携带稳定指纹，防止重复；软删除墓碑继续占用指纹，避免把用户已删除的导入流水静默导回。
- 流水删除默认写墓碑；活跃退款会阻止删除其原交易，恢复退款也要求原交易仍活跃。应用层在同一进程内使用严格单调的删除时间作为 compare-and-set token，只在 8 秒内提供撤销；旧 token 不能复活再次删除的记录。删除与成功恢复都会跨重启持久化，但应用重启后不承诺继续显示这次短期撤销入口。
- 删除关联物品的购买流水不会级联删除物品、维护、使用或报价资料；“彻底清除”才原子替换为全空快照。
- JSON 导出格式带 `schemaVersion=3`，当前验证 v0→v1→v2→v3；恢复前有 25 MiB 上限和全快照结构/引用/币种校验。Room 当前 schema v3，显式注册 1→2 与 2→3 迁移。
- JSON/CSV 完整导出包含软删除墓碑及删除时间，界面需明确披露；恢复采用 `max(当前 revision, 导入 revision) + 1`，不得令本机 revision 倒退。
- 后续同步使用操作日志或版本向量，不做数据库文件级覆盖。
- `RoomStoragePolicy.REQUIRE_ENCRYPTED_PRODUCTION` 对直接 Room 生产入口继续 fail-closed；三平台组合根只打开受保护仓储，Room 仅在检测到旧库时作为迁移源短暂打开。Android 入口异步完成受保护账本初始化，失败时只显示重试/退出，不回退到 Room 明文仓储。
- 初始化 journal 使用同一平台密钥提供器下的专用受保护标记，单调区分全新初始化、旧库迁移与就绪；迁移标记不能被当作空账本恢复，就绪但信封缺失会 fail-closed。v2 信封把 active-key alias 纳入认证数据，轮换以新别名加密并通过 envelope CAS 原子提交，提交失败保持旧信封可读；“数据密钥 + 初始化记录”仍不是单一平台事务，系统级文件回滚和密钥丢失灾难演练继续作为安全门禁。

## 6. 分析系统

```text
Repository snapshot
  → deterministic metrics
  → rule evaluators
  → conflict/deduplication
  → impact × confidence × actionability ranking
  → optional wording adapter
  → UI with evidence
```

模型解释器是可替换端口。未授权时使用本地模板；授权远端模型时只允许聚合字段白名单，并在请求前给用户预览。

出售目标价属于确定性本地规则：原始报价历史先按资产、目标币种和非 `DEMO` 来源过滤，再用默认 90 天硬过期策略估计；只有至少 3 条入选报价、可呈现中位数且中位数不低于目标时生成 `PRICE_TARGET_REACHED`。规则不读取无日期的 `currentEstimatedValue`，稳定去重键包含资产、币种和目标最小单位。首版仅在打开应用时重新计算，不申请系统通知权限，也不在后台联网。

## 7. 平台连接器

每个连接器声明：

- `capabilities`：交易、订单、品类、退款、增量游标、撤权。
- `authorizationMode`：file、share、oauth、system entitlement。
- `dataFields`：所需字段白名单与用途。
- `availability`：sandbox、review-required、production。
- `privacyClass` 与保留期。

连接器故障不得阻塞本地记账。所有外部数据先进入预览/对账区，再由用户确认写入主账本。

## 8. 安全边界

- MVP 不接受真实 OAuth secret，不在仓库中保存 token。
- 正式 token 只能保存在 Keychain/Keystore/Credential Locker 或后端加密 vault。
- 导入解析器视文件为不可信输入：限制大小、行数、编码和公式注入。
- 日志禁止出现原始账单、token、完整 URL query 或文件内容。
- 远程价格与模型服务使用域名白名单、超时、重试上限和证书校验。
- 不在客户端内置平台私钥；需要签名的调用经受控网关完成。

## 9. 质量策略

- 领域层：快速纯单元测试和属性边界测试。
- 数据层：迁移、导入、幂等、回滚和契约测试。
- UI：状态驱动的组件测试、关键路径 UI 自动化、平台截图回归。
- 构建：依赖锁定、编译警告升级策略、SBOM、漏洞扫描、签名产物。
- 发布：Windows 与 Android 可在当前 Windows 环境验证；iOS/macOS 必须由 macOS runner、Xcode 和真实签名链验证，不能声称已在 Windows 完成。
- Release：必须测试混淆后的真实二进制。Room 生成类、领域枚举和 SQLite JNI 都可能被优化破坏；本工程用 `proguard-rules.pro` 保留应用 ABI 与 native 符号，并以便携包、MSI 行政解包后的实际 `Hengji.exe` 和 `runRelease` 启动验证，而不是只检查任务成功。Windows 使用与默认数据目录分离的每用户安装，0.0.9→0.1.0 安装器升级与卸载已验证；已安装位置启动、生产签名和 SmartScreen 仍是独立门禁。

## 10. ADR

- `ADR-001` 选择 KMP/Compose，而不是 Tauri 2/Flutter：生产稳定级别、原生互操作与平台体验优先；代价是构建链更复杂、iOS 必须 macOS。
- `ADR-002` 本地优先且无登录：先验证核心价值并减少首版攻击面；代价是首版不跨设备同步。
- `ADR-003` 平台导入通过端口接入：第三方能力和审核变化频繁，核心领域不能依赖任何单一平台。
- `ADR-004` 确定性分析优先：财务数字必须可复现；生成式模型只做透明的可选解释。
- `ADR-005` 二手报价使用区间与置信度：规格、成色和时效会使单点价格产生虚假精确感。
- `ADR-006` 出售目标先做应用内确定性关注：用近期、同币种、非示例且足量的报价触发可解释建议；系统通知和后台刷新等到平台权限、授权行情源和真机门禁齐备后再接入。
