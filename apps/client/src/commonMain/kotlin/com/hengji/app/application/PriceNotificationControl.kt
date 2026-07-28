package com.hengji.app.application

data class PriceNotificationControl(
    val status: String,
    val canRequest: Boolean,
    val request: () -> Unit,
    val disable: () -> Unit,
)

fun PriceNotificationControl.shouldDisplay(hasAuthorizedLiveQuotes: Boolean): Boolean =
    hasAuthorizedLiveQuotes || !canRequest
