package com.hengji.data

/** Backwards-compatible entry point; the full bidirectional contract lives in [LedgerJsonCodec]. */
object LedgerJsonExporter {
    fun export(snapshot: LedgerSnapshot): String = LedgerJsonCodec.export(snapshot)
    fun restore(payload: String): LedgerSnapshot = LedgerJsonCodec.restore(payload)
}
