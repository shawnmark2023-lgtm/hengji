package com.hengji.connectors

class SandboxCommerceConnector(
    connectorId: String,
    displayName: String,
    private val records: List<ExternalTransaction>,
) : PlatformConnector {
    override val descriptor = ConnectorDescriptor(
        id = connectorId,
        displayName = "$displayName（沙箱）",
        capabilities = setOf(
            ConnectorCapability.TRANSACTIONS,
            ConnectorCapability.CATEGORIES,
            ConnectorCapability.REFUNDS,
            ConnectorCapability.INCREMENTAL_CURSOR,
        ),
        authorizationMode = AuthorizationMode.SANDBOX_ONLY,
        availability = ConnectorAvailability.SANDBOX,
        privacyClass = PrivacyClass.FINANCIAL_SENSITIVE,
        dataFields = listOf(
            DataFieldDeclaration("occurredAt", "交易排序与统计", true, 0),
            DataFieldDeclaration("amountMinor", "精确记录金额", true, 0),
            DataFieldDeclaration("merchant", "对账与分类建议", false, 0),
            DataFieldDeclaration("category", "支出占比分析", false, 0),
        ),
        disclosure = "沙箱演示数据，非实时、非真实账户同步，不代表平台已授权。",
    )
    override val authorizationState: AuthorizationState = AuthorizationState.NOT_REQUIRED

    override suspend fun fetch(request: ConnectorFetchRequest): ConnectorPage {
        val offset = request.cursor?.value?.toIntOrNull() ?: 0
        if (offset !in 0..records.size) {
            throw ConnectorException(ConnectorErrorCode.INVALID_CURSOR, "Sandbox cursor is invalid")
        }
        val page = records.drop(offset).take(request.pageSize)
        val nextOffset = offset + page.size
        return ConnectorPage(
            records = page,
            nextCursor = if (nextOffset < records.size) ConnectorCursor(nextOffset.toString()) else null,
            hasMore = nextOffset < records.size,
            sourceDisclosure = descriptor.disclosure,
        )
    }
}

object SandboxConnectorCatalog {
    private val names = listOf(
        "alipay-sandbox" to "支付宝",
        "wechat-pay-sandbox" to "微信支付",
        "taobao-sandbox" to "淘宝",
        "jd-sandbox" to "京东",
    )

    fun create(): List<PlatformConnector> = names.mapIndexed { index, (id, name) ->
        val occurredAt = "2026-06-${(index + 10).toString().padStart(2, '0')}T12:00:00+08:00"
        val amountMinor = (index + 1) * 2599L
        val fingerprint = StableTransactionFingerprint.create(
            occurredAt,
            amountMinor,
            "CNY",
            TransactionDirection.EXPENSE,
            "$name 演示商户",
            "demo-$index",
            id,
        )
        SandboxCommerceConnector(
            connectorId = id,
            displayName = name,
            records = listOf(
                ExternalTransaction(
                    occurredAt = occurredAt,
                    amountMinor = amountMinor,
                    currency = "CNY",
                    direction = TransactionDirection.EXPENSE,
                    merchant = "$name 演示商户",
                    category = "演示分类",
                    note = "沙箱演示数据，非实时",
                    externalId = "demo-$index",
                    sourceConnectorId = id,
                    fingerprint = fingerprint,
                ),
            ),
        )
    }
}
