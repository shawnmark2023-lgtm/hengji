# 恒迹 HENGJI

看见每一笔消费的长期价值。

恒迹是一款本地优先的跨平台消费价值管理应用。当前交付范围是 Windows 和 Android；它把账单、物品、使用记录、二手残值和智能分析连接起来，让用户同时看到“花了多少”和“是否值得”。

## 当前工程里程碑

- Kotlin Multiplatform + Compose Multiplatform 共享领域与 UI。
- 持久化仓储边界覆盖流水、资产、使用、报价、偏好和导入批次；Room KMP + bundled SQLite 保留为开发/迁移源。
- AES-256-GCM 受保护账本仓储、Windows/Android/iOS 原子密文文件适配器，以及可中断恢复的三平台 Room 明文迁移门禁；Desktop、Android 与 iOS 入口均已 fail-closed 接入。
- 五步导入账单：用户文件/明确沙箱 → 字段映射 → 预览去重 → 原子确认 → 整批撤销。
- 完整 JSON 备份/恢复与防公式注入 CSV 导出。
- 内置 Qwen2.5-0.5B INT4 本机模型，无需另行下载；默认开启、可关闭，消费记录覆盖 90 天后生成第一次专属分析，以后结合新账单、历史分析和明确反馈逐步适配。
- 四步首用教程直接说明隐私、记第一笔、导入旧账单和三个月后的智能分析；顶层功能统一使用“首页、账单、我的物品、智能分析、设置”等通俗名称。
- 账单新增/编辑、物品与日均/单次使用成本、手工二手报价与即时残值重算、应用内出售目标价关注、支出占比和可解释建议；手工/示例行情始终标注为非实时，目标价不会由示例或过期报价触发。
- Windows MSI 与 Android Debug/R8 Release 构建链已建立；发行工件仍需生产签名和商店流程。
- 自动架构/secret/沙箱门禁、畸形导入矩阵和 10 万流水开发基线。

## 先读

1. [产品方案](docs/01-product-plan.md)
2. [开发 List](docs/02-development-list.md)
3. [分层架构](docs/03-architecture.md)
4. [联网研究](docs/04-research.md)
5. [账户与同步路线](docs/07-account-and-sync-roadmap.md)
6. [四平台发布清单](docs/08-release-checklist.md)
7. [测试与构建报告](docs/09-test-report.md)
8. [内置专属分析与新手引导](docs/17-built-in-personal-ai-onboarding.md)

## 隐私与安全边界

首版无登录、默认不联网，不采集姓名、手机号、证件、通讯录、位置、广告标识或设备指纹。交易和资产记录即使不含直接身份字段，仍按敏感财务数据处理。Windows 与 Android 应用入口使用平台密钥保护的认证密文仓储，并在失败时拒绝创建明文替代账本；受保护的初始化状态区分全新创建、旧库迁移与就绪，迁移中的状态不能静默变成空账本。

内置模型和运行时随安装包交付，运行期没有模型下载器或远程推理入口。模型只接收本机生成的聚合指标、已验证候选和最多三条历史分析摘要，不接收逐笔账单、商户、备注、账户、导入原文或 OCR 原文。模型生成自然语言，金额、证据、资格和排序仍由应用代码计算并校验；关闭智能分析后不会加载模型。

沙箱连接器、示例报价和本地文件导入不会伪装成平台实时同步。支付宝、微信、淘宝、京东、闲鱼等真实连接器只有在取得官方 scope、合同与审核后才能启用；禁止抓密码、复用 Cookie、调用私有 API 或违规爬取。

## 构建与验证

```powershell
.\gradlew.bat desktopTest
.\gradlew.bat :apps:client:androidApp:lintDebug :apps:client:androidApp:assembleDebug :apps:client:androidApp:assembleRelease
python scripts/quality/run_quality.py --output-dir quality/evidence
```

Windows 发行构建还要执行 Release 混淆后的真实启动冒烟；当前便携 ZIP 已从解压后的实际二进制完成受保护账本首次启动/重启验证。Room 反射类与 SQLite JNI 符号的保留规则位于 `apps/client/proguard-rules.pro`。

## 当前不能宣称的内容

- iOS/macOS 原生编译、真机、签名、公证和商店发布仍需 macOS + Xcode。
- 当前 Windows MSI/EXE 与 Android Release APK 均未生产签名；没有 SmartScreen、Play App Signing、AAB 或商店内部测试轨证据。
- iOS 系统文件选择、JSON/CSV 导出与 JSON 恢复适配器已实现并通过 Kotlin/Native arm64/simulator 交叉编译；Swift host、Xcode 链接、模拟器/真机交互与签名仍需 macOS 验证。
- Android 旧 Room 库迁移与加密入口已实现并通过主机测试、API 36 历史设备测试、lint 和 APK 构建，但代表性实体机上的升级/卸载/恢复、密钥丢失和内置模型性能仍需设备矩阵。
- 演示二手报价不是实时市场价；没有授权的数据源不会在生产模式降级为沙箱。
