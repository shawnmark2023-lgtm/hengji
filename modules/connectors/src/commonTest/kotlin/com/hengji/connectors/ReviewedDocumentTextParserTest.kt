package com.hengji.connectors

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
}
