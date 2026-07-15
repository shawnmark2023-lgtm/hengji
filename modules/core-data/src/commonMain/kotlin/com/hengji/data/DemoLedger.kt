package com.hengji.data

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.AssetStatus
import com.hengji.domain.CategoryId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MaintenanceCostId
import com.hengji.domain.MarketQuote
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import kotlinx.datetime.LocalDate

object DemoLedger {
    val dining = CategoryId("dining")
    val digital = CategoryId("digital")
    val transport = CategoryId("transport")
    val home = CategoryId("home")
    val cny = CurrencyCode.CNY

    fun snapshot(): LedgerSnapshot {
        val headphonesId = AssetId("asset-headphones")
        val deskChairId = AssetId("asset-chair")
        val assets = listOf(
            Asset(
                id = headphonesId,
                name = "降噪耳机",
                categoryId = digital,
                purchasePrice = Money(2_699_00, cny),
                purchasedOn = LocalDate(2026, 1, 12),
                targetUseDays = 730,
                currentEstimatedValue = Money(1_820_00, cny),
            ),
            Asset(
                id = deskChairId,
                name = "人体工学椅",
                categoryId = home,
                purchasePrice = Money(3_280_00, cny),
                purchasedOn = LocalDate(2025, 10, 3),
                status = AssetStatus.ACTIVE,
                currentEstimatedValue = Money(2_050_00, cny),
            ),
        )

        val transactions = listOf(
            sampleExpense("tx-1", 3_280_00, LocalDate(2025, 10, 3), home, "家居旗舰店", deskChairId),
            sampleExpense("tx-2", 2_699_00, LocalDate(2026, 1, 12), digital, "数码自营", headphonesId),
            sampleExpense("tx-3", 4_680, LocalDate(2026, 7, 11), dining, "社区咖啡"),
            sampleExpense("tx-4", 12_900, LocalDate(2026, 7, 12), transport, "城市出行"),
            sampleExpense("tx-5", 8_600, LocalDate(2026, 7, 13), dining, "轻食工坊"),
        )

        val maintenance = listOf(
            MaintenanceCost(
                id = MaintenanceCostId("maintenance-earpads"),
                assetId = headphonesId,
                amount = Money(12_900, cny),
                occurredOn = LocalDate(2026, 6, 20),
                description = "替换耳罩",
            ),
        )

        val usage = buildList {
            repeat(18) { index ->
                add(
                    UsageEvent(
                        id = UsageEventId("headphones-use-$index"),
                        assetId = headphonesId,
                        occurredOn = LocalDate(2026, 6, 24 + index.coerceAtMost(6)),
                    ),
                )
            }
            repeat(40) { index ->
                add(
                    UsageEvent(
                        id = UsageEventId("chair-use-$index"),
                        assetId = deskChairId,
                        occurredOn = LocalDate(2026, 5, 1 + (index % 28)),
                    ),
                )
            }
        }

        val quotes = listOf(
            demoQuote("quote-h-1", headphonesId, 1_760_00),
            demoQuote("quote-h-2", headphonesId, 1_820_00),
            demoQuote("quote-h-3", headphonesId, 1_880_00),
            demoQuote("quote-c-1", deskChairId, 1_980_00),
            demoQuote("quote-c-2", deskChairId, 2_050_00),
            demoQuote("quote-c-3", deskChairId, 2_160_00),
        )

        return LedgerSnapshot(0, transactions, assets, maintenance, usage, quotes)
    }

    private fun sampleExpense(
        id: String,
        minorUnits: Long,
        date: LocalDate,
        category: CategoryId,
        merchant: String,
        assetId: AssetId? = null,
    ) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(minorUnits, cny),
        bookedOn = date,
        categoryId = category,
        merchant = Merchant(merchant),
        source = TransactionSource.SAMPLE,
        assetId = assetId,
    )

    private fun demoQuote(id: String, assetId: AssetId, minorUnits: Long) = MarketQuote(
        id = id,
        assetId = assetId,
        providerId = QuoteProviderId("demo-market"),
        provenance = QuoteProvenance.DEMO,
        specification = "示例规格，仅用于功能演示",
        condition = ItemCondition.GOOD,
        price = Money(minorUnits, cny),
        collectedOn = LocalDate(2026, 7, 15),
        confidence = Confidence(5_500),
    )
}
