package com.hengji.data

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ProtectedLedgerRepositoryTest {
    @Test
    fun createsEncryptedStoreAndReopensPersistedMutation() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()

        val opened = ProtectedLedgerRepository.open(store, "ledger-primary", keys)
        assertEquals(ProtectedLedgerOpenOutcome.CREATED_EMPTY, opened.outcome)
        assertEquals(1, keys.provisionCount)
        assertTrue(store.envelope?.contains("protected-ledger") == false)

        opened.repository.upsertTransaction(transaction("persisted", "fingerprint-persisted"))
        val reopened = ProtectedLedgerRepository.open(store, "ledger-primary", keys)

        assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
        assertEquals(1, keys.provisionCount)
        assertEquals(listOf("persisted"), reopened.repository.snapshot().transactions.map { it.id.value })
    }

    @Test
    fun clearRemovesEverySnapshotFieldAndReopensAsExistingEmptyLedger() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val repository = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository
        val populated = DemoLedger.snapshot().copy(
            revision = 7,
            insightPreferences = InsightPreferenceRecord(
                mutedTypes = setOf("LOW_USE_ASSET"),
                ignoredDeduplicationKeys = setOf("ignored-insight"),
                updatedAtEpochMillis = 25,
                adoptedDeduplicationKeys = setOf("adopted-insight"),
                snoozedUntilEpochMillisByKey = mapOf("snoozed-insight" to 50),
            ),
            importBatches = listOf(
                ImportBatchRecord(
                    batchId = "batch_clear_001",
                    sourceConnectorId = "local-test",
                    sourceDigest = "digest-clear-001",
                    state = ImportBatchState.ROLLED_BACK,
                    createdAtEpochMillis = 10,
                    committedAtEpochMillis = 20,
                    rolledBackAtEpochMillis = 30,
                ),
            ),
        )
        repository.replaceWith(populated)
        val populatedRevision = repository.snapshot(includeDeleted = true).revision

        repository.clear()

        val cleared = repository.snapshot(includeDeleted = true)
        assertEquals(populatedRevision + 1, cleared.revision)
        assertTrue(cleared.transactions.isEmpty())
        assertTrue(cleared.assets.isEmpty())
        assertTrue(cleared.maintenanceCosts.isEmpty())
        assertTrue(cleared.usageEvents.isEmpty())
        assertTrue(cleared.marketQuotes.isEmpty())
        assertEquals(InsightPreferenceRecord(), cleared.insightPreferences)
        assertTrue(cleared.importBatches.isEmpty())

        val reopened = ProtectedLedgerRepository.open(store, "ledger-primary", keys)
        val reopenedSnapshot = reopened.repository.snapshot(includeDeleted = true)
        assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
        assertEquals(cleared, reopenedSnapshot)
        assertEquals(1, keys.provisionCount)
    }

    @Test
    fun manualMarketQuotePersistsAcrossEncryptedReopen() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val repository = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository
        val originalSeed = DemoLedger.snapshot()
        val assetWithSaleTarget = originalSeed.assets.first().copy(
            saleTargetPrice = Money(18_800, CurrencyCode.CNY),
        )
        val seed = originalSeed.copy(
            assets = originalSeed.assets.map {
                if (it.id == assetWithSaleTarget.id) assetWithSaleTarget else it
            },
        )
        val baseQuote = seed.marketQuotes.first()
        repository.replaceWith(seed)
        repository.addMarketQuote(
            baseQuote.copy(
                id = "manual-encrypted-quote",
                providerId = QuoteProviderId("manual-local"),
                provenance = QuoteProvenance.MANUAL,
                sourceUrl = null,
                isLive = false,
            ),
        )

        val reopened = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository
        val quote = reopened.snapshot().marketQuotes.single { it.id == "manual-encrypted-quote" }

        assertEquals(QuoteProvenance.MANUAL, quote.provenance)
        assertFalse(quote.isLive)
        assertEquals(null, quote.sourceUrl)
        assertEquals(
            Money(18_800, CurrencyCode.CNY),
            reopened.snapshot().assets.single { it.id == assetWithSaleTarget.id }.saleTargetPrice,
        )
    }

    @Test
    fun rejectsWrongCurrencyQuoteForAddAndReplacement() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val repository = ProtectedLedgerRepository.open(
            store,
            "ledger-primary",
            TestProvisioningKeyProvider(),
        ).repository
        val seed = DemoLedger.snapshot()
        repository.replaceWith(seed)
        val wrongCurrency = seed.marketQuotes.first().copy(
            id = "wrong-currency-protected",
            price = Money(100, CurrencyCode("USD")),
            shipping = Money.zero(CurrencyCode("USD")),
        )

        assertFailsWith<IllegalArgumentException> {
            runProtectedTest { repository.addMarketQuote(wrongCurrency) }
        }
        assertFailsWith<IllegalArgumentException> {
            runProtectedTest {
                repository.replaceWith(seed.copy(marketQuotes = seed.marketQuotes + wrongCurrency))
            }
        }
        val quotedAsset = seed.assets.first { it.id == seed.marketQuotes.first().assetId }
        assertFailsWith<IllegalArgumentException> {
            runProtectedTest {
                repository.upsertAsset(
                    quotedAsset.copy(
                        purchasePrice = Money(quotedAsset.purchasePrice.minorUnits, CurrencyCode("USD")),
                        currentEstimatedValue = null,
                    ),
                )
            }
        }
        assertFalse(repository.snapshot().marketQuotes.any { it.id == wrongCurrency.id })
        assertEquals(CurrencyCode.CNY, repository.findAsset(quotedAsset.id)?.purchasePrice?.currency)
    }

    @Test
    fun failedAtomicWriteDoesNotPublishCandidateToMemory() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val repository = ProtectedLedgerRepository.open(
            store,
            "ledger-primary",
            TestProvisioningKeyProvider(),
        ).repository
        store.rejectNextWrite = true

        assertFailsWith<ConcurrentLedgerWriteException> {
            runProtectedTest { repository.upsertTransaction(transaction("rejected", "fingerprint-rejected")) }
        }
        assertTrue(repository.snapshot().transactions.isEmpty())
        assertEquals(0, repository.snapshot().revision)
    }

    @Test
    fun staleInstanceRefreshesAndCannotLoseExternalWrite() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val first = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository
        val second = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository

        second.upsertTransaction(transaction("external", "fingerprint-external"))

        assertEquals(listOf("external"), first.snapshot().transactions.map { it.id.value })
        first.upsertTransaction(transaction("local", "fingerprint-local"))
        assertEquals(
            setOf("external", "local"),
            second.snapshot().transactions.mapTo(mutableSetOf()) { it.id.value },
        )
    }

    @Test
    fun interruptedMigrationRetainsSourceAndCompletesOnRetry() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val source = TestMigrationSource(DemoLedger.snapshot(), failRetirement = true)

        assertFailsWith<IllegalStateException> {
            runProtectedTest {
                ProtectedLedgerRepository.open(store, "ledger-primary", keys, plaintextSource = source)
            }
        }
        assertFalse(source.retired)
        assertTrue(store.envelope != null)

        source.failRetirement = false
        val recovered = ProtectedLedgerRepository.open(
            store,
            "ledger-primary",
            keys,
            plaintextSource = source,
        )

        assertEquals(ProtectedLedgerOpenOutcome.COMPLETED_INTERRUPTED_MIGRATION, recovered.outcome)
        assertTrue(source.retired)
        assertEquals(DemoLedger.snapshot(), recovered.repository.snapshot(includeDeleted = true))
    }

    @Test
    fun migrationConflictFailsClosedAndRetainsPlaintext() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val encrypted = ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository
        encrypted.upsertTransaction(transaction("encrypted", "fingerprint-encrypted"))
        val source = TestMigrationSource(DemoLedger.snapshot())

        assertFailsWith<PlaintextLedgerMigrationConflictException> {
            runProtectedTest {
                ProtectedLedgerRepository.open(store, "ledger-primary", keys, plaintextSource = source)
            }
        }
        assertFalse(source.retired)
    }

    @Test
    fun existingEnvelopeWithMissingKeyFailsWithoutProvisioningReplacement() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val originalKeys = TestProvisioningKeyProvider()
        ProtectedLedgerRepository.open(store, "ledger-primary", originalKeys)
        val missing = TestProvisioningKeyProvider(keyAvailable = false)

        assertFailsWith<StorageProtectionException> {
            runProtectedTest { ProtectedLedgerRepository.open(store, "ledger-primary", missing) }
        }
        assertEquals(0, missing.provisionCount)
    }

    @Test
    fun existingKeyWithMissingEnvelopeFailsWithoutCreatingEmptyReplacement() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider(keyAvailable = true)

        assertFailsWith<StorageProtectionException> {
            runProtectedTest { ProtectedLedgerRepository.open(store, "ledger-primary", keys) }
        }

        assertEquals(0, keys.provisionCount)
        assertEquals(null, store.envelope)
    }

    @Test
    fun protectedPendingMarkerRecoversCrashAfterKeyProvisioning() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val journal = KeyBackedProtectedLedgerInitializationJournal("ledger-primary", keys)
        assertTrue(
            journal.compareAndSwap(
                null,
                ProtectedLedgerInitializationState.INITIALIZING_FRESH,
            ),
        )
        keys.loadOrCreateKey("ledger-primary").destroy()

        val recovered = ProtectedLedgerRepository.open(
            store = store,
            keyAlias = "ledger-primary",
            keyProvider = keys,
            initializationJournal = journal,
        )

        assertEquals(ProtectedLedgerOpenOutcome.CREATED_EMPTY, recovered.outcome)
        assertEquals(ProtectedLedgerInitializationState.READY, journal.readState())
        assertTrue(recovered.repository.snapshot().transactions.isEmpty())
    }

    @Test
    fun readyMarkerWithMissingEnvelopeStillFailsClosed() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val journal = KeyBackedProtectedLedgerInitializationJournal("ledger-primary", keys)
        assertTrue(
            journal.compareAndSwap(
                null,
                ProtectedLedgerInitializationState.INITIALIZING_FRESH,
            ),
        )
        assertTrue(
            journal.compareAndSwap(
                ProtectedLedgerInitializationState.INITIALIZING_FRESH,
                ProtectedLedgerInitializationState.READY,
            ),
        )

        assertFailsWith<StorageProtectionException> {
            runProtectedTest {
                ProtectedLedgerRepository.open(
                    store = store,
                    keyAlias = "ledger-primary",
                    keyProvider = keys,
                    initializationJournal = journal,
                )
            }
        }
        assertEquals(null, store.envelope)
    }

    @Test
    fun migrationPendingMarkerCannotBecomeFreshEmptyLedger() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val journal = KeyBackedProtectedLedgerInitializationJournal("ledger-primary", keys)
        assertTrue(
            journal.compareAndSwap(
                null,
                ProtectedLedgerInitializationState.INITIALIZING_MIGRATION,
            ),
        )

        assertFailsWith<StorageProtectionException> {
            runProtectedTest {
                ProtectedLedgerRepository.open(
                    store = store,
                    keyAlias = "ledger-primary",
                    keyProvider = keys,
                    initializationJournal = journal,
                )
            }
        }
        assertEquals(null, store.envelope)
    }

    @Test
    fun authenticatedEnvelopeCompletesInterruptedReadyTransition() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val keys = TestProvisioningKeyProvider()
        val protectedJournal = KeyBackedProtectedLedgerInitializationJournal("ledger-primary", keys)
        val failingJournal = FailingReadyInitializationJournal(protectedJournal)

        assertFailsWith<StorageProtectionException> {
            runProtectedTest {
                ProtectedLedgerRepository.open(
                    store = store,
                    keyAlias = "ledger-primary",
                    keyProvider = keys,
                    initializationJournal = failingJournal,
                )
            }
        }
        assertTrue(store.envelope != null)
        assertEquals(
            ProtectedLedgerInitializationState.INITIALIZING_FRESH,
            protectedJournal.readState(),
        )

        failingJournal.failReadyTransition = false
        val recovered = ProtectedLedgerRepository.open(
            store = store,
            keyAlias = "ledger-primary",
            keyProvider = keys,
            initializationJournal = failingJournal,
        )

        assertEquals(ProtectedLedgerOpenOutcome.COMPLETED_INTERRUPTED_INITIALIZATION, recovered.outcome)
        assertEquals(ProtectedLedgerInitializationState.READY, protectedJournal.readState())
    }

    @Test
    fun importCommitIsIdempotentAndRollbackOnlyRemovesInsertedRows() = runProtectedTest {
        val store = MemoryProtectedLedgerStore()
        val repository = ProtectedLedgerRepository.open(
            store,
            "ledger-primary",
            TestProvisioningKeyProvider(),
        ).repository
        repository.upsertTransaction(transaction("existing", "fingerprint-existing"))
        val request = CommitImportBatchRequest(
            batchId = "batch_secure_001",
            sourceConnectorId = "local-test",
            sourceDigest = "digest-001",
            createdAtEpochMillis = 10,
            committedAtEpochMillis = 20,
            transactions = listOf(
                transaction("duplicate-request", "fingerprint-existing"),
                transaction("inserted", "fingerprint-inserted"),
            ),
        )

        val committed = repository.commitImportBatch(request)
        val repeated = repository.commitImportBatch(request)
        val rolledBack = repository.rollbackImportBatch("batch_secure_001", 30)

        assertEquals(ImportBatchCommitStatus.COMMITTED, committed.status)
        assertEquals(listOf("inserted"), committed.insertedTransactionIds)
        assertEquals(listOf("fingerprint-existing"), committed.skippedFingerprints)
        assertEquals(ImportBatchCommitStatus.ALREADY_COMMITTED, repeated.status)
        assertEquals(listOf("inserted"), rolledBack.removedTransactionIds)
        assertEquals(listOf("existing"), repository.snapshot().transactions.map { it.id.value })
        assertEquals(ImportBatchState.ROLLED_BACK, repository.snapshot().importBatches.single().state)
    }
}

private class FailingReadyInitializationJournal(
    private val delegate: ProtectedLedgerInitializationJournal,
) : ProtectedLedgerInitializationJournal {
    var failReadyTransition = true

    override suspend fun readState(): ProtectedLedgerInitializationState? = delegate.readState()

    override suspend fun compareAndSwap(
        expectedState: ProtectedLedgerInitializationState?,
        replacementState: ProtectedLedgerInitializationState,
    ): Boolean {
        if (failReadyTransition && replacementState == ProtectedLedgerInitializationState.READY) return false
        return delegate.compareAndSwap(expectedState, replacementState)
    }
}

private class MemoryProtectedLedgerStore : ProtectedLedgerStore {
    var envelope: String? = null
    var rejectNextWrite: Boolean = false

    override suspend fun readEnvelope(): String? = envelope

    override suspend fun compareAndSwap(expectedEnvelope: String?, replacementEnvelope: String): Boolean {
        if (rejectNextWrite) {
            rejectNextWrite = false
            return false
        }
        if (envelope != expectedEnvelope) return false
        envelope = replacementEnvelope
        return true
    }
}

private class TestProvisioningKeyProvider(
    keyAvailable: Boolean = false,
) : ProvisioningDatabaseKeyProvider {
    var provisionCount: Int = 0
        private set
    private val key = ByteArray(32) { 42 }
    private val availableAliases = mutableSetOf<String>().apply {
        if (keyAvailable) add("ledger-primary")
    }

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? =
        if (alias in availableAliases) DatabaseKeyMaterial(key) else null

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial {
        if (alias !in availableAliases) {
            availableAliases += alias
            if (alias == "ledger-primary") provisionCount += 1
        }
        return DatabaseKeyMaterial(key)
    }
}

private class TestMigrationSource(
    private val snapshot: LedgerSnapshot,
    var failRetirement: Boolean = false,
) : PlaintextLedgerMigrationSource {
    var retired: Boolean = false
        private set

    override suspend fun snapshotForMigration(): LedgerSnapshot = snapshot

    override suspend fun retireAfterVerifiedMigration() {
        if (failRetirement) error("simulated retirement failure")
        retired = true
    }
}

private fun transaction(id: String, fingerprint: String) = Transaction(
    id = TransactionId(id),
    kind = TransactionKind.EXPENSE,
    amount = Money(1_234, CurrencyCode.CNY),
    bookedOn = LocalDate(2026, 7, 25),
    categoryId = CategoryId("daily"),
    merchant = Merchant("Test Merchant"),
    source = TransactionSource.FILE_IMPORT,
    importFingerprint = fingerprint,
)

private fun runProtectedTest(block: suspend () -> Unit) {
    var completion: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            completion = result
        }
    })
    requireNotNull(completion).getOrThrow()
}
