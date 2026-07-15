# 四平台发布清单

## 通用门禁

- [ ] 版本固定、依赖锁、SBOM、许可证审查、漏洞扫描通过。
- [ ] 核心领域测试、导入契约测试、关键 UI 测试和恢复演练通过。
- [ ] 10 万流水性能基线通过；低内存、离线、时区/币种和大字体验证。
- [ ] 隐私政策、字段清单、权限用途、数据删除和联系渠道完成。
- [ ] 正式构建无演示行情；或以不可误解的“示例”模式隔离。
- [ ] 不包含测试 secret、调试后门、明文 token、广告或未披露 SDK。
- [ ] 崩溃监控采用最小数据方案，并提供关闭选项。
- [ ] 分阶段发布、回滚版本、数据库向前/向后迁移与支持预案完成。

## iOS

- [ ] macOS + 当前 Xcode 真机构建、归档、签名和 TestFlight 验证。
- [ ] App Privacy、Privacy Manifest、entitlement 和用途说明匹配实际代码。
- [ ] FinanceKit 仅在 entitlement/地区/eligible account 满足时出现。
- [ ] Dynamic Type、VoiceOver、Reduce Motion、深浅色、后台模糊通过。
- [ ] Sign in with Apple/Passkey、Keychain、沙箱与 Universal Link 验证。

## Android

- [ ] targetSdk/compileSdk、AAB、签名、Play App Signing 和内部测试轨验证。
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

- [ ] MSIX/安装器签名、SmartScreen、安装/升级/卸载和数据保留策略验证。
- [ ] Web/原生运行时依赖策略清楚，不需要管理员权限完成日常升级。
- [ ] 键盘、屏幕阅读器、高对比度、125%-300% 缩放和触控设备验证。
- [ ] Credential Locker/DPAPI、文件选择器和协议回调验证。
