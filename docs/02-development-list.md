# 衡记 HENGJI：开发 List

状态：`TODO` / `DOING` / `DONE` / `BLOCKED`。优先级：P0 首版必需，P1 Beta，P2 上线。

## A. 工程基础

- [x] `FND-001` P0 建立 Kotlin Multiplatform + Compose Multiplatform 工程，目标 Android/iOS/Windows/macOS。
- [x] `FND-002` P0 分层与自动化依赖方向检查已建立，领域层禁止 Compose/SQL/HTTP/文件系统依赖。
- [ ] `FND-003` P0 版本目录、Gradle Wrapper、JDK 21、严格依赖锁与 SHA-256 verification metadata 已统一；本地全配置解析与宿主编译通过，仍待干净 CI 连续构建及产物清单一致性证据。
- [ ] `FND-004` P0 单元测试、架构/secret/沙箱门禁与依赖审计已建立；统一格式化和覆盖率阈值仍待完成。
- [x] `FND-005` P0 建立 Windows/Linux 构建 CI；预留 macOS/iOS 签名 CI。
- [x] `FND-006` P0 建立设计 token、图标、排版、间距、深浅色主题。

## B. 领域与数据

- [x] `DOM-001` P0 金额、币种、日期区间、分类、商户等值对象。
- [x] `DOM-002` P0 流水实体与新增/编辑/退款/删除规则。
- [x] `DOM-003` P0 产品资产、维护成本、使用事件、价格报价与估值实体。
- [x] `DOM-004` P0 精确实现总拥有成本、日均成本、净日均成本、单次使用成本。
- [x] `DOM-005` P0 预算、月度汇总、分类占比、趋势和异常计算。
- [x] `DAT-001` P0 suspend 仓储接口、内存测试实现与 Room KMP/bundled SQLite 持久化实现。
- [x] `DAT-002` P0 schema v2、v0→v1→v2 导出恢复、显式 Room 1→2 迁移、样例数据、幂等导入、去重和原子撤销批次。
- [x] `DAT-003` P0 完整 JSON 导出/恢复与防公式注入 CSV 导出。
- [ ] `DAT-004` P1 已实现跨平台 AES-256-GCM 账本封装及 Windows DPAPI、Android Keystore、iOS/macOS Keychain 数据密钥保护；Desktop/iOS 入口与可恢复明文迁移已接入，Android 入口和旧库迁移、Apple/Android 平台运行验收仍是 Beta 门禁。

## C. 用户体验

- [x] `UX-001` P0 自适应应用壳：移动底栏、桌面侧栏、窗口尺寸断点。
- [x] `UX-002` P0 概览：首页总览、分类占比、预算进度、洞察列表。
- [x] `UX-003` P0 流水列表、搜索筛选和新增/编辑表单。
- [x] `UX-004` P0 物品库、物品详情、使用打卡、成本指标和价格历史。
- [x] `UX-005` P0 二手比价界面，严格区分示例/手工/实时来源。
- [ ] `UX-006` P0 Desktop/Android 已有来源 → 映射 → 预览去重 → 确认 → 可撤销结果；iOS 系统文件选择适配器已实现并交叉编译，待 macOS 模拟器/真机验收。
- [ ] `UX-007` P0 四端已有本地模式；Desktop/Android 落盘已验证，iOS JSON/CSV 导出与 JSON 恢复适配器已实现并交叉编译，待 Xcode/真机验收。
- [ ] `UX-008` P0 已补齐主要表单/导航/状态语义、字体缩放重排与 Reduce Motion 行为；仍待四平台屏幕阅读器、键盘焦点和对比度实机验收。
- [ ] `UX-009` P1 小组件、快捷记账、分享扩展、桌面快捷键。

## D. 智能分析

- [x] `INS-001` P0 分类占比、预算燃烧速度、月环比和大额异常规则。
- [x] `INS-002` P0 商户集中度、重复扣款/疑似订阅规则。
- [x] `INS-003` P0 低使用资产和建议出售候选，展示节省估算与依据。
- [x] `INS-004` P0 建议排序：影响 × 置信度 × 可执行性，避免重复和冲突。
- [x] `INS-005` P0 建议反馈（采纳/稍后 7 天/忽略/恢复默认）与本地学习偏好，按稳定去重键持久化。
- [ ] `INS-006` P1 可选模型解释器；只接收脱敏聚合，不接收原始流水，默认关闭。

## E. 导入与平台连接器

- [x] `IMP-001` P0 通用连接器协议、能力声明、授权状态、游标与错误模型。
- [x] `IMP-002` P0 CSV/JSON 解析器和可配置字段映射。
- [x] `IMP-003` P0 支付宝/微信/淘宝/京东沙箱样例连接器，明确标注非真实同步。
- [x] `IMP-004` P0 OAuth 回调、token vault 接口和 PKCE/state 设计；首版不保存真实 token。
- [ ] `IMP-005` P1 OCR/PDF 解析和用户确认。
- [ ] `IMP-006` P1 Android 金融短信适配器；必须经 Google Play 权限审批并隔离非金融内容。
- [ ] `IMP-007` P1 Apple FinanceKit 适配器；受地区、eligible accounts 和 entitlement 限制。
- [ ] `IMP-008` P2 正式平台应用申请、scope 审核、隐私影响评估和连接器上线。

## F. 二手价格

- [x] `PRI-001` P0 报价提供器协议：查询、来源、时间、币种、成色、运费、置信度。
- [x] `PRI-002` P0 手工报价和演示报价提供器。
- [x] `PRI-003` P0 中位数、四分位区间、离群值过滤和新鲜度衰减。
- [x] `PRI-004` P0 产品规格归一化与匹配置信度；低置信度禁止给出单点价格。
- [ ] `PRI-005` P1 合规聚合服务和缓存；只接入已签约/官方 API。
- [ ] `PRI-006` P1 价格提醒和出售时机建议。

## G. 安全、隐私与上线

- [x] `SEC-001` P0 威胁模型：本地数据库、导入文件、OAuth token、备份、日志、模型调用。
- [x] `SEC-002` P0 日志脱敏、禁止明文 secret、依赖最小化和能力白名单。
- [ ] `SEC-003` P0 Desktop/Android 一键导出/恢复/删除和网络访问状态可见；iOS 有界导入、导出临时文件隔离/清扫与恢复已实现，待真机跨重启和隐私清理验收。
- [ ] `SEC-004` P1 平台密钥抽象与四平台实现已完成；Android/iOS/macOS 真实 Keystore/Keychain 往返和锁屏/卸载/恢复验收仍待完成。
- [ ] `SEC-005` P2 Passkey/Sign in with Apple、2FA 恢复流程、会话撤销。
- [ ] `SEC-006` P2 端到端加密同步、密钥轮换、冲突与灾难恢复演练。
- [ ] `REL-001` P2 四平台签名、公证、商店隐私清单、权限声明和发布回滚。

## H. 验收门禁

- [x] `QA-001` P0 领域单元测试：金额精度、跨月、退款、零使用、残值高于成本等边界。
- [x] `QA-002` P0 重复、公式注入、文件上限、BOM/Unicode、错列、空值、嵌套 JSON 和回滚矩阵通过。
- [ ] `QA-003` P0 记一笔、跨重启持久化、完整导入与整批撤销、洞察采纳/忽略/稍后与恢复确认已真实 UI 实跑；其余流程与自动化 UI 套件仍待补齐。
- [x] `QA-004` P0 Desktop 编译/运行与 Android APK 通过；iOS/macOS 目标交由 macOS CI 验证。
- [ ] `QA-005` P0 代码级语义、响应式重排与减少动态效果已完成；无障碍与键盘验收、200% 字体/缩放、深浅色及窄/宽窗口矩阵仍待执行。
- [ ] `QA-006` P1 10 万流水开发/CI 基线已通过；代表性低端设备、加密持久层与完整 UI 性能仍待验证。
- [ ] `QA-007` P2 上线安全审查、渗透测试、备份恢复和商店审核演练。

## 未完成项交付契约

以下条目保留原 ID/优先级，并补齐依赖与可量化验收；部分完成也保持未勾选，直至全部平台范围满足。

| ID | 状态 | 关键依赖 | 可量化验收 |
| --- | --- | --- | --- |
| FND-003 | PARTIAL | 远端 dependency-integrity CI、可复现产物清单 | 主构建与独立 quality harness 严格解析通过；桌面发行配置按 Windows/Linux/macOS 架构分档锁定；当前 Windows Release 便携包记录源码提交、大小与 SHA-256；仍需干净环境连续两次构建依赖与产物清单一致 |
| FND-004 | PARTIAL | formatter、coverage engine | Kotlin/TS/Python 格式化检查为 0；核心领域与导入模块达到约定分支覆盖率，CI 失败时阻断 |
| DAT-004 | PARTIAL | Android 入口/迁移、Apple/Android 平台 runner | AES-256-GCM envelope 已绑定版本/算法/密钥别名；Windows DPAPI、Android Keystore 与 iOS/macOS Keychain 边界均已实现且不允许明文降级；Desktop/iOS 入口、Room 明文→密文复制迁移及中断恢复已接入，iOS 密文设置 Complete File Protection 并排除系统备份；仍需 Android 接线、Apple/Android 运行验收、轮换与灾难恢复 |
| UX-006 | PARTIAL | macOS/Xcode runner、iOS simulator/device evidence | iOS 真机选择 CSV/JSON，完成映射/预览/提交/重复重试/整批撤销，全程不申请无关权限 |
| UX-007 | PARTIAL | macOS/Xcode runner、iOS simulator/device evidence | iOS 真机导出 JSON/CSV 到用户选定位置并从 JSON 恢复；清除后重启仍为空 |
| UX-008 | PARTIAL | platform screen readers、focus order、contrast audit | 主要交互已有角色/选中/标题/状态语义，表单错误可读，200% 字体可重排，Reduce Motion 可抑制不确定动画；VoiceOver/TalkBack/Narrator、仅键盘、深浅色和对比度清单仍须 100% 通过 |
| UX-009 | TODO | platform widgets/extensions | 至少 Android/iOS 各 1 个快捷记账入口、桌面全局快捷键冲突策略与撤销路径通过 |
| INS-006 | TODO | consent UI、aggregate contract、model provider | 默认零外发；只发送白名单聚合；撤回同意立即停用；离线规则结果保持可用 |
| IMP-005 | TODO | OCR/PDF parser、review UI | 20 份脱敏样本字段召回率达到目标；所有低置信度字段必须人工确认后才可提交 |
| IMP-006 | TODO | Google Play SMS declaration | 获批前构建不声明读取短信；获批版本只解析金融模板并有本地确认/删除路径 |
| IMP-007 | TODO | FinanceKit entitlement、eligible region/account | entitlement/地区/账户三重门控；不可用时功能隐藏且文件导入仍可用；真机授权/撤销通过 |
| IMP-008 | TODO | provider app、scope、DPA/合同 | 每个生产连接器有批准 scope、最小字段清单、撤权/过期/限流测试和上线回滚预案 |
| PRI-005 | TODO | 官方 API 或授权聚合合同 | 0 个抓取/私有 API；缓存 TTL、来源、运费、币种、成色和删除 SLA 均可审计 |
| PRI-006 | TODO | notification permissions、price history | 提醒阈值、冷却期、撤销和过期报价行为通过；通知不含敏感流水原文 |
| SEC-003 | PARTIAL | iOS simulator/device privacy and restart evidence | Desktop/Android/iOS 都能导出、恢复、清除并跨重启验证；网络计数为 0 时界面可见 |
| SEC-004 | PARTIAL | Android/Apple 平台 runner | Windows 当前用户 DPAPI 已完成真实与混淆产物往返；Android 已实现非导出 Keystore 包装密钥与 no-backup 保护物；iOS/macOS 已实现不迁移、不同步、仅解锁可用的 Keychain 项，macOS 使用 data-protection Keychain；仍需 Android/Apple 往返及锁屏、备份、卸载、轮换验收 |
| SEC-005 | TODO | account backend、Passkey/SIWA | 注册/验证/恢复/2FA/会话撤销/设备丢失演练通过，且不破坏无账号本地模式 |
| SEC-006 | TODO | E2EE protocol、sync engine | 双设备离线冲突、密钥轮换、恢复短语、灾难恢复和服务端不可读性测试通过 |
| REL-001 | TODO | Apple/Google/Microsoft signing accounts | 四平台生产签名、隐私声明、公证/商店审查、分阶段发布和一键回滚演练通过 |
| QA-003 | PARTIAL | UI automation harness、platform runners | 记账/物品/洞察/导入/导出/恢复/清除在每个平台自动化通过；失败保留截图与隔离数据目录 |
| QA-005 | PARTIAL | accessibility tooling、device matrix | UX-008 全矩阵通过且无 P0/P1 可访问性缺陷 |
| QA-006 | PARTIAL | encrypted DB、representative low-end devices | 10 万流水首次载入/筛选/导入峰值分别低于预算，内存不超阈值，三次运行取中位数 |
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
| DAT-001..003 | Room KMP / bundled SQLite | 9 表 schema v2、显式 1→2 迁移、跨重启持久化、完整备份恢复、幂等批次和回滚测试/实跑通过 |
| UX-001..002 | DomainDemoData / Compose | 宽屏侧栏、移动断点、同源总览/预算/占比/洞察编译并实跑 |
| UX-004..005 | repository / market estimate | 使用打卡写入仓储；成本指标与非实时二手来源实跑 |
| INS-001..004 | core-domain | 规则证据、阈值、影响、置信度、可执行性、去重排序测试通过 |
| INS-005 | core-insights / core-data / Compose | 采纳、忽略与精确 7 天稍后按稳定键持久化；跨重启 UI、Room、导出恢复和恢复默认测试通过 |
| IMP-001..004 | connectors / gateway | 协议、CSV/JSON、四个沙箱、PKCE/state、禁用 token vault 测试通过 |
| PRI-001..004 | domain / Python service | 来源、运费、四分位、离群值、新鲜度；低置信度单点隐藏测试通过 |
| SEC-001..002 | threat model / CI | 威胁模型、secret guard、脱敏 token、生产 fail-closed 测试通过 |
| QA-001 | domain tests | 金额、退款、零使用、残值、跨期、溢出和低置信度边界通过 |
| QA-004 | Gradle / Android SDK | Desktop 编译运行；Android Debug APK 构建曾通过但当前未保留交付文件；iOS source-set 元数据通过；Apple 原生仍仅配置 CI、未声称通过 |
| FND-002 | quality gates | 自动依赖方向与禁止依赖扫描通过，机器可读证据写入 `quality/evidence` |
| UX-003 | application gateway | 流水写入后首页与列表更新，重启后记录和总额仍存在 |
| QA-002 | export/import guards | 完整导出/恢复、公式中和及 8 类畸形输入契约通过 |
