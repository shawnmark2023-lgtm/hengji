package com.hengji.quality

import com.hengji.data.InMemoryLedgerRepository
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.system.measureNanoTime

fun main(arguments: Array<String>) {
    val count = arguments.getOrNull(0)?.toIntOrNull() ?: 100_000
    val maxMillis = arguments.getOrNull(1)?.toLongOrNull() ?: 20_000
    val maxMemoryMiB = arguments.getOrNull(2)?.toLongOrNull() ?: 768
    require(count in 1..1_000_000) { "count must be between 1 and 1,000,000" }
    require(maxMillis > 0) { "maxMillis must be positive" }
    require(maxMemoryMiB > 0) { "maxMemoryMiB must be positive" }

    val runtime = Runtime.getRuntime()
    runtime.gc()
    val beforeBytes = runtime.totalMemory() - runtime.freeMemory()
    val repository = InMemoryLedgerRepository()
    var aggregateMinor = 0L
    val elapsedNanos = measureNanoTime {
        repeat(count) { index ->
            val amount = (index % 100_000 + 1).toLong()
            repository.upsertTransaction(
                Transaction(
                    id = TransactionId("quality-$index"),
                    kind = TransactionKind.EXPENSE,
                    amount = Money(amount, CurrencyCode.CNY),
                    bookedOn = LocalDate(2026, 7, index % 28 + 1),
                    categoryId = CategoryId("quality"),
                    merchant = Merchant("Quality Merchant ${index % 100}"),
                )
            )
        }
        aggregateMinor = repository.snapshot().transactions.sumOf { it.spendingContribution().minorUnits }
    }
    val elapsedMillis = elapsedNanos / 1_000_000
    val afterBytes = runtime.totalMemory() - runtime.freeMemory()
    val deltaBytes = (afterBytes - beforeBytes).coerceAtLeast(0)
    val deltaMiB = deltaBytes / (1024.0 * 1024.0)
    val expectedAggregate = (0 until count).sumOf { (it % 100_000 + 1).toLong() }
    val checks = linkedMapOf(
        "row_count" to (repository.snapshot().transactions.size == count),
        "aggregate" to (aggregateMinor == expectedAggregate),
        "elapsed_budget" to (elapsedMillis <= maxMillis),
        "memory_budget" to (deltaMiB <= maxMemoryMiB),
    )
    val result = jsonObject(
        "gate" to "large-ledger",
        "status" to if (checks.values.all { it }) "passed" else "failed",
        "testCount" to checks.size,
        "rows" to count,
        "elapsedMillis" to elapsedMillis,
        "maxMillis" to maxMillis,
        "memoryDeltaBytes" to deltaBytes,
        "memoryDeltaMiB" to deltaMiB,
        "maxMemoryMiB" to maxMemoryMiB,
        "aggregateMinor" to aggregateMinor,
        "checks" to checks,
        "limitation" to "In-memory developer/CI baseline; does not represent encrypted durable storage or a representative device.",
    )
    println(result)
    check(checks.values.all { it }) { "large-ledger quality harness failed" }
}

private fun jsonObject(vararg entries: Pair<String, Any?>): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${key.json()}:${value.toJsonValue()}" }

private fun Any?.toJsonValue(): String = when (this) {
    null -> "null"
    is String -> json()
    is Boolean, is Number -> toString()
    is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${key.toString().json()}:${value.toJsonValue()}"
    }
    is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
    else -> toString().json()
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}
