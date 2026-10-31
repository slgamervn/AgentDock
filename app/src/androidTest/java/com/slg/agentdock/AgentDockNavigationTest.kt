
package com.slg.agentdock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentDockNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationOpensTabsAndSettings() {
        composeRule.onNodeWithTag("navigation.tabs").performClick()
        composeRule.onNodeWithTag("tabs.screen").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation.settings").performClick()
        composeRule.onNodeWithTag("settings.screen").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation.home").performClick()
        composeRule.onNodeWithTag("home.screen").assertIsDisplayed()
    }

    @Test
    fun savingDraftOpensWorkspaceWithLocalStatus() {
        composeRule.onNodeWithTag("home.taskInput").performTextInput("Prepare a small project plan")
        composeRule.onNodeWithTag("home.saveDraft").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("workspace.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace.taskInput").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace.localDraftStatus").assertIsDisplayed()
    }
}
