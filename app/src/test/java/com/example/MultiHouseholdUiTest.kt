package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.HomeListDatabase
import com.example.data.HomeListRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MultiHouseholdUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun createsSwitchesAndLeavesHouseholds() {
        fun waitTag(tag: String) {
            ui.waitUntil(timeoutMillis = 15000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        }
        fun waitText(text: String) {
            ui.waitUntil(timeoutMillis = 15000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
        fun create(name: String) {
            waitTag("found_family_button")
            ui.onNodeWithTag("found_family_button").performScrollTo().performClick()
            ui.onNodeWithTag("household_name_input").performTextInput(name)
            ui.onNodeWithTag("founder_name_input").performTextInput("Alex")
            ui.onNodeWithTag("create_family_submit_button").performScrollTo().performClick()
            waitText("$name ▾")
        }
        create("Fixture House A")
        ui.onNodeWithTag("primary_add_fab").performClick()
        ui.onNodeWithTag("fab_menu_chore").performClick()
        waitTag("chore_title_input")
        ui.onNodeWithTag("chore_title_input").performTextInput("Wash dishes")
        ui.onNodeWithTag("save_chore_button").performScrollTo().performClick()
        ui.onNodeWithTag("nav_tab_shopping").performClick()
        waitTag("grocery_item_input")
        ui.onNodeWithTag("grocery_item_input").performTextInput("Milk")
        ui.onNodeWithTag("add_grocery_button").performClick()
        waitText("Milk")

        ui.onNodeWithText("Fixture House A ▾").performClick()
        ui.onNodeWithText("+ Add / Join Household").performClick()
        create("Fixture House B")
        ui.onNodeWithTag("nav_tab_shopping").performClick()
        waitTag("grocery_item_input")
        ui.onNodeWithText("Milk").assertDoesNotExist()

        ui.onNodeWithText("Fixture House B ▾").performClick()
        ui.onNodeWithText("Fixture House A").performClick()
        waitText("Fixture House A ▾")
        ui.onNodeWithTag("nav_tab_shopping").performClick()
        waitText("Milk")
        ui.onNodeWithText("Milk").assertIsDisplayed()
        val repository = HomeListRepository(HomeListDatabase.getInstance(ui.activity))
        runBlocking { assertEquals("Wash dishes", repository.getAllChores().single().title) }

        ui.onNodeWithText("Fixture House A ▾").performClick()
        ui.onNodeWithText("Fixture House B").performClick()
        waitText("Fixture House B ▾")
        ui.onNodeWithTag("top_bar_household_button").performClick()
        waitTag("leave_household_button")
        ui.onNodeWithTag("leave_household_button").performScrollTo().performClick()
        waitText("Leave Fixture House B?")
        ui.onNodeWithTag("confirm_leave_household").performClick()
        waitText("Fixture House A ▾")
        runBlocking {
            assertEquals(listOf("Fixture House A"), repository.getAllHouseholds().map { it.householdName })
            assertEquals("Milk", repository.getAllGroceryItems().single().name)
        }
    }
}
