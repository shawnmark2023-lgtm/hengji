package com.hengji.connectors

import kotlinx.datetime.LocalDate

const val MAX_LOCAL_DOCUMENT_BYTES: Long = 20L * 1024 * 1024
const val MAX_LOCAL_DOCUMENT_PAGES: Int = 20
const val MAX_EXTRACTED_TEXT_CHARS: Int = 100_000

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
