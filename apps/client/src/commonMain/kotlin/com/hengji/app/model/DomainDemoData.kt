package com.hengji.app.model

import com.hengji.data.DemoLedger
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.AssetCostCalculator
import com.hengji.domain.DateRange
import com.hengji.domain.MarketQuoteEstimator
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.insights.EvidenceValue
import com.hengji.insights.Insight
import com.hengji.insights.InsightEngine
import com.hengji.insights.InsightSnapshot
import com.hengji.insights.InsightType
import kotlinx.datetime.LocalDate

/**
 * UI seed data is derived through the same domain, repository and insight boundaries used by production.
 * All market quotes in this seed are DEMO provenance and are therefore never presented as live prices.
 */
internal object DomainDemoData {
    private val asOf = LocalDate(2026, 7, 15)
    val initialSnapshot: LedgerSnapshot = DemoLedger.snapshot()

    private fun estimates(snapshot: LedgerSnapshot) = snapshot.assets.mapNotNull { asset ->
        MarketQuoteEstimator.estimate(asset.id, snapshot.marketQuotes, asOf)?.let { asset.id to it }
    }.toMap()

    fun transactions(snapshot: LedgerSnapshot): List<DemoTransaction> = snapshot.transactions
        .sortedByDescending { it.bookedOn }
        .map { transaction ->
            val signedAmount = if (transaction.kind == TransactionKind.REFUND) {
                -transaction.amount.minorUnits
            } else {
                transaction.amount.minorUnits
            }
            DemoTransaction(
                id = transaction.id.value,
                merchant = transaction.merchant?.displayName ?: "未命名交易",
                category = categoryLabel(transaction.categoryId.value),
                amountMinor = signedAmount,
                dateLabel = "${transaction.bookedOn.month.ordinal + 1} 月 ${transaction.bookedOn.day} 日",
                sourceLabel = sourceLabel(transaction.source),
                kind = when (transaction.kind) {
                    TransactionKind.EXPENSE -> EntryKind.Expense
                    TransactionKind.INCOME -> EntryKind.Income
                    TransactionKind.REFUND -> EntryKind.Refund
                },
                inCurrentPeriod = transaction.bookedOn >= LocalDate(2026, 7, 1),
            )
        }

    fun assets(snapshot: LedgerSnapshot): List<DemoAsset> {
        val estimates = estimates(snapshot)
        return snapshot.assets.map { asset ->
        val estimate = estimates[asset.id]
        val metrics = AssetCostCalculator.calculate(
            asset = asset,
            maintenanceCosts = snapshot.maintenanceCosts.filter { it.assetId == asset.id },
            usageEvents = snapshot.usageEvents.filter { it.assetId == asset.id },
            asOf = asOf,
            residualValue = estimate?.median ?: asset.currentEstimatedValue
                ?: error("Demo asset must have a residual estimate"),
        )
        DemoAsset(
            id = asset.id.value,
            name = asset.name,
            variant = when (asset.id.value) {
                "asset-headphones" -> "无线 · 国行 · 良好"
                "asset-chair" -> "黑色 · 良好"
                else -> "规格待补充"
            },
            ownedDays = metrics.ownedDays,
            usageCount = metrics.useQuantity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalCostMinor = metrics.totalOwnershipCost.minorUnits,
            currentValueMinor = metrics.residualValue.minorUnits,
            marketLowMinor = estimate?.minimum?.minorUnits ?: metrics.residualValue.minorUnits,
            marketHighMinor = estimate?.maximum?.minorUnits ?: metrics.residualValue.minorUnits,
            marketConfidence = (estimate?.confidence?.basisPoints ?: 0) / 100,
            quoteUpdatedLabel = when {
                estimate?.includesDemoData == true && estimate.includesLiveData -> "混合来源 · 含示例 · 非实时 · ${asOf.month.ordinal + 1} 月 ${asOf.day} 日"
                estimate?.includesManualData == true && estimate.includesLiveData -> "混合来源 · 含手工 · 非实时 · ${asOf.month.ordinal + 1} 月 ${asOf.day} 日"
                estimate?.includesDemoData == true -> "示例行情 · 非实时 · ${asOf.month.ordinal + 1} 月 ${asOf.day} 日"
                estimate?.includesManualData == true -> "手工估值 · 非实时"
                estimate?.isEntirelyLiveData == true -> "授权实时行情 · ${asOf.month.ordinal + 1} 月 ${asOf.day} 日"
                estimate?.includesLiveData == true -> "部分实时 / 历史来源 · 非实时"
                else -> "手工估值 · 非实时"
            },
            dailyCostMinor = metrics.grossDailyOwnershipCost?.minorUnits ?: 0,
            netDailyCostMinor = metrics.netDailyCost?.minorUnits ?: 0,
            costPerUseMinor = metrics.netCostPerUse?.minorUnits ?: 0,
        )
        }
    }

    fun insights(snapshot: LedgerSnapshot): List<DemoInsight> {
        val estimates = estimates(snapshot)
        return InsightEngine().generate(
        InsightSnapshot(
            asOf = asOf,
            currency = DemoLedger.cny,
            currentPeriod = DateRange(LocalDate(2026, 7, 1), LocalDate(2026, 8, 1)),
            previousPeriod = DateRange(LocalDate(2026, 6, 1), LocalDate(2026, 7, 1)),
            transactions = snapshot.transactions,
            assets = snapshot.assets,
            maintenanceCosts = snapshot.maintenanceCosts,
            usageEvents = snapshot.usageEvents,
            marketEstimates = estimates,
        ),
        ).take(4).map(::toDemoInsight)
    }

    private fun toDemoInsight(insight: Insight): DemoInsight {
        val localized = when (insight.type) {
            InsightType.CATEGORY_CONCENTRATION -> Triple(
                "一类支出占比较高",
                "该品类在本期净支出中占比较高，建议先复核其中可调整的项目。",
                "查看这一品类的消费记录",
            )
            InsightType.BUDGET_PACE -> Triple("预算消耗偏快", "按当前节奏，本期支出可能超过预算。", "调整剩余额度")
            InsightType.SPENDING_TREND -> Triple("支出较上期增加", "退款按入账日扣减后，本期净支出仍高于上期。", "比较变化最大的品类")
            InsightType.MERCHANT_CONCENTRATION -> Triple("支出集中于一个商户", "单一商户占商户类支出的比例较高。", "查看该商户交易")
            InsightType.LARGE_EXPENSE -> Triple("发现一笔大额支出", "该笔消费明显高于本期消费中位数。", "确认并补全分类")
            InsightType.POSSIBLE_DUPLICATE -> Triple("可能存在重复扣款", "同一商户和金额在较短时间内重复出现。", "并排核对两笔记录")
            InsightType.POSSIBLE_SUBSCRIPTION -> Triple("可能是一项订阅", "相似金额按稳定周期重复出现，请确认后再归类。", "确认或忽略订阅")
            InsightType.LOW_USAGE_ASSET -> Triple("物品使用率偏低", "持有期内记录的使用次数较少。", "记录使用或复核是否保留")
            InsightType.SELL_CANDIDATE -> Triple("物品可考虑出售", "低使用物品仍有可参考的二手残值。", "核对成色并创建出售清单")
        }
        return DemoInsight(
            title = localized.first,
            summary = localized.second,
            evidence = insight.evidence.joinToString(" · ") { evidenceLabel(it.code, it.observed) },
            action = localized.third,
            impactMinor = insight.estimatedImpact?.minorUnits ?: 0,
            confidence = insight.confidence.basisPoints / 100,
            priority = when {
                insight.rankScore >= 6_500 -> InsightPriority.High
                insight.rankScore >= 3_500 -> InsightPriority.Medium
                else -> InsightPriority.Low
            },
        )
    }

    private fun evidenceLabel(code: String, value: EvidenceValue): String = when (value) {
        is EvidenceValue.Amount -> "${codeLabel(code)} ${formatMoney(value.value.minorUnits)}"
        is EvidenceValue.BasisPoints -> "${codeLabel(code)} ${value.value / 100}%"
        is EvidenceValue.Count -> "${codeLabel(code)} ${value.value}"
        is EvidenceValue.Days -> "${codeLabel(code)} ${value.value} 天"
        is EvidenceValue.Text -> "${codeLabel(code)} ${value.value}"
    }

    private fun codeLabel(code: String): String = when (code) {
        "category.share" -> "品类占比"
        "category.net_spend" -> "品类净支出"
        "merchant.share" -> "商户占比"
        "merchant.net_spend" -> "商户净支出"
        "merchant.transactions" -> "交易笔数"
        "asset.usage" -> "使用次数"
        "asset.net_daily_cost" -> "日均净成本"
        else -> code.substringAfterLast('.').replace('_', ' ')
    }

    private fun categoryLabel(id: String): String = when (id) {
        "dining" -> "餐饮"
        "digital" -> "数码"
        "transport" -> "交通"
        "home" -> "居家"
        else -> "其他"
    }

    private fun sourceLabel(source: TransactionSource): String = when (source) {
        TransactionSource.MANUAL -> "手动记录 · 本机"
        TransactionSource.FILE_IMPORT -> "授权文件导入 · 本机"
        TransactionSource.SHARE_EXTENSION -> "分享扩展 · 本机"
        TransactionSource.OFFICIAL_CONNECTOR -> "官方授权连接器"
        TransactionSource.SAMPLE -> "演示账本 · 本机"
    }
}
