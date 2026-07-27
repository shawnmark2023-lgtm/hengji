package com.hengji.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hengji.app.application.PreviewLedgerGateway
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HengjiKeyboardUiTest {
    @Test
    fun keyboardTabOrderCanOpenLedgerWithoutPointerInput() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(900.dp, 700.dp)) {
                    HengjiApp(gatewayWithKeyboardTransaction())
                }
            }
        }
        waitUntilExactlyOneExists(hasText("概览") and hasClickAction())

        val overviewDestination = onNode(hasText("概览") and hasClickAction())
        overviewDestination.performSemanticsAction(SemanticsActions.RequestFocus)
        overviewDestination.assertIsFocused()
        overviewDestination.performKeyInput { pressKey(Key.Tab) }

        val ledgerDestination = onNode(hasText("流水") and hasClickAction())
        ledgerDestination.assertIsFocused()
        ledgerDestination.performKeyInput { pressKey(Key.Enter) }
        waitUntilExactlyOneExists(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "流水"),
        )
        onNodeWithText("键盘商户").assertIsDisplayed()
    }

    private fun ComposeUiTest.waitUntilExactlyOneExists(
        matcher: SemanticsMatcher,
        timeoutMillis: Long = 5_000,
    ) {
        waitUntil(
            conditionDescription = "exactly one node matching $matcher",
            timeoutMillis = timeoutMillis,
        ) {
            onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
    }

    private fun gatewayWithKeyboardTransaction() = PreviewLedgerGateway(
        InMemoryLedgerRepository(
            LedgerSnapshot(
                revision = 0,
                transactions = listOf(
                    Transaction(
                        id = TransactionId("keyboard"),
                        kind = TransactionKind.EXPENSE,
                        amount = Money(1_299, CurrencyCode.CNY),
                        bookedOn = LocalDate(2026, 7, 25),
                        categoryId = CategoryId("dining"),
                        merchant = Merchant("键盘商户"),
                    ),
                ),
                assets = emptyList(),
                maintenanceCosts = emptyList(),
                usageEvents = emptyList(),
                marketQuotes = emptyList(),
            ),
        ),
    )
}
