package com.hengji.connectors

import kotlinx.datetime.LocalDate

const val MAX_LOCAL_DOCUMENT_BYTES: Long = 20L * 1024 * 1024
const val MAX_LOCAL_DOCUMENT_PAGES: Int = 20
const val MAX_EXTRACTED_TEXT_CHARS: Int = 100_000
const val MAX_REVIEWED_CAPTURE_CANDIDATES: Int = 200

enum class LocalDocumentKind {
    IMAGE,
    PDF,
    USER_SHARED_TEXT,
    USER_SHARED_FINANCIAL_SMS,
}

enum class FieldConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class ReviewedField<T>(
    val value: T?,
    val confidence: FieldConfidence,
    val evidenceLabel: String,
    val confirmedByUser: Boolean = false,
) {
    val requiresConfirmation: Boolean
        get() = value == null || !confirmedByUser
}

data class ReviewedDocumentCandidate(
    val sourceKind: LocalDocumentKind,
    val merchant: ReviewedField<String>,
    val amountMinor: ReviewedField<Long>,
    val bookedOn: ReviewedField<LocalDate>,
    val currency: ReviewedField<String>,
    val categoryHint: ReviewedField<String>,
    val localOnlyDisclosure: String = "内容仅在本机解析；确认前不会写入账本。",
) {
    val canCommit: Boolean
        get() = listOf(merchant, amountMinor, bookedOn, currency, categoryHint)
            .all { !it.requiresConfirmation }

    fun confirmAll(): ReviewedDocumentCandidate = copy(
        merchant = merchant.copy(confirmedByUser = merchant.value != null),
        amountMinor = amountMinor.copy(confirmedByUser = amountMinor.value != null),
        bookedOn = bookedOn.copy(confirmedByUser = bookedOn.value != null),
        currency = currency.copy(confirmedByUser = currency.value != null),
        categoryHint = categoryHint.copy(confirmedByUser = categoryHint.value != null),
    )
}

sealed interface ReviewedDocumentParseResult {
    data class Candidate(val value: ReviewedDocumentCandidate) : ReviewedDocumentParseResult
    data class Rejected(val reason: String) : ReviewedDocumentParseResult
}

/**
 * Conservative parser for text produced by an on-device OCR engine or an explicit Android share.
 *
 * It never commits transactions and deliberately assigns MEDIUM/LOW confidence to inferred fields,
 * forcing the caller to present a review screen. The original text is not retained in the result.
 */
class ReviewedDocumentTextParser {
    fun parse(
        text: String,
        sourceKind: LocalDocumentKind,
    ): ReviewedDocumentParseResult {
        require(text.length <= MAX_EXTRACTED_TEXT_CHARS) { "Extracted document text exceeds the local limit" }
        val normalized = text.replace('\u00A0', ' ').trim()
        if (normalized.isBlank()) return ReviewedDocumentParseResult.Rejected("未识别到可供确认的文本")
        if (sourceKind == LocalDocumentKind.USER_SHARED_FINANCIAL_SMS && !looksFinancial(normalized)) {
            return ReviewedDocumentParseResult.Rejected("分享内容不像金融通知，已在本机拒绝")
        }

        val amountMatch = AMOUNT.find(normalized)
        val amountMinor = amountMatch?.groupValues?.get(1)?.let(::decimalToMinor)
        val date = DATE.find(normalized)?.let { match ->
            runCatching {
                LocalDate(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
        val merchant = merchant(normalized)
        val currency = when {
            CNY.containsMatchIn(normalized) -> "CNY"
            USD.containsMatchIn(normalized) -> "USD"
            else -> "CNY"
        }
        val category = category(normalized)

        return ReviewedDocumentParseResult.Candidate(
            ReviewedDocumentCandidate(
                sourceKind = sourceKind,
                merchant = ReviewedField(
                    value = merchant,
                    confidence = if (merchant == null) FieldConfidence.LOW else FieldConfidence.MEDIUM,
                    evidenceLabel = "商户候选",
                ),
                amountMinor = ReviewedField(
                    value = amountMinor,
                    confidence = if (amountMinor == null) FieldConfidence.LOW else FieldConfidence.HIGH,
                    evidenceLabel = "金额候选",
                ),
                bookedOn = ReviewedField(
                    value = date,
                    confidence = if (date == null) FieldConfidence.LOW else FieldConfidence.MEDIUM,
                    evidenceLabel = "日期候选",
                ),
                currency = ReviewedField(
                    value = currency,
                    confidence = if (CNY.containsMatchIn(normalized) || USD.containsMatchIn(normalized)) {
                        FieldConfidence.HIGH
                    } else {
                        FieldConfidence.LOW
                    },
                    evidenceLabel = "币种候选",
                ),
                categoryHint = ReviewedField(
                    value = category,
                    confidence = FieldConfidence.LOW,
                    evidenceLabel = "分类建议",
                ),
            ),
        )
    }

    private fun looksFinancial(text: String): Boolean =
        FINANCIAL_KEYWORDS.containsMatchIn(text) && AMOUNT.containsMatchIn(text)

    private fun merchant(text: String): String? {
        val explicit = MERCHANT.find(text)?.groupValues?.get(1)?.trim()?.take(80)
        if (!explicit.isNullOrBlank()) return explicit
        return text.lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.length in 2..80 &&
                    !AMOUNT.containsMatchIn(line) &&
                    !DATE.containsMatchIn(line) &&
                    !FINANCIAL_KEYWORDS.matches(line)
            }
    }

    private fun category(text: String): String = when {
        DINING.containsMatchIn(text) -> "餐饮"
        TRANSPORT.containsMatchIn(text) -> "交通"
        DIGITAL.containsMatchIn(text) -> "数码"
        HOME.containsMatchIn(text) -> "居家"
        else -> "其他"
    }

    private fun decimalToMinor(raw: String): Long? {
        val clean = raw.replace(",", "")
        val parts = clean.split('.')
        if (parts.size > 2 || parts[0].length > 16) return null
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = when (val value = parts.getOrNull(1).orEmpty()) {
            "" -> 0
            else -> value.padEnd(2, '0').take(2).toIntOrNull() ?: return null
        }
        if (whole > (Long.MAX_VALUE - fraction) / 100) return null
        return whole * 100 + fraction
    }

    private companion object {
        val AMOUNT = Regex("""(?i)(?:金额|消费|支付|支出|合计|total|amount|¥|￥|CNY|RMB|\$|USD)\s*[:：]?\s*(?:¥|￥|CNY|RMB|\$|USD)?\s*([0-9]{1,16}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""")
        val DATE = Regex("""\b(20[0-9]{2})[-/.年](0?[1-9]|1[0-2])[-/.月](0?[1-9]|[12][0-9]|3[01])日?\b""")
        val CNY = Regex("""(?i)(?:¥|￥|CNY|RMB|人民币|元)""")
        val USD = Regex("""(?i)(?:\$|USD|美元)""")
        val MERCHANT = Regex("""(?:商户|商家|收款方|merchant)\s*[:：]\s*([^\r\n]{2,80})""", RegexOption.IGNORE_CASE)
        val FINANCIAL_KEYWORDS = Regex("""(?i)(?:消费|支付|扣款|交易|账单|支出|退款|入账|银行|信用卡|amount|paid|purchase)""")
        val DINING = Regex("""(?:餐|咖啡|奶茶|外卖|饭店|restaurant|coffee)""", RegexOption.IGNORE_CASE)
        val TRANSPORT = Regex("""(?:地铁|公交|打车|加油|停车|滴滴|taxi|metro)""", RegexOption.IGNORE_CASE)
        val DIGITAL = Regex("""(?:手机|电脑|数码|软件|会员|digital|software)""", RegexOption.IGNORE_CASE)
        val HOME = Regex("""(?:家居|日用|超市|水电|物业|home|grocery)""", RegexOption.IGNORE_CASE)
    }
}

data class ReviewedDocumentBatchCandidate(
    val merchant: String,
    val amountMinor: Long,
    val bookedOn: LocalDate,
    val currency: String,
    val categoryHint: String,
    val direction: String,
) {
    init {
        require(merchant.length in 2..80)
        require(amountMinor > 0)
        require(currency in setOf("CNY", "USD"))
        require(categoryHint in setOf("餐饮", "交通", "居家", "数码", "其他"))
        require(direction in setOf("expense", "income", "refund"))
    }
}

data class ReviewedDocumentBatch(
    val sourceKind: LocalDocumentKind,
    val candidates: List<ReviewedDocumentBatchCandidate>,
    val skippedAmountCount: Int,
    val localOnlyDisclosure: String =
        "图片文字仅在本机识别；原图和 OCR 原文不会写入账本。请在预览页逐笔确认。",
) {
    init {
        require(candidates.size in 1..MAX_REVIEWED_CAPTURE_CANDIDATES)
        require(skippedAmountCount >= 0)
    }
}

sealed interface ReviewedDocumentBatchParseResult {
    data class Batch(val value: ReviewedDocumentBatch) : ReviewedDocumentBatchParseResult
    data class Rejected(val reason: String) : ReviewedDocumentBatchParseResult
}

/**
 * Extracts multiple conservative transaction candidates from OCR text such as a long payment-app
 * screenshot. Only complete merchant/amount/date candidates leave this boundary; raw OCR text is
 * never retained. Missing or ambiguous rows are counted and skipped for explicit user safety.
 */
class ReviewedDocumentBatchParser {
    fun parse(
        text: String,
        sourceKind: LocalDocumentKind,
        asOf: LocalDate,
        allowDefaultDate: Boolean,
    ): ReviewedDocumentBatchParseResult {
        require(text.length <= MAX_EXTRACTED_TEXT_CHARS) { "Extracted document text exceeds the local limit" }
        require(sourceKind in setOf(LocalDocumentKind.IMAGE, LocalDocumentKind.PDF))
        val lines = text
            .replace('\u00A0', ' ')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(MAX_OCR_LINES + 1)
            .toList()
        if (lines.isEmpty()) return ReviewedDocumentBatchParseResult.Rejected("没有识别到可检查的文字")
        if (lines.size > MAX_OCR_LINES) {
            return ReviewedDocumentBatchParseResult.Rejected("截图内容过长，请分成两张后重试")
        }

        val candidates = mutableListOf<ReviewedDocumentBatchCandidate>()
        var skipped = 0
        var candidateOverflow = false
        var activeDate: LocalDate? = null
        for ((index, line) in lines.withIndex()) {
            parseDate(line, asOf)?.let { activeDate = it }
            val amount = parseAmount(line) ?: continue
            if (SUMMARY_LINE.containsMatchIn(line)) {
                skipped++
                continue
            }
            val nearby = nearbyLines(lines, index)
            val date = activeDate
                ?: lines.subList(index + 1, (index + 4).coerceAtMost(lines.size))
                    .firstNotNullOfOrNull { parseDate(it, asOf) }
                ?: asOf.takeIf { allowDefaultDate }
            val merchant = merchant(lines, index, amount.matchStart)
            if (date == null || merchant == null) {
                skipped++
                continue
            }
            val context = nearby.joinToString(" ")
            val rowContext = lines.subList(
                (index - 1).coerceAtLeast(0),
                (index + 2).coerceAtMost(lines.size),
            ).joinToString(" ")
            val candidate = ReviewedDocumentBatchCandidate(
                merchant = merchant,
                amountMinor = amount.minorUnits,
                bookedOn = date,
                currency = if (USD.containsMatchIn(line)) "USD" else "CNY",
                categoryHint = category(merchant).takeUnless { it == "其他" } ?: category(context),
                direction = when {
                    REFUND.containsMatchIn(rowContext) -> "refund"
                    INCOME.containsMatchIn(rowContext) -> "income"
                    else -> "expense"
                },
            )
            if (candidates.size < MAX_REVIEWED_CAPTURE_CANDIDATES) {
                candidates += candidate
            } else {
                candidateOverflow = true
            }
        }
        if (candidateOverflow) {
            return ReviewedDocumentBatchParseResult.Rejected(
                "一次最多检查 $MAX_REVIEWED_CAPTURE_CANDIDATES 笔，请把长截图分成两张后重试",
            )
        }
        if (candidates.isEmpty()) {
            return ReviewedDocumentBatchParseResult.Rejected(
                "没有找到同时包含日期、商户和金额的完整账单，请换清晰截图或改用手动记账",
            )
        }
        return ReviewedDocumentBatchParseResult.Batch(
            ReviewedDocumentBatch(sourceKind, candidates, skipped),
        )
    }

    private fun nearbyLines(lines: List<String>, index: Int): List<String> =
        lines.subList((index - 4).coerceAtLeast(0), (index + 4).coerceAtMost(lines.size))

    private fun merchant(lines: List<String>, amountIndex: Int, amountStart: Int): String? {
        val explicitIndexes = listOf(amountIndex) +
            (1..4).mapNotNull { offset -> (amountIndex - offset).takeIf { it >= 0 } } +
            (1..3).mapNotNull { offset -> (amountIndex + offset).takeIf { it <= lines.lastIndex } }
        explicitIndexes.firstNotNullOfOrNull { index ->
            EXPLICIT_MERCHANT.find(lines[index])?.groupValues?.get(1)?.cleanMerchant()
        }?.let { return it }

        lines[amountIndex]
            .take(amountStart)
            .cleanMerchant()
            ?.takeUnless(::isNoiseLine)
            ?.let { return it }

        val indexes = ((amountIndex - 1) downTo (amountIndex - 4).coerceAtLeast(0)).toList() +
            ((amountIndex + 1)..(amountIndex + 3).coerceAtMost(lines.lastIndex)).toList()
        return indexes.asSequence()
            .map { lines[it] }
            .filterNot(::isNoiseLine)
            .mapNotNull { it.cleanMerchant() }
            .firstOrNull()
    }

    private fun isNoiseLine(line: String): Boolean =
        parseAmount(line) != null ||
            parseDate(line, DATE_PROBE) != null ||
            GENERIC_LINE.matches(line.trim()) ||
            SUMMARY_LINE.containsMatchIn(line)

    private fun parseDate(raw: String, asOf: LocalDate): LocalDate? {
        if (TODAY.containsMatchIn(raw)) return asOf
        if (YESTERDAY.containsMatchIn(raw)) {
            return LocalDate.fromEpochDays(asOf.toEpochDays() - 1)
        }
        FULL_DATE.find(raw)?.let { match ->
            return runCatching {
                LocalDate(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
        val partial = MONTH_DAY.find(raw) ?: return null
        val month = partial.groupValues[1].toInt()
        val day = partial.groupValues[2].toInt()
        val currentYear = runCatching { LocalDate(asOf.year, month, day) }.getOrNull() ?: return null
        return if (currentYear.toEpochDays() - asOf.toEpochDays() > FUTURE_DATE_TOLERANCE_DAYS) {
            runCatching { LocalDate(asOf.year - 1, month, day) }.getOrNull()
        } else {
            currentYear
        }
    }

    private fun parseAmount(line: String): AmountHit? {
        val match = LABELED_AMOUNT.find(line)
            ?: SYMBOL_AMOUNT.find(line)
            ?: TRAILING_YUAN_AMOUNT.find(line)
            ?: NEGATIVE_AMOUNT.find(line)
            ?: return null
        val raw = match.groupValues.last { it.isNotBlank() }
        val minor = decimalToMinor(raw) ?: return null
        if (minor <= 0) return null
        return AmountHit(minor, match.range.first)
    }

    private fun decimalToMinor(raw: String): Long? {
        val clean = raw.replace(",", "")
        val parts = clean.split('.')
        if (parts.size > 2 || parts[0].length > 16) return null
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toIntOrNull() ?: 0
        if (whole > (Long.MAX_VALUE - fraction) / 100) return null
        return whole * 100 + fraction
    }

    private fun category(text: String): String = when {
        DINING.containsMatchIn(text) -> "餐饮"
        TRANSPORT.containsMatchIn(text) -> "交通"
        DIGITAL.containsMatchIn(text) -> "数码"
        HOME.containsMatchIn(text) -> "居家"
        else -> "其他"
    }

    private fun String.cleanMerchant(): String? =
        filterNot(Char::isISOControl)
            .replace(Regex("^[•·▪▫◆◇>-]+"), "")
            .replace(Regex("^(?:商户|商家|收款方|交易对方|商品|merchant)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '：', '-', '—')
            .take(80)
            .takeIf { value -> value.length >= 2 && value.any(Char::isLetterOrDigit) }

    private data class AmountHit(val minorUnits: Long, val matchStart: Int)

    private companion object {
        const val MAX_OCR_LINES = 5_000
        const val FUTURE_DATE_TOLERANCE_DAYS = 31L
        val DATE_PROBE = LocalDate(2026, 6, 15)
        val FULL_DATE = Regex("""(?<![0-9])(20[0-9]{2})[-/.年](0?[1-9]|1[0-2])[-/.月](0?[1-9]|[12][0-9]|3[01])日?(?![0-9])""")
        val MONTH_DAY = Regex("""(?<![0-9])(?:周[一二三四五六日天]\s*)?(0?[1-9]|1[0-2])[-/.月](0?[1-9]|[12][0-9]|3[01])日?(?![0-9])""")
        val TODAY = Regex("""(?:^|\s)今天(?:\s|$)""")
        val YESTERDAY = Regex("""(?:^|\s)昨天(?:\s|$)""")
        val LABELED_AMOUNT = Regex("""(?i)(?:金额|消费|支付|支出|收入|退款|合计|total|amount|paid)\s*[:：]?\s*(?:[-−+]\s*)?(?:¥|￥|CNY|RMB|\$|USD)?\s*([0-9]{1,16}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)(?![0-9.,])""")
        val SYMBOL_AMOUNT = Regex("""(?i)(?:[-−+]\s*)?(?:¥|￥|CNY|RMB|\$|USD)\s*(?:[-−+]\s*)?([0-9]{1,16}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)(?![0-9.,])""")
        val TRAILING_YUAN_AMOUNT = Regex("""(?<![0-9])([0-9]{1,16}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*元""")
        val NEGATIVE_AMOUNT = Regex("""(?:^|\s)[-−]\s*([0-9]{1,16}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)(?:\s|$)""")
        val USD = Regex("""(?i)(?:\$|USD|美元)""")
        val EXPLICIT_MERCHANT = Regex("""(?:商户|商家|收款方|交易对方|商品|merchant)\s*[:：]\s*([^\r\n]{2,80}?)(?=\s+(?:金额|消费|支付|支出|收入|退款|合计|total|amount|paid|¥|￥|CNY|RMB|\$|USD)|$)""", RegexOption.IGNORE_CASE)
        val SUMMARY_LINE = Regex("""(?i)(?:本月|本周|今日)?(?:总计|合计|支出合计|收入合计|月账单|total)""")
        val GENERIC_LINE = Regex("""(?i)(?:支付成功|交易成功|已完成|付款成功|消费|支付|支出|收入|退款|账单|全部|筛选|搜索|交易记录|收支明细|订单详情|查看详情|amount|paid|purchase)""")
        val REFUND = Regex("""(?:退款|退回|已退|refund)""", RegexOption.IGNORE_CASE)
        val INCOME = Regex("""(?:收入|收款|入账|转入|income)""", RegexOption.IGNORE_CASE)
        val DINING = Regex("""(?:餐|咖啡|奶茶|外卖|饭店|restaurant|coffee)""", RegexOption.IGNORE_CASE)
        val TRANSPORT = Regex("""(?:地铁|公交|打车|加油|停车|滴滴|taxi|metro)""", RegexOption.IGNORE_CASE)
        val DIGITAL = Regex("""(?:手机|电脑|数码|软件|会员|digital|software)""", RegexOption.IGNORE_CASE)
        val HOME = Regex("""(?:家居|日用|超市|水电|物业|home|grocery)""", RegexOption.IGNORE_CASE)
    }
}

interface LocalDocumentTextExtractor {
    /**
     * Platform implementations must enforce byte/page/pixel limits before decoding and must not upload input.
     */
    suspend fun extract(
        bytes: ByteArray,
        kind: LocalDocumentKind,
        pageLimit: Int = MAX_LOCAL_DOCUMENT_PAGES,
    ): String
}
