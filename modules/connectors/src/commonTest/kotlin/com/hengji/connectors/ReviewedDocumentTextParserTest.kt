package com.hengji.connectors

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReviewedDocumentTextParserTest {
    private val parser = ReviewedDocumentTextParser()

    @Test
    fun `twenty redacted samples produce reviewable amount candidates`() {
        val samples = (1..20).map { index ->
            """
            电子回单
            商户：脱敏商户$index
            金额：¥ ${index}.2${index % 10}
            日期：2026-07-${index.toString().padStart(2, '0')}
            支付成功
            """.trimIndent()
        }

        val candidates = samples.map {
            assertIs<ReviewedDocumentParseResult.Candidate>(
                parser.parse(it, LocalDocumentKind.IMAGE),
            ).value
        }

        assertEquals(20, candidates.size)
        assertTrue(candidates.all { it.amountMinor.value != null })
        assertTrue(candidates.all { it.amountMinor.confidence == FieldConfidence.HIGH })
        assertTrue(candidates.all { !it.canCommit })
        assertTrue(candidates.all { it.confirmAll().canCommit })
    }

    @Test
    fun `nonfinancial shared sms is rejected locally`() {
        val result = parser.parse(
            "验证码 123456，请勿告知他人",
            LocalDocumentKind.USER_SHARED_FINANCIAL_SMS,
        )

        assertIs<ReviewedDocumentParseResult.Rejected>(result)
    }

    @Test
    fun `ambiguous fields cannot commit without confirmation`() {
        val result = assertIs<ReviewedDocumentParseResult.Candidate>(
            parser.parse("咖啡店\n消费 28.00", LocalDocumentKind.USER_SHARED_TEXT),
        ).value

        assertFalse(result.canCommit)
        assertEquals(2_800L, result.amountMinor.value)
        assertEquals(FieldConfidence.LOW, result.currency.confidence)
    }

    @Test
    fun `long screenshot produces multiple complete local candidates`() {
        val result = assertIs<ReviewedDocumentBatchParseResult.Batch>(
            ReviewedDocumentBatchParser().parse(
                text = """
                    8月3日
                    星巴克咖啡
                    -￥35.50
                    滴滴出行
                    - 28.00
                    本月支出合计 ￥999.00
                """.trimIndent(),
                sourceKind = LocalDocumentKind.IMAGE,
                asOf = LocalDate(2026, 8, 3),
                allowDefaultDate = false,
            ),
        ).value

        assertEquals(2, result.candidates.size)
        assertEquals(listOf(3_550L, 2_800L), result.candidates.map { it.amountMinor })
        assertEquals(listOf("餐饮", "交通"), result.candidates.map { it.categoryHint })
        assertTrue(result.candidates.all { it.bookedOn == LocalDate(2026, 8, 3) })
        assertEquals(1, result.skippedAmountCount)
    }

    @Test
    fun `long screenshot skips rows without a date while a single receipt can use today`() {
        val text = "咖啡店\n支付：￥18.00"
        val longScreenshot = ReviewedDocumentBatchParser().parse(
            text,
            LocalDocumentKind.IMAGE,
            LocalDate(2026, 8, 3),
            allowDefaultDate = false,
        )
        val receipt = assertIs<ReviewedDocumentBatchParseResult.Batch>(
            ReviewedDocumentBatchParser().parse(
                text,
                LocalDocumentKind.IMAGE,
                LocalDate(2026, 8, 3),
                allowDefaultDate = true,
            ),
        ).value

        assertIs<ReviewedDocumentBatchParseResult.Rejected>(longScreenshot)
        assertEquals(LocalDate(2026, 8, 3), receipt.candidates.single().bookedOn)
        assertEquals("咖啡店", receipt.candidates.single().merchant)
    }

    @Test
    fun `most recent date header wins and zero totals cannot create candidates`() {
        val result = assertIs<ReviewedDocumentBatchParseResult.Batch>(
            ReviewedDocumentBatchParser().parse(
                text = """
                    8月2日
                    早餐店
                    -￥10.00
                    8月3日
                    咖啡店
                    -￥20.00
                    优惠金额 ￥0.00
                """.trimIndent(),
                sourceKind = LocalDocumentKind.IMAGE,
                asOf = LocalDate(2026, 8, 3),
                allowDefaultDate = false,
            ),
        ).value

        assertEquals(
            listOf(LocalDate(2026, 8, 2), LocalDate(2026, 8, 3)),
            result.candidates.map { it.bookedOn },
        )
        assertEquals(listOf(1_000L, 2_000L), result.candidates.map { it.amountMinor })
    }

    @Test
    fun `inline and adjacent merchant labels stay with their own amount`() {
        val result = assertIs<ReviewedDocumentBatchParseResult.Batch>(
            ReviewedDocumentBatchParser().parse(
                text = """
                    2026-08-03
                    商户：早餐店
                    金额：￥10.00
                    商户：星巴克 金额：￥20.00
                """.trimIndent(),
                sourceKind = LocalDocumentKind.IMAGE,
                asOf = LocalDate(2026, 8, 3),
                allowDefaultDate = false,
            ),
        ).value

        assertEquals(listOf("早餐店", "星巴克"), result.candidates.map { it.merchant })
    }

    @Test
    fun `malformed precision and excessive candidate counts are rejected`() {
        val malformed = ReviewedDocumentBatchParser().parse(
            "2026-08-03\n咖啡店\n￥12.345",
            LocalDocumentKind.IMAGE,
            LocalDate(2026, 8, 3),
            allowDefaultDate = false,
        )
        val excessive = ReviewedDocumentBatchParser().parse(
            buildString {
                appendLine("2026-08-03")
                repeat(MAX_REVIEWED_CAPTURE_CANDIDATES + 1) { index ->
                    appendLine("商户：测试商户$index")
                    appendLine("金额：￥1.00")
                }
            },
            LocalDocumentKind.IMAGE,
            LocalDate(2026, 8, 3),
            allowDefaultDate = false,
        )

        assertIs<ReviewedDocumentBatchParseResult.Rejected>(malformed)
        assertIs<ReviewedDocumentBatchParseResult.Rejected>(excessive)
        assertTrue(excessive.reason.contains(MAX_REVIEWED_CAPTURE_CANDIDATES.toString()))
    }
}
