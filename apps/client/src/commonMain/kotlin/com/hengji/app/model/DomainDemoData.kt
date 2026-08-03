package com.hengji.app.model

import com.hengji.app.application.AssetSaleTargetProjector
import com.hengji.data.DemoLedger
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.AssetCostCalculator
import com.hengji.domain.DateRange
import com.hengji.domain.MarketQuoteEstimator
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.insights.EvidenceValue
import com.hengji.insights.Insight
import com.hengji.insights.InsightEngine
import com.hengji.insights.InsightPreferences
import com.hengji.insights.InsightSnapshot
import com.hengji.insights.InsightType
import com.hengji.insights.PersonalInsightProfile
import com.hengji.insights.PersonalInsightProfileBuilder
import com.hengji.insights.PersonalInsightModelContextFactory
import kotlinx.datetime.LocalDate

/**
 * UI seed data is derived through the same domain, repository and insight boundaries used by production.
 * All market quotes in this seed are DEMO provenance and are therefore never presented as live prices.
 */
internal object DomainDemoData {
    val initialSnapshot: LedgerSnapshot = DemoLedger.snapshot()

    private fun estimates(snapshot: LedgerSnapshot, asOf: LocalDate) = snapshot.assets.mapNotNull { asset ->
        MarketQuoteEstimator.estimate(asset.id, snapshot.marketQuotes, asOf)?.let { asset.id to it }
    }.toMap()

    fun transactions(snapshot: LedgerSnapshot, asOf: LocalDate): List<DemoTransaction> {
        val currentPeriodStart = startOfMonth(asOf)
        return snapshot.transactions
            .asSequence()
            .filterNot { it.isDeleted }
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
                    inCurrentPeriod = transaction.bookedOn >= currentPeriodStart && transaction.bookedOn <= asOf,
                )
            }
            .toList()
    }

    fun assets(snapshot: LedgerSnapshot, asOf: LocalDate): List<DemoAsset> {
        val estimates = estimates(snapshot, asOf)
        return snapshot.assets.map { asset ->
            val estimate = estimates[asset.id]
            val assetQuotes = snapshot.marketQuotes.filter { it.assetId == asset.id && it.collectedOn <= asOf }
            val nonDemoQuotes = assetQuotes.filter {
                it.provenance != QuoteProvenance.DEMO &&
                    it.price.currency == asset.purchasePrice.currency
            }
            val actionableEstimate = MarketQuoteEstimator.estimate(asset.id, nonDemoQuotes, asOf)
            val latestQuoteDate = assetQuotes.maxByOrNull { it.collectedOn }?.collectedOn
            val latestQuoteDateLabel = latestQuoteDate?.let {
                "${it.month.ordinal + 1} 月 ${it.day} 日"
            }
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
                marketMedianMinor = estimate?.median?.minorUnits,
                quoteCount = assetQuotes.size,
                marketConfidence = (estimate?.confidence?.basisPoints ?: 0) / 100,
                currencyCode = asset.purchasePrice.currency.value,
                saleTarget = AssetSaleTargetProjector.project(
                    asset = asset,
                    actionableEstimate = actionableEstimate,
                    hasNonDemoQuotes = nonDemoQuotes.isNotEmpty(),
                    hasDemoQuotes = assetQuotes.any { it.provenance == QuoteProvenance.DEMO },
                ),
                quoteUpdatedLabel = when {
                    estimate?.includesDemoData == true && estimate.includesManualData ->
                        "混合来源 · 含示例/手工 · 非实时${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.includesDemoData == true && estimate.includesLiveData ->
                        "混合来源 · 含示例 · 非实时${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.includesManualData == true && estimate.includesLiveData ->
                        "混合来源 · 含手工 · 非实时${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.includesDemoData == true ->
                        "示例行情 · 非实时${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.includesManualData == true ->
                        "手工估值 · 非实时${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.isEntirelyLiveData == true ->
                        "授权实时行情${latestQuoteDateLabel?.let { " · $it" }.orEmpty()}"
                    estimate?.includesLiveData == true -> "部分实时 / 历史来源 · 非实时"
                    else -> "手工估值 · 非实时"
                },
                dailyCostMinor = metrics.grossDailyOwnershipCost?.minorUnits ?: 0,
                netDailyCostMinor = metrics.netDailyCost?.minorUnits ?: 0,
                costPerUseMinor = metrics.netCostPerUse?.minorUnits ?: 0,
            )
        }
    }

    fun insights(
        snapshot: LedgerSnapshot,
        asOf: LocalDate,
        nowEpochMillis: Long,
    ): List<DemoInsight> = insightFeed(snapshot, asOf, nowEpochMillis).items

    fun insightFeed(
        snapshot: LedgerSnapshot,
        asOf: LocalDate,
        nowEpochMillis: Long,
    ): PersonalInsightFeed = insightComputation(snapshot, asOf, nowEpochMillis).feed

    fun insightComputation(
        snapshot: LedgerSnapshot,
        asOf: LocalDate,
        nowEpochMillis: Long,
    ): PersonalInsightComputation {
        val estimates = estimates(snapshot, asOf)
        val currentPeriodStart = startOfMonth(asOf)
        val nextPeriodStart = shiftMonth(currentPeriodStart, 1)
        val previousPeriodStart = shiftMonth(currentPeriodStart, -1)
        val knownTypes = enumValues<InsightType>().associateBy(InsightType::name)
        val storedPreferences = snapshot.insightPreferences
        val preferences = InsightPreferences(
            mutedTypes = storedPreferences.mutedTypes.mapNotNull(knownTypes::get).toSet(),
            ignoredDeduplicationKeys = storedPreferences.ignoredDeduplicationKeys,
            adoptedDeduplicationKeys = storedPreferences.adoptedDeduplicationKeys,
            snoozedUntilEpochMillisByKey = storedPreferences.snoozedUntilEpochMillisByKey,
            feedbackTypeByKey = storedPreferences.feedbackTypeByKey.mapNotNull { (key, typeName) ->
                knownTypes[typeName]?.let { type -> key to type }
            }.toMap(),
        )
        val profile = PersonalInsightProfileBuilder.build(
            transactions = snapshot.transactions,
            asOf = asOf,
            preferences = preferences,
        )
        val rankedInsights = InsightEngine().generate(
            snapshot = InsightSnapshot(
                asOf = asOf,
                currency = DemoLedger.cny,
                currentPeriod = DateRange(currentPeriodStart, nextPeriodStart),
                previousPeriod = DateRange(previousPeriodStart, currentPeriodStart),
                transactions = snapshot.transactions,
                assets = snapshot.assets,
                maintenanceCosts = snapshot.maintenanceCosts,
                usageEvents = snapshot.usageEvents,
                marketEstimates = estimates,
                marketQuotes = snapshot.marketQuotes,
            ),
            preferences = preferences,
            nowEpochMillis = nowEpochMillis,
        ).let(::retainPriceTargetInsights)
        val feed = PersonalInsightFeed(
            items = rankedInsights.map { toDemoInsight(it, profile) },
            learningStage = profile.stage,
            learningPercent = profile.confidenceBasisPoints / 100,
            observedTransactionCount = profile.activeTransactionCount,
            observedDays = profile.observedDays,
            feedbackCount = profile.feedbackCount,
        )
        return PersonalInsightComputation(
            feed = feed,
            modelRequest = rankedInsights.takeIf { it.isNotEmpty() }?.let {
                PersonalInsightModelContextFactory.create(profile, it)
            },
        )
    }

    private fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    private fun shiftMonth(date: LocalDate, delta: Int): LocalDate {
        val zeroBased = date.year * 12 + date.month.ordinal + delta
        val year = zeroBased.floorDiv(12)
        val month = zeroBased.mod(12) + 1
        return LocalDate(year, month, 1)
    }

    private fun toDemoInsight(insight: Insight, profile: PersonalInsightProfile? = null): DemoInsight {
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
            InsightType.PRICE_TARGET_REACHED -> Triple(
                "出售目标价已达到",
                "可信的非示例二手报价中位数已达到你设置的出售目标价。",
                "查看物品与报价",
            )
        }
        return DemoInsight(
            deduplicationKey = insight.deduplicationKey,
            type = insight.type,
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
            feedback = insight.feedback,
            personalizationReason = profile?.let {
                val affinity = it.affinities.getValue(insight.type)
                when {
                    affinity.helpfulCount > 0 -> "你曾认为这类洞察有帮助，因此本次优先展示。"
                    affinity.notRelevantCount > 0 -> "这类洞察曾被你标记为不适合，已降低展示优先级。"
                    it.feedbackCount > 0 -> "排序已结合你保存在本机的 ${it.feedbackCount} 次反馈。"
                    else -> "当前先依据 ${it.activeTransactionCount} 笔记录建立个人基线。"
                }
            },
        )
    }

    private fun evidenceLabel(code: String, value: EvidenceValue): String = when (value) {
        is EvidenceValue.Amount -> "${codeLabel(code)} ${formatMoney(value.value.minorUnits)}"
        is EvidenceValue.BasisPoints -> "${codeLabel(code)} ${value.value / 100}%"
        is EvidenceValue.Count -> "${codeLabel(code)} ${value.value}"
        is EvidenceValue.Days -> "${codeLabel(code)} ${value.value} 天"
        is EvidenceValue.Text -> "${codeLabel(code)} ${evidenceTextLabel(value.value)}"
    }

    private fun codeLabel(code: String): String = when (code) {
        "category.share" -> "品类占比"
        "category.net_spend" -> "品类净支出"
        "merchant.share" -> "商户占比"
        "merchant.net_spend" -> "商户净支出"
        "merchant.transactions" -> "交易笔数"
        "asset.usage" -> "使用次数"
        "asset.net_daily_cost" -> "日均净成本"
        "asset.market_median" -> "可信报价中位数"
        "asset.accepted_quote_count" -> "有效报价数"
        "asset.newest_quote_age" -> "最新报价距今"
        "asset.estimate_source" -> "报价来源"
        else -> code.substringAfterLast('.').replace('_', ' ')
    }

    private fun evidenceTextLabel(value: String): String = when (value) {
        "market_live_or_licensed" -> "官方或授权报价"
        "market_mixed_live_manual" -> "官方或授权与手工报价"
        "market_manual_quotes" -> "本机手工报价"
        else -> value
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

/**
 * Keeps the compact four-card feed while never silently dropping a reached user-defined target.
 * Existing rank order is preserved; the feed expands only when a reached-target card falls outside the cap.
 */
internal fun retainPriceTargetInsights(
    rankedInsights: List<Insight>,
    compactLimit: Int = 4,
): List<Insight> {
    require(compactLimit > 0)
    val retainedKeys = buildSet {
        rankedInsights.take(compactLimit).forEach { add(it.deduplicationKey) }
        rankedInsights
            .filter { it.type == InsightType.PRICE_TARGET_REACHED }
            .forEach { add(it.deduplicationKey) }
    }
    return rankedInsights.filter { it.deduplicationKey in retainedKeys }
}
