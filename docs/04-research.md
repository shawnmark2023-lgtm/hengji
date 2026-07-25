# 衡记 HENGJI：联网研究摘要（2026-07-15）

## 用户痛点

公开用户讨论反复出现以下问题：手工维护难坚持；自动导入会错分、断连后反而需要审计；用户不信任账单上传；订阅价格令人反感；CSV/PDF 导入仍是必要兜底；用户需要“还能花多少”和可执行提醒，而不只是历史图表。

- Reddit 用户反馈集中在隐私、错误分类和维护成本：[自动分类错误导致失去信任](https://www.reddit.com/r/personalfinanceindia/comments/1u1z99v/whats_the_biggest_thing_current_expense_tracking/)、[隐私与低摩擦讨论](https://www.reddit.com/r/personalfinanceindia/comments/1tdw5lt/why_is_every_personal_finance_tracking_apps/)、[PDF 本地导入](https://www.reddit.com/r/iosapps/comments/1sw8fcf/i_have_made_an_expense_tracker_app_where_you_can/)。
- 中国 App Store 竞品已把多币种、小组件、Face ID 和后台模糊作为常见能力：[Cookie 记账](https://apps.apple.com/cn/app/cookie-%E8%AE%B0%E8%B4%A6/id1559943673)。
- 自动化不能替代纠错与控制权；本方案把“预览、确认、撤销、纠正学习”设为导入的一等流程。

## 平台与 API 现实

- 微信支付公开查单和交易账单接口要求商户号，服务于商户自身订单/对账，并不是消费者全量账本 API：[微信支付查询订单](https://pay.wechatpay.cn/doc/v3/merchant/4013070354)、[申请交易账单](https://pay.wechatpay.cn/doc/v3/merchant/4012791907)。
- 淘宝公开示例 `taobao.trades.sold.get` 查询卖家已卖出的交易，授权 token 也不能自动等价为买家全量消费读取：[淘宝开放平台](https://developer.alibaba.com/docs/api.htm?apiId=46&source=search)。
- 京东公开订单 API 文档主要面向商家授权与生产/履约场景：[京东 API 调用指南](https://help.jd.com/oapihelp/question-460.html)。
- Apple FinanceKit 可以在用户同意下向财务管理 App 分享 eligible Apple Wallet 账户余额和交易，但受地区、账户和 entitlement 限制：[Apple FinanceKit](https://developer.apple.com/financekit/)。
- Android 金融预算类应用可能申请 SMS 例外，但需 Google Play 声明和审批，且不得外传非金融短信：[Google Play SMS/Call Log Policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)。
- Apple 沙箱不允许任意读取其他 App 容器；用户选择文件和官方扩展/授权才是稳定路径：[Apple Shared Data](https://developer.apple.com/documentation/technologyoverviews/shared-data)。
- iOS 用户文件导入/导出应走系统 `UIDocumentPickerViewController`；安全作用域 URL 必须成对开始/结束访问，Kotlin/Native 通过 Objective-C/Swift 互操作调用这些 Foundation/UIKit API：[Document picker](https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller)、[Security-scoped URL](https://developer.apple.com/documentation/foundation/nsurl)、[Kotlin/Native interoperability](https://kotlinlang.org/docs/native-objc-interop.html)。

结论：首版必须把“用户主动导入 + 官方连接器适配层”做扎实；任何“一键同步”只能在取得对应平台真实 scope 后逐个平台上线。

## 隐私与合规

《个人信息保护法》将金融账户列为敏感个人信息，并要求自动化决策透明、公平；消费/交易记录也不能被当作无个人属性的普通业务数据：[个人信息保护法](https://www.samr.gov.cn/wljys/gzzd/art/2023/art_3ef1e889c1e644d4b65b5f5c7f432386.html)。合规审计规则强调敏感信息单独同意和防止基于交易习惯的不合理差别待遇：[个人信息保护合规审计管理办法](https://www.cac.gov.cn/2025-02/14/c_1741233507681519.htm)。

因此产品采用：身份数据零收集、交易数据本地处理、最小字段白名单、单独授权、可撤权、可导出/删除、自动建议可解释。

## 二手市场

闲鱼开放 API 的公开目录更偏向电商 SaaS、商家上架和行业合作，没有证据表明普通第三方 App 可无条件获得全站消费者级实时搜索授权：[闲鱼电商 SaaS API 目录](https://developer.alibaba.com/docs/api.htm?apiId=74471&source=search)。

结论：先交付报价协议、手工/演示提供器、来源标注、中位数和置信度；生产比价只接入已签约官方/正规聚合 API，绝不把网页爬虫当正式方案。

## 技术选型

Compose Multiplatform 官方当前将 Android、iOS、Desktop UI 标为 Stable，并明确可用于生产：[平台稳定级别](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)、[KMP FAQ](https://kotlinlang.org/docs/multiplatform/faq.html)。它支持平台专属入口和原生互操作，适合四端统一领域逻辑又保留平台体验。

Tauri 2 也覆盖 Windows、macOS、Android、iOS，但依赖系统 WebView，移动插件需要 Swift/Kotlin 原生实现；本项目对移动原生能力和长期可控性权重更高，因此把 Tauri 保留为备选而不是首选：[Tauri prerequisites](https://v2.tauri.app/start/prerequisites/)、[Tauri mobile plugin](https://v2.tauri.app/develop/plugins/develop-mobile/)。

Gradle 9 的 dependency locking 会把解析版本写入应提交到版本库的 lockfile，`STRICT` 模式会让已启用锁定但缺少锁状态的配置失败；dependency verification 则用 `gradle/verification-metadata.xml` 校验工件内容，两者互补而非替代：[Gradle dependency locking](https://docs.gradle.org/9.3.1/userguide/dependency_locking.html)、[Gradle dependency verification](https://docs.gradle.org/9.3.1/userguide/dependency_verification.html)。本项目采用 SHA-256 完整性校验；官方同时提醒，自动生成元数据只信任生成当时取到的工件，必须审查来源，且校验和本身不等同于发布者身份认证。

静态加密采用“平台密钥保护 + AES-GCM 数据密钥”的分层，而不是自写密码算法。Android 官方建议需要更强密钥安全时使用 Android Keystore，并列出 `AES/GCM/NoPadding` 与 256 位密钥；Apple 推荐 CryptoKit，并由 Keychain 保存小型密钥材料；Windows DPAPI 默认把受保护数据绑定到同一用户与机器并提供完整性校验：[Android cryptography](https://developer.android.com/privacy-and-security/cryptography)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[Apple CryptoKit](https://developer.apple.com/documentation/cryptokit/)、[Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services/)、[Windows CryptProtectData](https://learn.microsoft.com/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)。公共加密原语使用 [cryptography-kotlin 0.6.0](https://whyoleg.github.io/cryptography-kotlin/getting-started/) 的 optimal provider，在 JVM/Android 委托 JCA，在 Apple 目标优先委托 CryptoKit；其 AES-GCM 默认格式为随机 IV、密文与认证标签，并支持 AAD。库已锁版本并纳入 SHA-256 dependency verification，但仍须 SBOM、许可证与漏洞审查。

Windows 桌面通过 [JNA 5.18.1](https://java-native-access.github.io/jna/5.18.1/javadoc/overview-summary.html) 调用系统 DPAPI，并设置 `CRYPTPROTECT_UI_FORBIDDEN`，避免后台密钥操作弹出不可控系统提示。JNA/JNI 依赖反射和固定符号名，因此除普通 ProGuard 构建外，还必须直接从混淆后 JAR 执行 DPAPI 往返；该冒烟已捕获并修复一次 JNI 裁剪问题。

Android 数据密钥由 `AndroidKeyStore` 中不可导出的 AES-256-GCM 包装密钥保护，保护物放在系统明确排除自动备份的 [`noBackupFilesDir`](https://developer.android.com/identity/data/autobackup)，避免恢复到缺少原设备 Keystore 密钥的新设备。保护物以版本、别名 AAD 做认证绑定并采用“仅在目标不存在时发布”，既有保护物损坏、被串换或包装密钥丢失时不会静默生成新数据密钥。主机测试只验证可注入的保护物生命周期；真实 Keystore、卸载/恢复与锁屏行为仍必须由 Android 设备测试提供证据。

iOS/macOS 把 256 位数据密钥作为 Generic Password 项交给 Keychain，而不是自行落盘；采用 [`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)，只在设备解锁时可用且不迁移到新设备，并显式关闭同步。为避免跨设备恢复出“有密文、无设备密钥”的孤立账本，iOS 每次原子写入后设置 [`NSFileProtectionComplete`](https://developer.apple.com/documentation/foundation/fileprotectiontype/complete) 并通过 [`NSURLIsExcludedFromBackupKey`](https://developer.apple.com/documentation/foundation/urlresourcekey/isexcludedfrombackupkey) 排除系统备份；灾难恢复依赖后续用户主动加密备份，而不是系统迁移。macOS 同时设置 [`kSecUseDataProtectionKeychain`](https://developer.apple.com/documentation/security/ksecusedataprotectionkeychain)，使 accessibility 属性应用于 data-protection Keychain。并发首次创建以 Keychain 的 `errSecDuplicateItem` 决胜，失败后只重读、不覆盖。Windows 只能提供 Kotlin/Native 交叉编译、JNA ABI 与混淆符号证据；真实权限、签名身份、锁屏和卸载行为须在 Apple runner/设备验证。

## 设计质量基线

Apple 最新设计原则强调目的、用户自主、责任、熟悉性、灵活性、简洁、工艺和愉悦，并要求每个平台都得到同等关注：[Apple Design Principles](https://developer.apple.com/design/human-interface-guidelines/design-principles)。Apple 也要求尽可能在设备端处理数据、按需请求最少权限，并从一开始考虑可访问性：[Privacy](https://developer.apple.com/design/human-interface-guidelines/privacy/)、[Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility/)。

本项目把这些原则转换为可验收门禁，而不是用“苹果质量”作为不可测量的形容词。

## Skill 检索结论

- OpenAI 当前官方 Skill 目录已安装：`security-best-practices`、`security-threat-model`、`playwright`；用于后续轮次的安全和验收工作。
- JetBrains 官方 [Kotlin Agent Skills](https://github.com/Kotlin/kotlin-agent-skills) 当前主要覆盖 AGP/SPM/集合迁移和 JPA，没有适合从零构建 KMP 财务应用的 Skill。
- 项目结束时将按官方 `skill-creator` 规范产出 `build-local-first-finance-app`，包含架构检查、隐私连接器清单、成本算法、测试门禁和发布工作流。
