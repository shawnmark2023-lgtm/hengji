# 测试与构建报告

最终验证时间：2026-07-15T19:28:50+08:00（Asia/Shanghai）。环境：Windows 11 10.0.26200 amd64、JDK 21.0.2、Gradle 9.3.1、Node 22.23.0、npm 10.9.8、Python 3.12.13。本报告只记录实际执行结果，不把工程入口等同于真机发布。

## 已通过

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Kotlin Desktop | 41/41 测试通过 | client 5、core-domain 12、core-data 4、core-insights 12、connectors 8 |
| Desktop 编译 | 通过 | `:apps:client:compileKotlinDesktop` |
| Android | Debug APK 构建通过 | `:apps:client:androidApp:assembleDebug`，compileSdk/targetSdk 36 |
| Connector Gateway | 4/4 测试、类型检查通过 | PKCE、一次性/过期 state、精确回调地址、production fail-closed |
| npm 依赖审计 | 0 漏洞 | `npm audit --audit-level=high` |
| Price Intelligence | 3/3 测试通过 | 非实时标识、低置信度隐藏单点、离群值过滤 |
| 视觉与交互 | 人工实跑通过 | 宽屏总览、侧栏、物品成本、二手来源标签、新增流水并返回账本 |

统一 Kotlin 命令：

```powershell
.\gradlew.bat :modules:core-domain:desktopTest :modules:core-insights:desktopTest `
  :modules:core-data:desktopTest :modules:connectors:desktopTest `
  :apps:client:desktopTest :apps:client:compileKotlinDesktop
.\gradlew.bat :apps:client:androidApp:assembleDebug
```

Android 产物：`apps/client/androidApp/build/outputs/apk/debug/androidApp-debug.apk`，19,974,977 bytes，SHA-256 `E6CB6A1F93670796BE2F4390FF847128BE71AB63963AEBE762E50AF79B060C3F`。

手工 UI 验收在 18:52–18:57 +08:00 完成：启动 `:apps:client:run`，观察宽屏总览；切换物品页核对两件资产与“非实时”标签；打开“记一笔”，输入“测试咖啡 / 36.50”，保存后流水数由 5 变 6 且净额增加 ¥36.50；随后修复静态总览问题并复核本月支出 ¥261.80、残值 ¥3,870.00、两类占比 50%/49%。新增数据仅在测试进程内存中，关闭进程后丢弃。

审计整改后的仓储链路在 19:22 +08:00 复验：新增“仓储测试 / ¥10.00”后流水由 5 变 6；对降噪耳机记录一次使用后，仓储 revision 触发资产重算，单次使用成本从 ¥56.00 下降到 ¥53.05。关闭进程后内存数据按设计丢弃。

GitHub Actions 当前只有工作流配置，尚无远端 CI run 结果，因此 macOS/iOS 和远端 Windows/Linux 状态均记为“未验证”，不计入通过项。

新建 Skill 的独立前向审计先发现 5 项缺陷，整改后复核为 PASS；关闭项包括完成声明、仓储垂直切片、实时报价真值、低置信度/金额溢出和发布证据。

## 已验证的关键不变量

- 金额使用 minor units 整数，不用浮点数处理财务计算。
- 演示报价不能携带外部来源 URL，也不能声明为实时数据。
- UI 新增流水和使用打卡写入内存仓储；总览、资产卡和洞察从同一快照重新计算，不再使用静态汇总数。
- 连接器生产模式没有真实配置时 fail-closed，不保存 token。
- 原始账本默认不发送到外部模型；AI 路线只允许显式同意后的脱敏聚合。

## 尚未完成，不能宣称上线

- 当前可运行客户端使用内存仓储，重启后新增数据会重置；Beta 必须完成加密 SQLite/Room KMP 与迁移恢复测试。
- iOS/macOS 原生编译、真机、签名、公证和商店流程需要 macOS + Xcode CI。
- 支付平台和二手市场均未取得生产 scope；沙箱连接器与示例报价不是“一键实时同步”。
- Windows 运行代码已实测；安装器封装因本机下载官方 WiX 归档超时未完成，CI 应重试并固定校验值。
- 全量 UI 自动化、无障碍、10 万流水性能、渗透测试和灾难恢复仍是 Beta/上线门禁。
