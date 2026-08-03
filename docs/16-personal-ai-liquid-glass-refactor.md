# 恒迹个性化 AI 与 Liquid Glass 重构验收

状态：`WINDOWS_ANDROID_LOCAL_ACCEPTED`

日期：2026-08-03

范围：Windows、Android；iOS/macOS 不在本轮实现或验收范围

## 1. 结论

本轮已完成共享 UI、个性化洞察边界、持久化迁移和 Windows/Android 工程收口。最终结论仅为：**Windows/Android 本地代码、UI 与工程验收通过**。这不是 Apple 平台验收、商店审核、签名发布或生产联网模型批准。

核心变化：

- 移动端改为五个顶层页面的左右分页，底部悬浮 Dock 与分页双向同步；“记一笔”保持为独立操作，不占用第六个标签。
- 宽屏使用导航轨或侧栏；主题收敛到近白/石墨/玉绿色，内容区域使用高对比不透明表面，玻璃材质只用于悬浮导航和控制层。
- 空账本直接显示三步上下文引导及“记第一笔”“本地导入”入口，不用阻塞式首启教程，也不索取权限。
- 洞察页增加渐进学习阶段、学习进度、明确反馈和重置入口。排序由本机确定性规则与用户反馈共同决定。
- 可选模型只负责改写标题、摘要和动作文本；金额、证据、置信度、排序和候选选择都由本机计算与校验。
- 可见品牌文案统一为“恒迹”；历史代码标识 `Hengji` 保留以避免包名、升级 UUID 和数据兼容性破坏。

设计取舍遵循 Apple 对层级、材质和导航克制使用的原则：材质用于表达层级而不是覆盖财务内容；顶层导航保持稳定；触控与滑动手势均保留可见替代入口。实现参考 [Apple Materials](https://developer.apple.com/design/human-interface-guidelines/materials)、[Apple Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)、[Apple Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles)、[Liquid Glass 设计说明](https://developer.apple.com/videos/play/wwdc2025/219/)、[Android Pager](https://developer.android.com/develop/ui/compose/layouts/pager) 和 [Android 导航模式](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns)。这是一套跨平台设计语言，不复制 Apple 私有素材，也不把 Windows/Android 伪装成原生 Apple 应用。

视觉方向稿位于 [hengji-liquid-glass-direction.png](assets/ui-reference/hengji-liquid-glass-direction.png)。它是设计参考，不是运行截图或测试证据。

## 2. 个性化 AI 数据与安全边界

### 2.1 本机学习

- `PersonalInsightProfile` 仅从本机有效流水和显式反馈重建学习阶段、观察天数、反馈数与洞察类型偏好。
- 反馈权重限制在 70%–130%，只影响相同确定性候选的展示顺序，不改变证据或财务计算。
- 反馈类型映射持久化到 Room schema 4 和 JSON schema 4；旧账本通过 3→4 迁移补默认空映射。
- 用户可以恢复默认偏好；没有隐藏画像、行为追踪、广告标识符或后台遥测。

### 2.2 可选模型

模型默认关闭，且当前产品入口未配置远程模型实现。只有同时满足用户本次会话同意、供应商 `privacyReviewed=true`、存在本机候选三项条件时才允许调用。

允许发送的内容被类型系统限制为：

- 学习阶段与 0–10000 置信度；
- 流水数、历史天数、反馈数的区间；
- 最多五个洞察类型和静态证据代码；
- 金额影响等级、5% 百分比区间、计数/天数区间；
- `candidate-1` 至 `candidate-5` 这类不透明候选键。

不能由模型合同表达或发送：逐笔流水、精确金额、商户、备注、账户、导入原文、OCR 原文、本地实体 ID、洞察去重键或账本文件。候选到本地去重键的映射保留在 provider payload 之外。

模型输出必须选择已知候选并仅引用该候选已有证据；未知候选、伪造证据、调用异常、未评审供应商、控制字符、零宽字符和双向文本覆盖字符全部 fail-closed 回退本机洞察。模型不能覆盖本机金额、证据、置信度或排序。

## 3. UI 与可用性实现

| 区域 | 本轮实现 |
|---|---|
| 主题 | 亮/暗色语义色、系统字体、8/12/16/20/28 dp 圆角、4–48 dp 间距层级 |
| 移动导航 | 五页 `HorizontalPager`、悬浮玻璃 Dock、独立 54 dp 新增按钮、导入流程禁用顶层滑页 |
| 宽屏导航 | 700 dp 以下 Dock、700–1079 dp 导航轨、1080 dp 以上侧栏；断点按字号缩放修正 |
| 概览 | 收支/结余、分类、最近流水、洞察入口；空账本三步引导和明确下一步 |
| 流水 | 保留搜索、筛选、新增、编辑、删除撤销和日期分组合同 |
| 物品 | 轻量线框表面，持续披露成本、估值来源、沙盒/非实时状态 |
| 洞察 | 学习阶段、进度、数据基础、显式反馈、重置、模型同意与来源披露 |
| 设置/隐私 | 本地加密、导入导出、外观、减弱动效和平台能力保持可见 |
| 可访问性 | 48 dp 以上关键触控目标、分页/标签语义、pane title、200% 字号窄屏 UI 回归 |

## 4. 工程验收结果

### 4.1 静态与测试

| 门禁 | 本轮结果 |
|---|---|
| 格式检查 | 189 个文件通过：164 Kotlin、20 Python、5 TypeScript |
| 架构 / Release / Apple-readiness | 全部通过 |
| 依赖复现 | 1519 个锁定坐标、3639 个校验工件通过 |
| Desktop 全量测试 | 204/204，通过；0 failure、0 error、0 skipped |
| 覆盖率 | core-domain 94.8% line / 61.9% branch；core-insights 93.2% / 62.0%；connectors 91.9% / 53.2%，全部超过策略阈值 |
| Android host tests | 63/63，通过 |
| Android lint | 0 error、0 fatal、13 warning；6 `UnusedAttribute`、5 `UseKtx`、2 `UseTomlInstead` |
| Android 构建 | Debug、R8 Release、AndroidTest APK 全部通过 |
| 财务应用验证器 | 651 个文件，0 error、0 warning |
| 畸形导入 / 大账本 | 8/8；100000 行 4/4，109 ms、43.44 MiB |

Android Debug APK 权限复核未发现 `INTERNET`、`READ_SMS` 或 `RECEIVE_SMS`。现有权限为通知、唤醒锁、网络状态、开机完成、前台服务以及 AndroidX 动态接收器保护权限；这些服务仍受本地设置和授权行情门禁控制。

### 4.2 工件

| 工件 | 字节 | SHA-256 |
|---|---:|---|
| Windows Release JAR | 30,847,672 | `86265C047EE678D36D7588D69DF703B33444F2F7A7C1FCA1DA5B55CC91D60E3A` |
| Windows Release MSI | 55,796,120 | `7A04CFB1F291D3AD3E4E35204B8A18DC39A7118864BE9D3371D683DFAE5FD2EB` |
| Android Debug APK | 71,410,816 | `1236947BDF561091C9FB13911FB32A1923ADC4F8858862DC04415EB7296C628C` |
| Android unsigned Release APK | 50,448,840 | `876E696497D9D2266237EFC4AD129EF9D87E579D29779128AD8FF4232AE50E83` |
| AndroidTest APK | 4,430,821 | `311FF1325379DBF90866EF028B624078B721785DA90AE644AAF3C4D427471718` |

Windows MSI 安装目录从嵌套 `%LOCALAPPDATA%\Programs\Hengji` 收敛为单层 `%LOCALAPPDATA%\HengjiApp`，继续与 `%LOCALAPPDATA%\Hengji` 加密账本分离，并规避 JDK `jpackage` 在嵌套每用户目录触发的 ICE64。没有关闭 WiX 校验。最终 MSI 已通过：

- Windows Installer 行政解包；
- Release EXE 首次启动、关闭、重开；
- DPAPI 密钥材料存在，明文 `hengji.db` 为 0，密文中无示例哨兵；
- 0.0.9→0.1.0 每用户安装升级；
- 卸载后程序目录、产品注册和快捷方式清理；
- 升级与卸载期间用户加密数据保持不变。

## 5. 尚未宣称通过的边界

- 没有配置或发布真实 LLM provider；当前产品仍默认完全本机运行。未来 provider 必须独立完成数据处理协议、区域、保留期、删除和供应商隐私评审。
- 本轮没有在线 Android 设备或 API 36 模拟器，因此只构建了 AndroidTest APK，没有把 host/build 结果冒充 instrumentation 或真机结果。
- TalkBack、Narrator、真实硬件键盘、低端 Android 设备、横竖屏与系统级超大字号仍需要代表性设备验收。
- MSI、EXE 和 Release APK 均未做生产签名；SmartScreen、Windows Application Control、Play App Signing、AAB、内部测试轨和 Data Safety 未验收。
- iOS/macOS 编译、SwiftUI/AppKit 适配、签名、公证和 App Store Review 全部延期，不从 Windows 主机宣称通过。
- 视觉方向稿不是运行截图；真实亮/暗色、手机/桌面视觉回归矩阵仍应在目标设备建立。
