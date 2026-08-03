package com.hengji.app.application

import com.hengji.app.importflow.LocalCaptureMode

data class QuickEntryRequest(
    val sequence: Long,
    val merchant: String = "",
    val amountMinor: Long? = null,
    val categoryLabel: String = "其他",
    val sourceDisclosure: String? = null,
) {
    init {
        require(sequence >= 0)
        require(merchant.length <= 100)
        require(amountMinor == null || amountMinor > 0)
        require(categoryLabel in setOf("餐饮", "交通", "居家", "数码", "其他"))
        require(sourceDisclosure == null || sourceDisclosure.length <= 240)
    }
}

data class LocalCaptureLaunchRequest(
    val sequence: Long,
    val mode: LocalCaptureMode,
) {
    init {
        require(sequence > 0)
    }
}
