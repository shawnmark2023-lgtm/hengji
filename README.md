# 衡记 HENGJI

看见每一笔消费的长期价值。

衡记是一款本地优先的跨平台消费价值管理应用，目标平台为 iOS、Android、Windows 和 macOS。它把流水、产品资产、使用记录、二手残值和可解释洞察连接起来，让用户同时看到“花了多少”和“是否值得”。

## 当前工程里程碑

- Kotlin Multiplatform + Compose Multiplatform 共享领域与 UI。
- 持久化仓储边界覆盖流水、资产、使用、报价、偏好和导入批次；Room KMP + bundled SQLite 保留为开发/迁移源。
- AES-256-GCM 受保护账本仓储、Windows/Android/iOS 原子密文文件适配器，以及可中断恢复的 Desktop Room 明文迁移门禁；Windows/macOS Desktop 入口已 fail-closed 接入。
- 五步导入中心：用户文件/明确沙箱 → 字段映射 → 预览去重 → 原子确认 → 整批撤销。
- 完整 JSON 备份/恢复与防公式注入 CSV 导出。
- 流水新增/编辑、资产与日均/单次使用成本、二手价格区间、支出占比和可解释建议。
- Windows 免安装包与 MSI、Android Debug APK；iOS 系统文件导入/导出适配器、源码入口与 Apple CI 路线。
- 自动架构/secret/沙箱门禁、畸形导入矩阵和 10 万流水开发基线。

## 先读

1. [产品方案](docs/01-product-plan.md)
2. [开发 List](docs/02-development-list.md)
3. [分层架构](docs/03-architecture.md)
4. [联网研究](docs/04-research.md)
5. [账户与同步路线](docs/07-account-and-sync-roadmap.md)
6. [四平台发布清单](docs/08-release-checklist.md)
7. [测试与构建报告](docs/09-test-report.md)

## 隐私与安全边界

首版无登录、默认不联网，不采集姓名、手机号、证件、通讯录、位置、广告标识或设备指纹。交易和资产记录即使不含直接身份字段，仍按敏感财务数据处理。Desktop 应用入口已使用平台密钥保护的认证密文仓储，并在失败时拒绝创建明文替代账本；密文账本缺失但平台密钥仍存在时也会阻止空账本重建。Android/iOS 入口仍是显式 Room 明文开发存储，切换其生产默认值前必须完成旧库迁移、真机运行和代表性数据量性能验收。

沙箱连接器、示例报价和本地文件导入不会伪装成平台实时同步。支付宝、微信、淘宝、京东、闲鱼等真实连接器只有在取得官方 scope、合同与审核后才能启用；禁止抓密码、复用 Cookie、调用私有 API 或违规爬取。

## 构建与验证

```powershell
.\gradlew.bat desktopTest
.\gradlew.bat :apps:client:androidApp:assembleDebug
python scripts/quality/run_quality.py --output-dir quality/evidence
```

Windows 发行构建还要执行 Release 混淆后的真实启动冒烟；Room 反射类与 SQLite JNI 符号的保留规则位于 `apps/client/proguard-rules.pro`。

## 当前不能宣称的内容

- iOS/macOS 原生编译、真机、签名、公证和商店发布仍需 macOS + Xcode。
- Windows MSI/免安装包未签名；Android APK 由 Android Debug 证书以 v2 方案签名，但未做生产发布签名，也未做设备安装/启动验证。
- iOS 系统文件选择、JSON/CSV 导出与 JSON 恢复适配器已实现并通过 Kotlin/Native arm64/simulator 交叉编译；Swift host、Xcode 链接、模拟器/真机交互与签名仍需 macOS 验证。
- Android/iOS 平台密钥真机验收、旧 Room 库迁移与应用入口切换，以及加密同步、灾难恢复和真实平台授权尚未完成；Desktop 使用受验证的复制迁移门禁，而移动端密文仓储不会把现有 bundled SQLite 文件原地变成密文。
- 演示二手报价不是实时市场价；没有授权的数据源不会在生产模式降级为沙箱。
