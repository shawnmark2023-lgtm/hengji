package com.hengji.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.PreviewLedgerGateway
import com.hengji.app.application.UserDocumentPurpose
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerJsonExporter
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.insights.PersonalInsightModelAnswer
import com.hengji.insights.PersonalInsightModelContext
import com.hengji.insights.PersonalInsightModelProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HengjiAppUiTest {
    @Test
    fun firstRunGuideExplainsCoreFlowAndCanBeReopenedFromSettings() = runComposeUiTest {
        val firstRunSnapshot = snapshotOf().copy(insightPreferences = InsightPreferenceRecord())
        val gateway = PreviewLedgerGateway(InMemoryLedgerRepository(firstRunSnapshot))
        setHengjiContent(gateway)

        onNodeWithText("欢迎使用恒迹").assertIsDisplayed()
        onNodeWithText("下一步").performClick()
        onNodeWithText("先记第一笔消费").assertIsDisplayed()
        onNodeWithText("下一步").performClick()
        onNodeWithText("有旧账单就直接导入").assertIsDisplayed()
        onNodeWithText("下一步").performClick()
        onNodeWithText("三个月后看专属分析").assertIsDisplayed()
        onNodeWithText("我会用了").performClick()
        waitUntil {
            runBlocking { gateway.snapshot().insightPreferences.onboardingCompletedAtEpochMillis != null }
        }

        navigateTo("设置")
        onNodeWithText("重新看使用教程").performScrollTo().performClick()
        onNodeWithText("欢迎使用恒迹").assertIsDisplayed()
    }

    @Test
    fun deletionRequiresConfirmationAndUndoRestoresTheVisibleLedgerRow() = runComposeUiTest {
        val merchant = "自动化咖啡"
        val gateway = gatewayWith(transaction("delete-me", merchant))
        setHengjiContent(gateway)

        navigateTo("账单")
        onNodeWithContentDescription("删除 $merchant 账单").performClick()
        onNodeWithText("删除这笔账？").assertIsDisplayed()
        onNodeWithText("确认删除").assertIsDisplayed()
        onNodeWithText("取消").performClick()
        onNodeWithText("删除这笔账？").assertDoesNotExist()
        onNodeWithText(merchant).assertIsDisplayed()

        onNodeWithContentDescription("删除 $merchant 账单").performClick()
        onNodeWithText("确认删除").performClick()
        waitUntilExactlyOneExists(hasText("这笔账已删除；8 秒内可撤销"))
        onNodeWithText(merchant).assertDoesNotExist()
        onNodeWithText("撤销").performClick()
        waitUntilExactlyOneExists(hasText(merchant))
        onNodeWithText("这笔账已删除；8 秒内可撤销").assertDoesNotExist()

        onNodeWithContentDescription("删除 $merchant 账单").performClick()
        onNodeWithText("确认删除").performClick()
        waitUntilExactlyOneExists(hasText("这笔账已删除；8 秒内可撤销"))
        mainClock.advanceTimeBy(8_100)
        waitForIdle()
        onNodeWithText("这笔账已删除；8 秒内可撤销").assertDoesNotExist()
        onNodeWithText(merchant).assertDoesNotExist()
    }

    @Test
    fun exportClearAndRestoreAreDrivenThroughSettingsUi() = runComposeUiTest {
        val originalMerchant = "清除前商户"
        val restoredMerchant = "恢复后商户"
        val gateway = gatewayWith(transaction("before-clear", originalMerchant))
        val restoredSnapshot = snapshotOf(transaction("after-restore", restoredMerchant))
        val picker = RecordingRestorePicker(LedgerJsonExporter.export(restoredSnapshot))
        val writer = RecordingExportWriter()
        setHengjiContent(gateway, picker, writer)

        navigateTo("设置")
        onNodeWithText("完整 JSON").performScrollTo().performClick()
        waitUntil { writer.calls.size == 1 }
        assertEquals("application/json", writer.calls.single().mediaType)
        assertEquals("JSON 数据导出", writer.calls.single().title)
        onNodeWithText("JSON 数据导出").assertIsDisplayed()
        onNodeWithText("完成").performClick()

        onNodeWithText("账单 CSV").performScrollTo().performClick()
        waitUntil { writer.calls.size == 2 }
        assertEquals("text/csv", writer.calls.last().mediaType)
        assertEquals("CSV 账单导出", writer.calls.last().title)
        onNodeWithText("CSV 账单导出").assertIsDisplayed()
        onNodeWithText("完成").performClick()

        onNodeWithText("清除数据").performScrollTo().performClick()
        onNodeWithText("清除所有本机数据？").assertIsDisplayed()
        onNodeWithText("取消").performClick()
        navigateTo("账单")
        onNodeWithText(originalMerchant).assertIsDisplayed()

        navigateTo("设置")
        onNodeWithText("清除数据").performScrollTo().performClick()
        onNodeWithText("确认清除").performClick()
        waitUntilExactlyOneExists(hasText("本机账本已清除"))
        onNodeWithText("跳过教程").performClick()
        navigateTo("账单")
        onNodeWithText(originalMerchant).assertDoesNotExist()
        onNodeWithText("没有找到这笔账").assertIsDisplayed()

        navigateTo("设置")
        onNodeWithText("恢复备份").performScrollTo().performClick()
        waitUntilExactlyOneExists(hasText("已从 自动化恢复.json 恢复本机账本"))
        assertEquals(UserDocumentPurpose.LedgerRestore, picker.observedPurpose)
        assertEquals(ImportDocumentFormat.Json, picker.observedFormat)
        navigateTo("账单")
        onNodeWithText(restoredMerchant).assertIsDisplayed()
        onNodeWithText(originalMerchant).assertDoesNotExist()
    }

    @Test
    fun sandboxImportRunsFromSourceThroughAtomicRollback() = runComposeUiTest {
        setHengjiContent(gatewayWith())

        navigateTo("设置")
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("选择文件并导入"))
        onNodeWithText("选择文件并导入").performClick()
        waitUntilExactlyOneExists(hasText("CSV 沙箱样例"))

        onNodeWithText("CSV 沙箱样例").performClick()
        waitUntilExactlyOneExists(hasText("确认字段对应关系"))
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("生成导入预览"))
        onNodeWithText("生成导入预览").performClick()
        waitUntilExactlyOneExists(hasText("逐条预览并处理重复"))

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("继续确认 3 笔"))
        onNode(hasText("继续确认 3 笔") and hasClickAction())
            .performClick()
        waitUntilExactlyOneExists(hasText("最后确认"))
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("确认导入 3 笔"))
        onNode(hasText("确认导入 3 笔") and hasClickAction())
            .performClick()
        waitUntilExactlyOneExists(hasText("导入完成"))

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("撤销整个导入批次"))
        onNodeWithText("撤销整个导入批次").performClick()
        waitUntilExactlyOneExists(hasText("批次已撤销"))
        navigateTo("账单")
        onNodeWithText("没有找到这笔账").assertIsDisplayed()
    }

    @Test
    fun compactTwoHundredPercentLayoutKeepsNavigationAndSettingsActionsAccessible() = runComposeUiTest {
        val gateway = gatewayWith(transaction("narrow", "窄屏商户"))
        setHengjiContent(
            gateway = gateway,
            widthDp = 360,
            heightDp = 720,
            fontScale = 2f,
        )

        onNodeWithContentDescription("记一笔").assertIsDisplayed()
        navigateTo("设置")
        onNodeWithText("完整 JSON").performScrollTo().assertIsDisplayed()
        onNodeWithText("账单 CSV").performScrollTo().assertIsDisplayed()
        onNodeWithText("恢复备份").performScrollTo().assertIsDisplayed()
        onNodeWithText("清除数据").performScrollTo().assertIsDisplayed()

        val verticalPage = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        onNode(verticalPage).performScrollToNode(hasText("跟随系统"))
        onNode(hasText("跟随系统") and hasClickAction()).assertIsSelected()
        onNode(verticalPage).performScrollToNode(hasText("进一步减少动态效果"))
        onNode(hasText("深色") and hasClickAction()).performClick()
        waitForIdle()
        onNode(hasText("深色") and hasClickAction()).assertIsSelected()
        onNode(verticalPage).performScrollToNode(hasText("跟随系统"))
        onNode(hasText("跟随系统") and hasClickAction()).performClick()
        waitForIdle()
        onNode(hasText("跟随系统") and hasClickAction()).assertIsSelected()
        val reduceMotionSwitch = onNode(hasText("进一步减少动态效果") and hasClickAction())
        reduceMotionSwitch.performScrollTo().assertIsOff().performClick().assertIsOn()

        onNode(verticalPage).performScrollToNode(hasText("查看隐私说明"))
        onNodeWithText("查看隐私说明").performClick()
        waitUntilExactlyOneExists(hasText("隐私说明"))
        onNodeWithText("本地优先").assertIsDisplayed()
        onNodeWithText("完成").performClick()
    }

    @Test
    fun interactiveControlsExposeRolesAndUnambiguousLabels() = runComposeUiTest {
        val merchant = "语义商户"
        setHengjiContent(gatewayWith(transaction("semantics", merchant)))

        onAllNodes(hasText("首页") and hasClickAction()).assertCountEquals(1)
        onAllNodes(hasText("账单") and hasClickAction()).assertCountEquals(1)
        onAllNodes(hasText("我的物品") and hasClickAction()).assertCountEquals(1)
        onAllNodes(hasText("智能分析") and hasClickAction()).assertCountEquals(1)
        onAllNodes(hasText("设置") and hasClickAction()).assertCountEquals(1)

        navigateTo("账单")
        onNodeWithContentDescription("删除 $merchant 账单")
            .assert(hasClickAction())
            .assertIsDisplayed()
        onAllNodes(hasContentDescription("记一笔")).assertAny(hasClickAction())
        onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.PaneTitle,
                "账单",
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun compactPagesCanBeChangedWithAHorizontalSwipe() = runComposeUiTest {
        setHengjiContent(
            gateway = gatewayWith(transaction("swipe", "滑动测试商户")),
            widthDp = 390,
            heightDp = 760,
        )

        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .performTouchInput { swipeLeft() }
        waitUntilExactlyOneExists(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "账单"),
        )
        onNodeWithText("滑动测试商户").assertIsDisplayed()
    }

    @Test
    fun builtInModelRunsAutomaticallyAfterThreeMonthsAndCanBeDisabled() = runComposeUiTest {
        val provider = RecordingPersonalInsightModelProvider()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val gateway = gatewayWith(
            transaction("model-test-old", "模型隐私商户", bookedOn = LocalDate(2026, 1, 1)),
            transaction("model-test", "模型隐私商户", bookedOn = today),
        )
        setHengjiContent(gateway = gateway, modelProvider = provider)

        navigateTo("智能分析")
        waitUntil { provider.calls == 1 }
        val verticalPage = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        onNode(verticalPage).performScrollToNode(hasText("为你整理的本期重点"))
        onNodeWithText("为你整理的本期重点").assertIsDisplayed()

        val observed = provider.observedContext
        assertTrue(observed.candidates.all { it.candidateKey.startsWith("candidate-") })
        assertTrue(!observed.toString().contains("模型隐私商户"))
        assertTrue(!observed.toString().contains("model-test"))
        onNode(verticalPage).performScrollToNode(hasText("关闭智能分析"))
        onNodeWithText("关闭智能分析").performClick()
        onNode(verticalPage).performScrollToNode(hasText("开启智能分析"))
        onNodeWithText("开启智能分析").assertIsDisplayed()
    }

    private fun ComposeUiTest.setHengjiContent(
        gateway: PreviewLedgerGateway,
        picker: UserImportDocumentPicker = UserImportDocumentPicker { _, _ -> null },
        writer: LedgerExportWriter = LedgerExportWriter { _, _, _ -> null },
        widthDp: Int = 900,
        heightDp: Int = 700,
        fontScale: Float = 1f,
        modelProvider: PersonalInsightModelProvider? = null,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                    HengjiApp(
                        gateway = gateway,
                        userImportDocumentPicker = picker,
                        ledgerExportWriter = writer,
                        personalInsightModelProvider = modelProvider,
                    )
                }
            }
        }
        waitForIdle()
        waitUntilExactlyOneExists(hasText("首页") and hasClickAction())
    }

    private fun ComposeUiTest.navigateTo(label: String) {
        onNode(hasText(label) and hasClickAction()).performClick()
        waitUntilExactlyOneExists(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, label),
        )
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

    private fun gatewayWith(vararg transactions: Transaction): PreviewLedgerGateway =
        PreviewLedgerGateway(InMemoryLedgerRepository(snapshotOf(*transactions)))

    private fun snapshotOf(vararg transactions: Transaction) = LedgerSnapshot(
        revision = 0,
        transactions = transactions.toList(),
        assets = emptyList(),
        maintenanceCosts = emptyList(),
        usageEvents = emptyList(),
        marketQuotes = emptyList(),
        insightPreferences = InsightPreferenceRecord(onboardingCompletedAtEpochMillis = 1),
    )

    private fun transaction(
        id: String,
        merchant: String,
        bookedOn: LocalDate = LocalDate(2026, 7, 25),
    ) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(1_299, CurrencyCode.CNY),
        bookedOn = bookedOn,
        categoryId = CategoryId("dining"),
        merchant = Merchant(merchant),
    )

    private class RecordingRestorePicker(
        private val content: String,
    ) : UserImportDocumentPicker {
        var observedFormat: ImportDocumentFormat? = null
        var observedPurpose: UserDocumentPurpose? = null

        override suspend fun pick(
            format: ImportDocumentFormat,
            purpose: UserDocumentPurpose,
        ): PickedImportDocument {
            observedFormat = format
            observedPurpose = purpose
            return PickedImportDocument(
                displayName = "自动化恢复.json",
                content = content,
                format = format,
            )
        }
    }

    private class RecordingExportWriter : LedgerExportWriter {
        val calls = mutableListOf<ExportCall>()

        override suspend fun save(
            suggestedFileName: String,
            utf8Content: String,
            mediaType: String,
        ): String? {
            calls += ExportCall(
                title = if (mediaType == "application/json") "JSON 数据导出" else "CSV 账单导出",
                suggestedFileName = suggestedFileName,
                utf8Content = utf8Content,
                mediaType = mediaType,
            )
            return null
        }
    }

    private class RecordingPersonalInsightModelProvider : PersonalInsightModelProvider {
        override val providerId: String = "自动化隐私模型"
        override val privacyReviewed: Boolean = true
        var calls: Int = 0
        lateinit var observedContext: PersonalInsightModelContext

        override suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer {
            calls += 1
            observedContext = context
            val candidate = context.candidates.first()
            return PersonalInsightModelAnswer(
                candidateKey = candidate.candidateKey,
                headline = "为你整理的本期重点",
                summary = "这段个性化表达只使用经过区间化的本机证据。",
                evidenceCodes = candidate.evidenceCodes,
                actionLabel = "查看本机明细",
            )
        }
    }

    private data class ExportCall(
        val title: String,
        val suggestedFileName: String,
        val utf8Content: String,
        val mediaType: String,
    )
}
