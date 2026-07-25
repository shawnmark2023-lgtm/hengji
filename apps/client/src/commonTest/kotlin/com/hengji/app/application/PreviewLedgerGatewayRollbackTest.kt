package com.hengji.app.application

import com.hengji.data.ImportBatchItemRecord
import com.hengji.data.ImportBatchRecord
import com.hengji.data.ImportBatchState
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreviewLedgerGatewayRollbackTest {
    @Test
    fun externalActiveRefundRejectsRollbackWithoutPartialMutation() = runTest {
        val originalId = TransactionId("import-original")
        val original = transaction(
            id = originalId,
            kind = TransactionKind.EXPENSE,
            fingerprint = "fingerprint-original",
        )
        val externalRefund = transaction(
            id = TransactionId("manual-refund"),
            kind = TransactionKind.REFUND,
            originalTransactionId = originalId,
        )
        val snapshot = LedgerSnapshot(
            revision = 4,
            transactions = listOf(original, externalRefund),
            assets = emptyList(),
            maintenanceCosts = emptyList(),
            usageEvents = emptyList(),
            marketQuotes = emptyList(),
            importBatches = listOf(
                ImportBatchRecord(
                    batchId = "batch_refund_guard",
                    sourceConnectorId = "local-file-csv",
                    sourceDigest = "document:test",
                    state = ImportBatchState.COMMITTED,
                    createdAtEpochMillis = 1,
                    committedAtEpochMillis = 2,
                    items = listOf(
                        ImportBatchItemRecord(originalId.value, requireNotNull(original.importFingerprint)),
                    ),
                ),
            ),
        )
        val gateway = PreviewLedgerGateway(InMemoryLedgerRepository(snapshot))

        val error = assertFailsWith<IllegalArgumentException> {
            gateway.rollbackImportBatch("batch_refund_guard", rolledBackAtEpochMillis = 10)
        }

        assertEquals(
            "Cannot roll back a batch while an active refund outside the batch references one of its transactions",
            error.message,
        )
        assertEquals(snapshot, gateway.snapshot(includeDeleted = true))
    }

    private fun transaction(
        id: TransactionId,
        kind: TransactionKind,
        fingerprint: String? = null,
        originalTransactionId: TransactionId? = null,
    ) = Transaction(
        id = id,
        kind = kind,
        amount = Money(1_000, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 7, 25),
        categoryId = CategoryId("other"),
        originalTransactionId = originalTransactionId,
        importFingerprint = fingerprint,
    )
}
