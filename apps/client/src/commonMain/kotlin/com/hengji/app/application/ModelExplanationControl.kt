package com.hengji.app.application

data class ModelExplanationControl(
    val enabled: Boolean,
    val status: String,
    val onEnabledChange: (Boolean) -> Unit,
)
