package com.hengji.connectors

class InMemoryImportLedger : ImportLedger {
    private val transactions = linkedMapOf<String, ExternalTransaction>()
    private val batches = linkedMapOf<String, ImportCommitResult>()
    private val rolledBack = mutableSetOf<String>()

    override fun existingFingerprints(fingerprints: Set<String>): Set<String> =
        fingerprints.intersect(transactions.keys)

    override fun commit(request: ImportCommitRequest, committedAt: String): ImportCommitResult {
        require(request.batchId !in batches) { "Batch id has already been committed" }
        val fingerprints = request.accepted.map { it.fingerprint }
        require(fingerprints.distinct().size == fingerprints.size) { "Batch contains duplicate fingerprints" }
        require(fingerprints.none { it in transactions }) { "Ledger already contains an imported transaction" }

        val result = ImportCommitResult(request.batchId, fingerprints, committedAt)
        request.accepted.forEach { transactions[it.fingerprint] = it }
        batches[request.batchId] = result
        return result
    }

    override fun rollbackBatch(batchId: String, rolledBackAt: String): ImportRollbackResult {
        val batch = requireNotNull(batches[batchId]) { "Unknown batch id" }
        if (batchId in rolledBack) {
            return ImportRollbackResult(batchId, emptyList(), rolledBackAt, alreadyRolledBack = true)
        }
        batch.insertedFingerprints.forEach(transactions::remove)
        rolledBack += batchId
        return ImportRollbackResult(batchId, batch.insertedFingerprints, rolledBackAt, alreadyRolledBack = false)
    }

    fun snapshot(): List<ExternalTransaction> = transactions.values.toList()
}
