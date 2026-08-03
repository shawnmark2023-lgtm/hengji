package com.hengji.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainActivityAccessibilityInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overviewPassesAutomatedAccessibilityChecks() {
        composeTestRule.onNodeWithText("跳过教程").performClick()
        composeTestRule.waitUntil(
            conditionDescription = "overview navigation is ready",
            timeoutMillis = 10_000,
        ) {
            composeTestRule
                .onAllNodes(hasText("首页") and hasClickAction())
                .fetchSemanticsNodes()
                .size == 1
        }

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}
