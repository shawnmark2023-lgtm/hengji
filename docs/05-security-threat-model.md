# 衡记 HENGJI：安全与隐私威胁模型 v0.1

更新日期：2026-07-25。范围：首版无登录、本地优先客户端，用户主动 CSV/JSON 导入，沙箱平台连接器，以及可选的连接器网关/二手估价服务边界。

## 1. 安全目标与非目标

安全目标按优先级排序：

1. 原始流水、物品、使用记录不在未授权情况下离开设备。
2. 导入必须先预览、再确认；整批写入原子化并可按批次撤销。
3. 金额始终使用最小货币单位整数，外部输入不能改变财务语义或造成溢出。
4. 沙箱、手工、历史、实时来源不可混淆；演示行情必须同时携带 `provenance=demo_non_live`、`isLive=false` 和“非实时”披露。
5. 正式 OAuth 上线前默认拒绝生产访问，客户端和仓库中不得出现平台私钥或生产 token。
6. 错误与日志提供可诊断性，但不包含账单内容、token、授权码、完整 URL query 或设备标识。

首版不承诺抵御已完全控制设备、读取进程内存的本地管理员；正式上线前通过平台安全存储、数据库加密、设备吊销和端到端加密逐步降低该风险。

## 2. 数据分类

| 类别 | 例子 | 默认位置 | 网络策略 | 日志策略 |
|---|---|---|---|---|
| S3 高敏财务 | 原始流水、金额、时间、商户、退款 | 设备本地 | 首版禁止自动上传 | 禁止 |
| S3 授权凭据 | OAuth code、access/refresh token、PKCE verifier | 正式版平台 vault/后端加密 vault | 仅 TLS、仅白名单端点 | 禁止 |
| S2 消费画像 | 分类汇总、预算、物品使用率 | 设备本地 | 用户显式同意后仅白名单聚合字段 | 仅无业务值计数 |
| S1 公开/演示 | 静态沙箱流水、演示二手报价 | 安装包或测试夹具 | 可用 | 必须标“非实时” |
| S0 运维 | 版本、健康状态、错误码、请求 ID | 本地/服务监控 | 可用 | 可记录，短期保留 |

“不获取个人信息”在工程中落实为不采集姓名、手机号、证件、通讯录、位置、广告标识和设备指纹；交易数据本身仍按高敏数据保护，绝不视作匿名普通数据。

## 3. 数据流与信任边界

```text
用户选择的文件（不可信）
  -> 有界解析器 -> 预览/对账区 -> 用户确认 -> 本地账本
                       |                         |
                       +-- 去重指纹              +-- 批次撤销

客户端 -> [TLS/域名白名单] -> Connector Gateway -> [正式版受审连接器] -> 官方平台
  |                              |
  |                              +-- 短期 state/PKCE、加密 token vault（后续）
  +-- 首版默认不调用此路径

客户端 -> [TLS/域名白名单] -> Price Intelligence <- 手工/演示/已签约 API 报价
```

信任边界：文件系统到解析器、外部平台到适配器、客户端到服务、服务到 token vault、构建系统到签名环境。任何跨边界对象都必须验证，不能共享数据库表或隐式序列化对象。

## 4. 威胁与缓解

| ID | 威胁/滥用路径 | 影响 | 首版缓解 | 上线门禁 |
|---|---|---|---|---|
| T01 | 恶意超大 CSV/JSON、超长单元格、行列炸弹 | 内存/CPU 耗尽 | 普通导入 5 MiB、完整恢复 25 MiB、1 万行、64 列、4096 字符硬限制；Android/iOS 在分配完整内容前做有界读取 | 流式解析、性能基准、模糊测试 |
| T02 | CSV 公式注入在后续导出时执行 | 本地命令/数据外带 | 导入文本字段拒绝 `= + - @` 等公式前缀；导出层还需二次转义 | CSV 导出契约测试覆盖所有危险前缀 |
| T03 | 金额浮点、指数、过多小数或整数溢出 | 账本失真 | 仅十进制定点字符串/整数；币种精度检查；64 位溢出检测 | 属性测试覆盖上下界、不同币种 |
| T04 | 重复导入、并发提交、部分写入 | 重复支出/审计困难 | 版本化稳定指纹；批内与账本去重；`ImportLedger` 规定提交/撤销原子性 | SQLite 事务与故障注入测试 |
| T05 | 导入撤销误删用户后来修改的数据 | 数据丢失 | 批次记录插入指纹；首版只撤销该批插入项 | 上线前改为不可变导入成员 ID + 冲突提示 |
| T06 | 沙箱/演示报价冒充实时行情 | 错误出售决策、信任损失 | 类型不变量强制 `isLive=false`，接口和 UI 均披露“非实时” | UI 截图测试、可访问名称检查 |
| T07 | 未授权抓取、密码代填或私有 API 逆向 | 账号封禁、合规风险 | L3 能力永久禁止；仓库不含爬虫/密码输入；生产连接器白名单 | 法务/平台 scope 审核与供应商登记 |
| T08 | OAuth CSRF、授权码注入、PKCE 降级 | 账户绑定错位/token 泄露 | 授权码模式；PKCE S256；随机一次性 state；5 分钟过期；严格绑定 connector 与 redirect URI | 每个平台 issuer 校验、metadata/PKCE 能力验证 |
| T09 | 开放重定向、redirect URI 模糊匹配 | 授权码/token 泄露 | redirect URI 完整字符串白名单；不接受请求提供的新目标 | 渗透测试和移动 deep-link 归属验证 |
| T10 | token/授权码出现在 URL、日志、仓库 | 长期凭据泄露 | token 不进 query/日志；首版 callback 丢弃沙箱 code，不存 token；secret scan | Keychain/Keystore/Credential Locker 或后端 vault；轮换演练 |
| T11 | 恶意/被入侵报价提供方返回错币种、负数、旧数据 | 估值操纵 | 整数、币种、时区、置信度、未来时间校验；离群过滤与新鲜度衰减 | 提供方签名、配额、熔断、来源审计 |
| T12 | 单一低质量报价制造虚假精确 | 误导决策 | 样本少于 3 或低置信度时不返回单点中位数；保留区间与披露 | 用真实已授权样本校准阈值 |
| T13 | 错误响应回显原始交易或内部堆栈 | 隐私/实现泄露 | 稳定错误码 + request ID；500 使用通用文案 | 集中脱敏日志与响应快照测试 |
| T14 | 依赖、CI 或签名供应链被篡改 | 恶意发行包 | 锁文件、最小依赖、CI 测试、禁止提交密钥 | SBOM、依赖审查、固定 action SHA、隔离签名 runner |
| T15 | 日后账户恢复绕过、设备撤销失效 | 云端账本泄露 | 首版无账户/云同步，攻击面不存在 | Passkey、恢复码、设备清单、会话/密钥吊销专项评审 |
| T16 | 本地数据库、备份或临时文件被离线复制/篡改 | 高敏财务明文泄露、静默数据操纵 | 受保护账本 envelope 使用 AES-256-GCM、随机 96-bit nonce、版本/算法/密钥别名 AAD、大小上限与 fail-closed；Windows 用当前用户 DPAPI；Android 用不可导出 Keystore AES 密钥和 no-backup 保护物；iOS/macOS 用不迁移、不同步、仅解锁可用的 Keychain 项；当前 Room 仍仅限开发明文策略 | Room 明文→密文原子迁移、错钥/轮换/崩溃恢复与 Android/Apple 设备验收 |

## 5. OAuth 上线约束

当前 `connector-gateway` 只模拟沙箱授权，不向任何消费平台发请求。正式连接器必须逐个平台满足：

- 已取得平台书面/控制台授权、准确 scope、隐私清单和可撤销流程；不得把商户订单 API 描述成消费者全量账本 API。
- 使用 authorization code + PKCE S256；state/nonce 交易级随机且单次使用。
- redirect URI 精确匹配；除原生 loopback 特例外只允许 TLS；禁止开放重定向。
- token 不放 URI，访问 token 与刷新 token 分离保存；客户端只用平台安全存储，服务端使用加密 vault 和独立密钥管理。
- 仅允许注册的授权、token、资源、撤销域名；设置连接/读取超时、有限重试、速率限制、熔断和最大响应体。
- 日志只记 provider、结果码、耗时、request ID；禁止 scope 外字段、原始响应和任何凭据。

这些要求依据 [RFC 9700 OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html) 和 [RFC 7636 PKCE](https://www.rfc-editor.org/rfc/rfc7636.html)。

## 6. 不可信文件基线

首版只接受用户主动选择的 UTF-8 CSV/JSON 文本，不根据文件名信任内容。平台选择器限制候选类型，共享策略再校验扩展名、严格 UTF-8、非空和 5/25 MiB 上限；解析器继续执行字段白名单、对象/数组形状、行/列/单元格、金额和日期验证。iOS 在安全作用域与 `NSFileCoordinator` 内最多读取“上限 + 1 字节”，导出仅使用应用沙箱内受控前缀临时目录并在取消/完成后清理。正式版仍需 MIME 内容探测、完整流式解析、模糊测试和恶意样本回归库。原则参考 [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)、[OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html) 和 [OWASP CSV Injection](https://owasp.org/www-community/attacks/CSV_Injection)。

## 7. 日志与遥测

首版不接入广告、行为追踪或第三方崩溃采集 SDK。允许事件：应用版本、匿名构建渠道、功能成功/失败计数、稳定错误码和耗时区间；默认本地保存。禁止事件：金额、商户、备注、产品名、文件名、token、授权码、完整 URL、IP 与设备指纹。上线前任何远程遥测都必须单独开关、数据字典、保留期和删除路径。

## 8. 验收与剩余风险

P0 门禁：导入边界/公式/去重/撤销测试，沙箱非实时不变量测试，网关 state 一次性与过期测试，服务请求体限制，生产模式 fail-closed，仓库 secret 扫描。iOS/macOS 签名、Keychain、FinanceKit entitlement、Android SMS 例外和真实 OAuth 均不能在 Windows 首轮构建中宣称完成。

剩余高风险项：AES-256-GCM 受保护账本原语及 Windows DPAPI、Android Keystore、iOS/macOS Keychain 数据密钥提供器已经实现，但 Android/Apple 平台真实密钥行为、本地 Room 密文映射与迁移尚未完成；批次撤销与用户后续编辑的冲突策略待持久化层实现；沙箱网关为单进程内存 state；服务未配置真实 vault/限流/集中审计；真实平台 scope 尚未取得。这些都必须在生产开关开启前关闭或书面接受。
