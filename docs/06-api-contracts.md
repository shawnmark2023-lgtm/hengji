# 衡记 HENGJI：连接器与服务 API 契约 v0.1

本文是跨 Kotlin、TypeScript、Python 边界的稳定契约。所有金额都是 `Int64` 最小货币单位；所有时间都是包含时区的 ISO-8601 字符串；币种是三位大写代码；未知枚举值按“不支持”处理，不能静默降级。

## 1. 版本与兼容性

- HTTP 基础路径 `/v1`；不兼容改动发布 `/v2`。
- JSON 字段使用 lowerCamelCase。客户端必须忽略未知响应字段，服务必须拒绝未知的安全关键枚举。
- `Content-Type: application/json`；服务请求体上限 64 KiB；导入文件独立限制为 5 MiB、1 万条、64 列、单元格 4096 字符。
- `Cache-Control: no-store` 用于授权与估价响应。
- 错误结构为 `{"error":{"code":"STABLE_CODE","message":"用户可理解文案","requestId":"可选"}}`；不得回显原始 payload。

## 2. 本地连接器端口

`ConnectorDescriptor` 必须声明：

- `id`、`displayName`；
- `capabilities`: transactions/orders/categories/refunds/incremental_cursor/revocation；
- `authorizationMode`: user_selected_file/user_initiated_share/oauth_pkce/system_entitlement/sandbox_only；
- `availability`: sandbox/review_required/production/unavailable；
- `privacyClass`、逐字段用途与保留期；
- 用户可见 `disclosure`。

`PlatformConnector.fetch({cursor,pageSize})` 返回 `records`、不透明 `nextCursor`、`hasMore`、`sourceDisclosure`。游标只能传回原提供方；调用方不能解析或拼接。连接器只能返回预览 DTO，不得直接写主账本。

`ExternalTransaction` 与连接器 `MarketQuote` 是反腐层 DTO，不是 `core-domain` 实体。应用/数据适配器在用户确认后显式转换成 `com.hengji.domain.Transaction/MarketQuote`，转换时重新校验日期、币种、方向和金额；领域层不得依赖连接器来源字段或 HTTP/文件格式。

## 3. CSV/JSON 导入

### 3.1 支持形状

CSV 第一条记录是唯一、非空表头，支持 RFC 4180 风格双引号、转义引号和引号内换行。JSON 支持两种根：

```json
[{"occurredAt":"2026-07-01","amountMinor":1234}]
```

```json
{"schemaVersion":1,"transactions":[{"occurredAt":"2026-07-01","amountMinor":1234}]}
```

首版交易字段只接受标量；嵌套对象/数组拒绝。字段映射至少包含 `occurredAt` 和 `amount`，并明确 `amountEncoding=major_decimal|minor_units`。金额的正负不承载业务语义，统一转为非负 magnitude；`direction=expense|income|refund` 单独保存。

### 3.2 预览与去重

每行结果为 `READY`、`DUPLICATE` 或 `INVALID`，并携带稳定错误码和源行号。`hj1_` 指纹由 connector、时间、金额、币种、方向、规范化商户和外部 ID 生成，用于幂等去重，不作为加密完整性证明。

同一文件后出现的重复行、已存在账本指纹均为 `DUPLICATE`；不会悄悄覆盖。危险公式前缀、坏日期、坏币种、金额过精度/溢出、缺失必填列均为 `INVALID`。

### 3.3 提交与撤销

`ImportCommitRequest` 包含调用方生成的唯一 `batchId`、单一 `sourceConnectorId` 和明确接受的 `READY` 行。持久化适配器必须在一个事务中完成：

1. 再次检查指纹；
2. 插入全部行；
3. 保存批次与成员关系；
4. 返回 `insertedFingerprints` 和 `committedAt`。

任一步失败则零写入。`rollbackBatch(batchId)` 也必须原子；重复撤销返回 `alreadyRolledBack=true`，不可误删其他批次或手工记录。持久化层应使用不可变记录 ID 处理用户在导入后编辑的场景。

软删除流水仍保留其导入指纹并在预览中判定为 `DUPLICATE`，不能因日常快照隐藏墓碑而重新导入。若回滚将删除某笔交易，而批次外仍有活跃退款引用它，整个回滚必须拒绝且零写入；同批次内的原交易与退款可一起回滚。

### 3.4 流水删除与撤销

`softDeleteTransaction(id, deletedAtEpochMillis)` 只接受非负 token，并在记录活跃且不存在活跃退款引用时原子写入墓碑。普通快照隐藏墓碑，完整快照与 JSON/CSV 导出保留 `deletedAtEpochMillis`。删除资产购买流水不得级联删除资产、维护、使用或报价记录。

`restoreTransaction(id, expectedDeletedAtEpochMillis)` 是 compare-and-set：只有 token 与当前墓碑精确相等才恢复并推进 revision；缺失、已恢复、token 错误或退款原交易不活跃均返回失败且不改 revision。应用层在同一进程内为每次删除生成严格单调 token，只在 8 秒内展示撤销入口；该入口不跨应用进程恢复。删除状态与成功恢复后的状态都必须跨账本重开持久化。

## 4. Connector Gateway

OpenAPI 文件：[connector-gateway/openapi.yaml](../services/connector-gateway/openapi.yaml)。

| 方法 | 路径 | 首版行为 |
|---|---|---|
| GET | `/health` | 无财务数据的健康状态 |
| GET | `/v1/connectors` | 仅返回四个 `sandbox`、`live=false` 提供方 |
| POST | `/v1/oauth/sandbox/start` | 校验精确 redirect URI，生成一次性 state 与 PKCE S256 |
| POST | `/v1/oauth/sandbox/callback` | 校验 connector/state/redirect，消费 state，明确 `tokenStored=false` |

生产模式当前构造即失败。真实平台接入后才可新增 `/v1/oauth/{provider}/start|callback|revoke`，并要求 issuer 绑定、token vault、scope 审批、域名白名单、审计和限流。沙箱 callback 中的 `code` 只用于验证形状，不交换、不保存、不记录。

## 5. Price Intelligence

OpenAPI 文件：[price-intelligence/openapi.yaml](../services/price-intelligence/openapi.yaml)。`POST /v1/estimates` 输入 0–100 条报价。每条报价必须有 provider、标题、成色、价格、运费、币种、观测时间、匹配置信度、来源、`isLive` 和披露。

估价顺序固定：字段/币种/时间验证 → 匹配阈值 → MAD 离群过滤 → 运费后价格排序 → 中位数与四分位 → 新鲜度、样本量、来源数与匹配度合成置信度。样本少于 3 或低置信度时 `medianMinor=null`，避免虚假精确；区间仍可展示但必须配合置信度。

客户端的本地出售目标价规则不等同于远端估价 API：它不发网络请求，先剔除 `demo_non_live`、异币种、未来和超过 90 天的报价，再要求至少 3 条入选报价及可呈现中位数。只有中位数达到用户目标时才生成应用内洞察；不会读取无日期的资产静态估值作为触发依据。

来源枚举：

- `manual`: 用户手工录入，`isLive=false`；
- `demo_non_live`: 静态演示，强制 `isLive=false` 且披露包含“非实时”；
- `official_or_contracted_api`: 只有真实合同/平台批准的适配器可用；仅当全部入选报价均属此类且自身声明 live 时，结果 `live=true`。

## 6. 幂等、重试与超时

- 本地导入由 `batchId + fingerprint` 幂等。
- OAuth start 不自动重试；每次产生新 state，旧 state 到期或消费后失效。
- OAuth callback 不重试交换授权码；网络不确定时查询 provider/本地流状态，不复用 code。
- 报价查询可在 429/502/503/504 上进行最多两次带抖动退避；其他 4xx 不重试。生产默认连接超时 3 秒、总超时 10 秒，具体提供方可更严。
- 任何外部连接器失败都不得阻塞本地记账、导入和查看历史数据。

## 7. 示例与契约测试

`samples/import` 是用户主动导入示例；`samples/api` 是协议示例。所有二手行情和平台返回样例均为虚构非实时数据，不得用于产品估值或宣传。CI 验证 Kotlin 导入/撤销、TypeScript OAuth state/生产拒绝、Python 离群/置信度/非实时披露。
