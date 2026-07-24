# 四平台发布清单

勾选仅表示已有本轮证据；未勾选项仍是 Beta/生产门禁。

## 当前已验证基础

- [x] Room KMP/bundled SQLite 跨重启持久化、schema v1、备份/恢复、批次回滚。
- [x] 自动架构/secret/沙箱门禁、87 个 Kotlin 测试、8 类畸形导入、10 万流水开发基线。
- [x] Android Debug APK 构建；Windows Release 免安装包启动；MSI 生成、行政解包并启动。
- [x] iOS 客户端与数据层 source-set 元数据及 arm64/simulator Kotlin klib 交叉编译；此项不代表 Xcode 链接、真机或签名通过。
- [x] 真实 UI 走通新增流水→关闭→重启保留，以及五步导入→原子写入→整批撤销。
- [ ] 应用层数据库加密、平台密钥实现与加密迁移/恢复。
- [ ] 签名、商店/公证、真实平台授权、账号验证与端到端加密同步。

## 通用门禁

- [ ] 版本固定、依赖锁、SBOM、许可证审查、漏洞扫描全部通过。
- [ ] 核心领域、导入契约、关键 UI 自动化、加密恢复演练全部通过。
- [ ] 10 万流水在代表性设备上的首次载入、筛选、导入峰值和低内存基线通过。
- [ ] 隐私政策、字段清单、权限用途、数据删除和联系渠道完成。
- [ ] 正式构建无演示行情，或以不可误解的“示例”模式隔离。
- [ ] 崩溃监控采用最小数据方案并可关闭；无原始账单和 token。
- [ ] 分阶段发布、回滚版本、数据库迁移与支持预案完成。

## iOS

- [ ] macOS + 当前 Xcode 真机构建、归档、签名和 TestFlight 验证。
- [ ] iOS 系统文件选择、JSON/CSV 落盘导出与 JSON 恢复适配器已实现并交叉编译；仍需模拟器/真机成功、超限、取消、清理与跨重启验证。
- [ ] App Privacy、Privacy Manifest、entitlement 和用途说明匹配实际代码。
- [ ] FinanceKit 仅在 entitlement/地区/eligible account 满足时出现。
- [ ] Dynamic Type、VoiceOver、Reduce Motion、深浅色、后台模糊通过。
- [ ] Sign in with Apple/Passkey、Keychain、沙箱与 Universal Link 验证。

## Android

- [x] Windows 本地 Debug APK 构建通过，`apksigner` 确认为 Android Debug 证书 v2 签名。
- [ ] AAB、发布签名、Play App Signing 和内部测试轨验证。
- [ ] Data Safety 与实际 SDK/网络字段一致。
- [ ] 若启用金融短信导入，先取得 SMS 权限声明批准；无批准版本不声明权限。
- [ ] TalkBack、大字体、预测返回、不同窗口尺寸和低端设备通过。
- [ ] Keystore、App Link、备份规则和截屏保护策略验证。

## macOS

- [ ] universal/目标架构构建、Developer ID、Hardened Runtime、公证和 Gatekeeper。
- [ ] App Sandbox entitlement 最小化，用户选择文件路径可用。
- [ ] 菜单、快捷键、键盘焦点、窗口恢复、200% 缩放和多显示器验证。
- [ ] Keychain、深浅色、VoiceOver、后台隐私遮罩验证。

## Windows

- [x] Release 免安装包真实启动；MSI 生成、Windows Installer 行政解包并从解包结果启动。
- [ ] 代码签名、SmartScreen、真实安装/升级/卸载和数据保留策略验证。
- [ ] 日常升级不要求管理员权限的分发策略确定。
- [ ] 键盘、屏幕阅读器、高对比度、125%–300% 缩放和触控设备验证。
- [ ] DPAPI/Credential Locker、协议回调和自动更新验证。
