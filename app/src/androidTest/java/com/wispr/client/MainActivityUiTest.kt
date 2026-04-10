package com.wispr.client

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.wispr.client.data.ServerConfigStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsPrimaryControls() {
        composeRule.onNodeWithText("Save Config").assertIsDisplayed()
        composeRule.onNodeWithText("Check Server").assertIsDisplayed()
        composeRule.onNodeWithText("Start Recording").assertIsDisplayed()
        composeRule.onNodeWithText("Copy Transcript").assertIsDisplayed()
        composeRule.onNodeWithText("Open Keyboard Settings").assertIsDisplayed()
    }

    @Test
    fun savesServerConfigFromUi() {
        val targetUrl = "https://10.0.2.2:3000"
        composeRule.onNodeWithText("Server base URL").performTextReplacement(targetUrl)
        composeRule.onNodeWithText("Save Config").performClick()
        composeRule.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ServerConfigStore(context)
        assertEquals(targetUrl, store.getBaseUrl())
    }
}
