# 衡记 HENGJI

看见每一笔消费的长期价值。

衡记是一款本地优先的跨平台消费价值管理应用，目标平台为 iOS、Android、Windows 和 macOS。它把普通流水、产品资产、使用记录、二手残值和可解释洞察连接起来，让用户同时看到“花了多少”和“是否值得”。

## 当前交付范围

- Kotlin Multiplatform + Compose Multiplatform 客户端。
- 无登录、本地优先的首版体验。
- 流水、产品资产、使用成本、二手报价和智能洞察领域模型。
- CSV/JSON 导入与官方 OAuth 连接器边界。
- Windows 可验证运行，Android 调试 APK，iOS/macOS 工程入口与 macOS CI 路线。
- 安全、隐私、测试、发布和后续账户体系方案。

## 先读

1. [产品方案](docs/01-product-plan.md)
2. [开发 List](docs/02-development-list.md)
3. [分层架构](docs/03-architecture.md)
4. [联网研究](docs/04-research.md)
5. [账户与同步路线](docs/07-account-and-sync-roadmap.md)
6. [四平台发布清单](docs/08-release-checklist.md)
7. [测试与构建报告](docs/09-test-report.md)

## 隐私承诺

首版不采集姓名、手机号、证件、通讯录、位置、广告标识或设备指纹；原始账本默认只保存在设备本地。交易记录本身属于需要谨慎处理的数据，任何外部连接器都必须最小字段、显式授权、可撤销，并在取得平台真实权限后才可启用。

## 构建

工程完成集成后使用 Gradle Wrapper：

```powershell
.\gradlew.bat :apps:client:desktopTest
.\gradlew.bat :apps:client:run
.\gradlew.bat :apps:client:androidApp:assembleDebug
```

Android 需要 Android SDK；iOS/macOS 需要 macOS、Xcode 和相应签名环境。当前可运行版使用内存仓储，新增记录在应用重启后会重置；持久化加密数据库属于 `DAT-004` Beta 门禁，不能把本轮演示版误称为生产版。

## 状态

当前为可验证的首轮工程实现，不代表已取得支付宝、微信、淘宝、京东、闲鱼等平台的生产授权。所有沙箱与演示行情必须在界面中明确标注，不能被解释为实时数据。
