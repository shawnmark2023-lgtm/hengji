package com.hengji.app.application

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppAppearanceTest {
    @Test
    fun systemModeTracksThePlatformAppearance() {
        assertFalse(AppAppearanceMode.SYSTEM.resolve(systemDarkTheme = false))
        assertTrue(AppAppearanceMode.SYSTEM.resolve(systemDarkTheme = true))
    }

    @Test
    fun explicitModesRemainDeterministic() {
        assertFalse(AppAppearanceMode.LIGHT.resolve(systemDarkTheme = true))
        assertTrue(AppAppearanceMode.DARK.resolve(systemDarkTheme = false))
    }

    @Test
    fun systemReduceMotionCannotBeOverriddenByTheApp() {
        assertTrue(
            shouldReduceMotion(
                systemRequestsReduction = true,
                userRequestsAdditionalReduction = false,
            ),
        )
        assertTrue(
            shouldReduceMotion(
                systemRequestsReduction = false,
                userRequestsAdditionalReduction = true,
            ),
        )
        assertFalse(
            shouldReduceMotion(
                systemRequestsReduction = false,
                userRequestsAdditionalReduction = false,
            ),
        )
    }
}
