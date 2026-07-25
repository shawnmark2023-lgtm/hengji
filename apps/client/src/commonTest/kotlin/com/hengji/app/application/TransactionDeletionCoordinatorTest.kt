package com.hengji.app.application

import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionDeletionCoordinatorTest {
    @Test
    fun deleteHidesTransactionAndExactTokenUndoRestoresIt() = runTest {
        val transaction = transaction()
        val gateway = PreviewLedgerGateway(repositoryWith(transaction))
        val coordinator = TransactionDeletionCoordinator(gateway)

        val result = assertIs<TransactionDeletionResult.Deleted>(
            coordinator.delete(transaction.id, nowEpochMillis = 1_000),
        )

        assertTrue(gateway.snapshot().transactions.isEmpty())
        assertEquals(
            1_000,
            gateway.snapshot(includeDeleted = true).transactions.single().deletedAtEpochMillis,
        )

        assertTrue(coordinator.undo(result.pendingUndo, nowEpochMillis = 8_999))
        assertEquals(transaction, gateway.snapshot().transactions.single())
    }

    @Test
    fun staleOrIncorrectTokenNeverRestoresTransaction() = runTest {
        val transaction = transaction()
        val gateway = PreviewLedgerGateway(repositoryWith(transaction))
        val coordinator = TransactionDeletionCoordinator(gateway)
        val deleted = assertIs<TransactionDeletionResult.Deleted>(
            coordinator.delete(transaction.id, nowEpochMillis = 5_000),
        )
        val incorrectToken = deleted.pendingUndo.copy(deletedAtEpochMillis = 4_999)

        assertFalse(coordinator.undo(incorrectToken, nowEpochMillis = 5_100))
        assertFalse(coordinator.undo(deleted.pendingUndo, nowEpochMillis = 13_001))
        assertTrue(gateway.snapshot().transactions.isEmpty())
        assertEquals(
            5_000,
            gateway.snapshot(includeDeleted = true).transactions.single().deletedAtEpochMillis,
        )
    }

    @Test
    fun repeatedDeletionInSameMillisecondUsesNewTokenAndOldUndoCannotReviveIt() = runTest {
        val transaction = transaction()
        val gateway = PreviewLedgerGateway(repositoryWith(transaction))
        val coordinator = TransactionDeletionCoordinator(gateway)
        val first = assertIs<TransactionDeletionResult.Deleted>(
            coordinator.delete(transaction.id, nowEpochMillis = 1_000),
        ).pendingUndo
        assertTrue(coordinator.undo(first, nowEpochMillis = 1_000))

        val second = assertIs<TransactionDeletionResult.Deleted>(
            coordinator.delete(transaction.id, nowEpochMillis = 1_000),
        ).pendingUndo

        assertEquals(1_001, second.deletedAtEpochMillis)
        assertEquals(9_000, second.expiresAtEpochMillis)
        assertFalse(coordinator.undo(first, nowEpochMillis = 1_001))
        assertTrue(coordinator.undo(second, nowEpochMillis = 1_000))
        assertEquals(transaction, gateway.snapshot().transactions.single())
    }

    private fun repositoryWith(transaction: Transaction) = InMemoryLedgerRepository(
        LedgerSnapshot(
            revision = 0,
            transactions = listOf(transaction),
            assets = emptyList(),
            maintenanceCosts = emptyList(),
            usageEvents = emptyList(),
            marketQuotes = emptyList(),
        ),
    )

    private fun transaction() = Transaction(
        id = TransactionId("transaction-delete-test"),
        kind = TransactionKind.EXPENSE,
        amount = Money(1_299, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 7, 25),
        categoryId = CategoryId("dining"),
        merchant = Merchant("测试商户"),
    )
}
