package com.atruedev.kmpble.quickstart

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QuickstartScreenFakeUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun goldenPath_scanConnectObserveDisconnect() {
        composeTestRule.setContent { App(ble = FakeQuickstartBle) }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(QuickstartTestTags.SCAN_ROW)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(QuickstartTestTags.SCAN_ROW).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasText("BPM", substring = true) and hasText("Value:", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(QuickstartTestTags.VALUE).assertIsDisplayed()

        composeTestRule.onNodeWithTag(QuickstartTestTags.DISCONNECT).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(QuickstartTestTags.NEARBY)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
