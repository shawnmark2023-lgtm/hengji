# 四平台发布清单

勾选仅表示已有本轮证据；未勾选项仍是 Beta/生产门禁。

2026-07-28 P0/P1 收口仅验收 Windows + Android；iOS/macOS 按产品决定延期，下面对应平台清单原样保留但不阻塞本轮结论。

## 当前已验证基础

- [x] Room KMP/bundled SQLite 跨重启持久化、schema v3、备份/恢复、批次回滚。
- [x] 自动架构/secret/沙箱门禁、188 个 Desktop Kotlin 测试、63 个 Android host 测试、8 类畸形导入、10 万流水开发基线。
- [x] Android lint、Debug APK 与未签名 R8 Release 构建；API 36 上平台入口、AndroidKeyStore 受保护账本和 Compose 自动无障碍分析 3/3。
- [x] Windows/Android 关键 UI 自动化覆盖导入整批回滚、导出、恢复、清除、删除确认与 8 秒撤销、360dp/200%、深色和 Reduce Motion；Windows 另覆盖 Tab/Enter。
- [x] Windows 73 个 Release JAR 的规范化内容及 Android Debug APK 的两次隔离构建一致。
- [x] iOS 客户端与数据层 source-set 元数据及 arm64/simulator Kotlin klib 交叉编译；此项不代表 Xcode 链接、真机或签名通过。
- [x] 真实 UI 走通新增流水→关闭→重启保留、三条手工二手报价→目标价等待/达到→洞察证据→重启保留，以及五步导入→原子写入→整批撤销。
- [ ] 应用层 AES-256-GCM、Windows DPAPI、Android Keystore、明文迁移、受保护初始化 journal 与崩溃安全轮换已实现并通过 Windows/API 36；仍待代表性 Android 实体机的锁屏、卸载、备份/迁移、系统回滚与密钥丢失演练，Apple 延期。
- [ ] 签名、商店/公证、真实平台授权、账号验证与端到端加密同步。

## 通用门禁

- [x] Gradle Wrapper/版本目录固定；主构建与 quality harness 的严格依赖锁、SHA-256 verification metadata 及 CI 全配置解析门禁已配置并在 Windows 本地通过。
- [x] 主要导航、表单错误、开关、导入映射/预览和异步状态已有可读语义；大字体重排与 Reduce Motion 代码路径已接入。
- [x] Windows/Android 发行运行依赖与 Node/Python 构建工具已固定并生成 CycloneDX 1.6 SBOM；276 个包经 OSV Scanner 2.3.8 审计为 0 已知漏洞、0 许可证违规。11 个 Google ML Kit 非标准条款组件按精确版本绑定官方条款和 2027-07-27 复审日期；64 个 GitHub Action 引用固定到完整提交 SHA。
- [x] Windows/Android 核心领域、导入契约、关键 UI 自动化、受保护账本创建/重开与密钥轮换单测通过；锁屏、卸载、系统备份/迁移、系统回滚与密钥丢失是实体设备安全演练。
- [ ] 10 万流水在代表性设备上的首次载入、筛选、导入峰值和低内存基线通过。
- [ ] 隐私政策、字段清单、权限用途、数据删除和联系渠道完成。
- [x] Android OCR 使用的 ML Kit 官方条款与数据披露已审查；识别内容在设备内处理，发行清单显式移除传递依赖引入的 `INTERNET`，自动断言阻止诊断/使用指标外发。未来授权行情联网必须单独重新审查并更新披露。
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
- [x] API 36 模拟器安装/启动、受保护账本创建/重开及共享 UI 52/52 通过。
- [ ] AAB、发布签名、Play App Signing 和内部测试轨验证。
- [ ] Data Safety 与实际 SDK/网络字段一致。
- [ ] 若启用金融短信导入，先取得 SMS 权限声明批准；无批准版本不声明权限。
- [ ] TalkBack、大字体、预测返回、不同窗口尺寸和低端设备通过。
- [ ] Keystore 创建/重开已在 API 36 通过；锁屏、卸载、系统备份/设备迁移、App Link 和截屏保护策略仍需真机专项验证。

## macOS

- [ ] universal/目标架构构建、Developer ID、Hardened Runtime、公证和 Gatekeeper。
- [ ] App Sandbox entitlement 最小化，用户选择文件路径可用。
- [ ] 菜单、快捷键、键盘焦点、窗口恢复、200% 缩放和多显示器验证。
- [ ] Keychain、深浅色、VoiceOver、后台隐私遮罩验证。

## Windows

- [x] Release 免安装包真实启动；最新源码的 `runRelease` 首次启动/重启不重写密文。
- [x] Desktop client 53/53、全体 Desktop Kotlin 188/188，Tab/Enter 导航自动化通过。
- [x] MSI 生成、Windows Installer 行政解包，并从解包结果连续两次启动；DPAPI 密钥材料与加密账本创建成功，重开未改写密文且未生成明文数据库。
- [x] 每用户真实安装、0.0.9→0.1.0 同代码版本升级、卸载清理和隔离数据保留通过；程序目录与默认账本目录分离。
- [x] 当前源码未签名 MSI 的已安装位置真实 EXE 在首次安装和升级后均启动通过。
- [ ] 生产代码签名、SmartScreen 信誉和受组织 Application Control 管控位置的签名候选包验证。
- [x] 日常升级采用 `%LOCALAPPDATA%\Programs\Hengji` 每用户安装，不要求管理员权限；默认加密账本保留在独立 `%LOCALAPPDATA%\Hengji`。
- [ ] 键盘、屏幕阅读器、高对比度、125%–300% 缩放和触控设备验证。
- [ ] DPAPI/Credential Locker、协议回调和自动更新验证。
