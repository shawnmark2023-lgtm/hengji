package com.hengji.app.application

/**
 * Keeps the system appearance as the default while still allowing an explicit,
 * reversible user choice. This avoids trapping the app in a custom theme after
 * the system appearance changes.
 */
enum class AppAppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun resolve(systemDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

fun shouldReduceMotion(
    systemRequestsReduction: Boolean,
    userRequestsAdditionalReduction: Boolean,
): Boolean = systemRequestsReduction || userRequestsAdditionalReduction
