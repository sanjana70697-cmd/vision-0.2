package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VISION", appName)
  }

  @Test
  fun `navigate across all four VISION screens and open ALFRED hub`() {
    composeTestRule.setContent {
      VisionApp()
    }
    composeTestRule.waitForIdle()

    // 1. Dashboard
    composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

    // 2. Alarm
    composeTestRule.onNodeWithTag("nav_tab_alarm").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("alarm_screen").assertIsDisplayed()

    // 3. Distraction
    composeTestRule.onNodeWithTag("nav_tab_distraction").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("distraction_screen").assertIsDisplayed()

    // 4. Report
    composeTestRule.onNodeWithTag("nav_tab_report").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("report_screen").assertIsDisplayed()

    // 5. Shared ALFRED Hub
    composeTestRule.onNodeWithTag("central_ai_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("alfred_assistant_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("alfred_mic_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("alfred_command_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("alfred_tools_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("alfred_next_task_card").assertIsDisplayed()
  }
}
