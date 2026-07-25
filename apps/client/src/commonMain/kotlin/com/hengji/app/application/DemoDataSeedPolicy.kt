package com.hengji.app.application

import com.hengji.data.LedgerSnapshot

/**
 * Demo content is allowed only for a newly created, untouched ledger.
 *
 * A cleared ledger deliberately has a positive revision, so relaunching the app cannot
 * silently restore samples after the user chose to remove their local data.
 */
internal object DemoDataSeedPolicy {
    fun shouldSeed(
        enabled: Boolean,
        snapshot: LedgerSnapshot,
    ): Boolean =
        enabled &&
            snapshot.revision == 0L &&
            snapshot.transactions.isEmpty() &&
            snapshot.assets.isEmpty()
}
