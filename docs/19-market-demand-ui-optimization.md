# 恒迹市场需求与 UI 优化计划

状态：`DONE_LOCAL`

日期：2026-08-19（Asia/Shanghai）

范围：Windows、Android；iOS/macOS 延期。本轮不新增账户、云同步、银行直连或未经授权的平台连接器。

## 1. 产品判断

当前记账市场的基础能力已经从“能录一笔账”演进为四个高频预期：低摩擦录入、账单文件/截图导入、可执行预算反馈、可搜索的收支统计。公开竞品说明也反复强调快速记账、截图/OCR、微信/支付宝账单导入、预算、周期记录和多维搜索。

恒迹不应在这一阶段复制多人账本、云同步和复杂账户体系。它已有更鲜明的差异化基础：默认本机加密、无账户、无广告与跟踪、长截图本机 OCR、消费到物品价值的长期闭环，以及三个月后才开始的本机个人分析。本轮策略是先让最常用的“记、找、看预算”达到日常可用，再继续扩功能宽度。

## 2. 研究依据

- [钱迹 App Store 公开功能页](https://apps.apple.com/cn/app/id1473785373)：快速记账、账单导入、预算、搜索、周期记录和资产管理是成熟产品的基础预期。
- [MONO App Store 公开功能页](https://apps.apple.com/cn/app/id6670716062)：文字/截图/拍照录入、无需登录即可开始、搜索类型筛选和明确账本归属被持续优化。
- [记笔账 App Store 公开功能页](https://apps.apple.com/cn/app/id6758046297)：最近常用组合、历史账单导入、预算提醒和退款后的净消费口径直接服务于低摩擦与可信统计。
- [Apple Onboarding](https://developer.apple.com/design/human-interface-guidelines/onboarding)：引导应快速、可跳过，并尽量在功能附近提供上下文说明。
- [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)：应用需要简单、精致、易用，避免误导性功能宣称；隐私、数据最小化和无必要登录是明确要求。
- [Android Adaptive Apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)：导航和布局需要根据实时窗口空间调整。
- [Android Compose Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)：自定义交互必须提供角色、状态与可理解语义。
- [Android Minimize Permission Requests](https://developer.android.com/privacy-and-security/minimize-permission-requests)：优先使用系统选择器和临时 URI 授权，避免扩大存储与媒体权限。

## 3. 当前差距

1. 首页把固定 `¥6,500` 当作用户预算，既不能编辑，也没有持久化来源，会制造错误的“还可支配”结论。
2. 手动新增只能录今天的支出，无法直接记录收入或补录历史日期，和领域层已有的收支能力不一致。
3. 账单页只有固定分类筛选，缺少收入/支出筛选；“净额”忽略收入，名称与计算口径不一致。
4. 账单列表没有日期分组，高频核对时视觉扫描成本高。
5. 最近重复消费仍需完整重填，没有利用本机已有记录降低输入成本。

## 4. 本轮 Task List

| ID | 优先级 | 状态 | 工作与完成标准 |
| --- | --- | --- | --- |
| `MKTUI-000` | P0 | DONE_LOCAL | 固化市场判断、明确不做项、任务依赖和可量化验收。 |
| `MKTUI-001` | P0 | DONE_LOCAL | 新增本机月预算偏好，以 `Long` 最小货币单位保存；未设置时不展示虚假余额；设置、修改、清除跨重启有效；JSON/Room 向后迁移通过；洞察引擎使用同一预算事实。 |
| `MKTUI-002` | P0 | DONE_LOCAL | 手动记账支持支出/收入以及今天、昨天和合法历史日期；未来日期、非法日期、零值和溢出拒绝保存；编辑时保留原类型与日期。 |
| `MKTUI-003` | P1 | DONE_LOCAL | 从最近账单生成至多三个本机快捷模板；点击只预填商户、分类、类型和金额，仍需用户确认保存。 |
| `MKTUI-004` | P0 | DONE_LOCAL | 账单页支持全部/支出/收入/退款筛选，分类来源于当前账本，按日期倒序分组；结果统计分别展示净支出、收入和结余，不用含混“净额”。 |
| `MKTUI-005` | P0 | DONE_LOCAL | 首页重构为真实月度概览：支出、收入、结余、预算进度；无预算时显示就近设置入口，超支时明确金额但不使用羞辱性文案。 |
| `MKTUI-006` | P0 | DONE_LOCAL | 新增迁移、备份恢复、计算和 UI 回归；格式、架构、测试、Desktop Release、Android host/lint/debug/release 通过；真实桌面首页、新手引导和新增流程复测。 |

## 5. 明确不做

- 不以抓取、密码代填、剪贴板监控或短信读取换取“自动记账”。
- 不把多人共享、云同步、多账户和银行直连塞进本轮；这些功能需要完整身份、加密同步、冲突和撤销设计。
- 不引入广告、行为分析 SDK 或远程模型；现有本机分析与无网络边界保持不变。
- 不复制竞品界面或 Apple 私有素材；只采用清晰层级、稳定导航、上下文引导和可访问性原则。

## 6. 成功标准

- 新用户不设置预算时，界面不再推导“还可支配”金额。
- 常规历史支出或收入可以在一个弹窗中完成，主要字段不超过四组。
- 账单筛选结果的支出、收入、退款和结余口径可由同一组测试数据复算。
- 所有新增偏好均进入受保护账本、备份和迁移合同，不增加网络或系统权限。
- 本轮结论最多为 Windows/Android 本地代码与工程验收通过，不宣称商店或生产发布完成。

## 7. 实际交付

- 首页删除固定 `¥6,500` 假预算，改为用户主动设置的本机月预算；卡片统一展示支出、收入、结余、预算进度与超支/剩余额度。
- 手动记账支持支出与收入、今天/昨天/自定义历史日期，并从最近账单生成至多三个只预填、不自动保存的快捷项。
- 账单页增加收支类型和动态分类筛选、来源搜索、日期倒序分组，以及口径明确的净支出/收入/结余。
- 空账本首页与账单页都提供“记第一笔”和“导入旧账单”就近入口；首次引导限制宽度并支持滚动，避免桌面小窗或 200% 字体下越界。
- 月预算进入受保护账本、Room schema 6 和 JSON schema 6；5→6 迁移默认保持未设置，不替用户猜预算。
- 严格依赖验证补入两个经 Maven Central 原始文件独立复算一致的 POM SHA-256，没有关闭供应链门禁。

## 8. 本轮验收证据

| 门禁 | 结果 |
| --- | --- |
| Desktop Kotlin | 221/221，0 failure/error/skip；其中共享 Compose UI 12/12，内置模型在 Microsoft OpenJDK 21.0.12 离线推理通过 |
| Android | host 64/64；lint 0 fatal/error、20 warning；Debug、R8 Release、AndroidTest APK 构建通过 |
| 权限 | Debug/Release 均不含 `INTERNET`、`READ_SMS`、`RECEIVE_SMS` 或图库读取权限 |
| 静态门禁 | formatting 195、architecture 40、release guards 355、Apple readiness 42，全部通过 |
| 财务校验 | 745 个文件，0 error、0 warning |
| Windows | 当前源码可分发目录与 `Hengji-0.1.0.msi` 构建通过；使用 JDK 21 内置运行时 |
| 真实 UI | 隔离数据目录中实开当前 Windows 成品，复核首次引导、空账本首页和新增账单弹窗；截图发现的引导宽度/滚动问题已修复并新增 200% 字体用例 |

Android 本轮只完成 host、lint 和构建，没有把未执行的模拟器或实体机验证写成通过。MSI 与 Release APK 均未生产签名；Apple 平台仍延期。
