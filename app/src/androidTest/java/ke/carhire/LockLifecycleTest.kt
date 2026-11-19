package ke.carhire

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.domain.model.ThemePreference
import ke.carhire.security.Vault
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LockLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val lockText = "Unlock your private business workspace"

    @Before
    fun startWithALockedSession() {
        ApplicationProvider.getApplicationContext<CarHireApp>().session.lock()
    }

    private fun awaitLock(visible: Boolean) {
        compose.waitUntil(30000) {
            compose
                .onAllNodes(isRoot())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty() &&
                (compose
                    .onAllNodesWithText(lockText)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty() == visible)
        }
    }

    private fun unlock(scenario: ActivityScenario<MainActivity>) {
        awaitLock(true)
        compose.onNodeWithText("PIN").performTextReplacement("527491")
        compose.onNodeWithText("Unlock").performClick()
        awaitLock(false)
        hideKeyboard(scenario)
        compose.onNodeWithText("Fleet overview").assertIsDisplayed()
    }

    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val keyboard =
                activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(10000) {
            var visible = false
            scenario.onActivity { activity ->
                visible =
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            !visible
        }
    }

    private fun shell(command: String) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    private fun openNavigation() {
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("workspace-navigation").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTheme(name: String) {
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Current theme: $name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(name).assertIsSelected()
    }

    @Test
    fun hamburgerMenuReachesLowerSectionsAndDismissesWithoutLosingDrafts() {
        Vault(ApplicationProvider.getApplicationContext<Context>()).setPin("527491")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("draft")
            openNavigation()
            compose
                .onNode(hasText("Home") and hasAnyAncestor(hasTestTag("workspace-navigation")))
                .assertIsSelected()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("Close navigation menu").assertDoesNotExist()
            compose.onNodeWithText("draft").assertExists()
            openNavigation()
            compose
                .onNode(hasText("Home") and hasAnyAncestor(hasTestTag("workspace-navigation")))
                .performClick()
            compose.onNodeWithText("draft").assertExists()

            for ((section, title) in
                listOf(
                    "Audit" to "Audit",
                    "Customers" to "Customers",
                    "Hires" to "Hires",
                    "Settings" to "Settings",
                )) {
                openNavigation()
                compose.onNodeWithTag("workspace-navigation").performScrollToNode(hasText(section))
                compose
                    .onNode(hasText(section) and hasAnyAncestor(hasTestTag("workspace-navigation")))
                    .performClick()
                compose.onNodeWithContentDescription("Close navigation menu").assertDoesNotExist()
                compose.onNodeWithTag("workspace-page-title").assertTextEquals(title)
            }
            compose.onNodeWithText("Appearance").assertIsDisplayed()
        }
    }

    @Test
    fun overflowSettingsAppliesAndPersistsLightAndDarkThemes() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        val original = app.appearance.theme.value
        Vault(app).setPin("527491")
        try {
            runBlocking { app.appearance.setTheme(ThemePreference.System) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                unlock(scenario)
                openSettings()
                compose.onNodeWithText("Dark").performClick()
                awaitTheme("Dark")
                org.junit.Assert.assertEquals(
                    ThemePreference.Dark,
                    AppearancePreferences(app).theme.value,
                )
                scenario.recreate()
                awaitLock(false)
                compose.onNodeWithText("PIN").assertDoesNotExist()
                openSettings()
                awaitTheme("Dark")
                compose.onNodeWithText("Light").performClick()
                awaitTheme("Light")
                org.junit.Assert.assertEquals(
                    ThemePreference.Light,
                    AppearancePreferences(app).theme.value,
                )
                compose.onNodeWithText("System default").performClick()
                compose.waitUntil(10000) { app.appearance.theme.value == ThemePreference.System }
                compose.onNodeWithText("System default").assertIsSelected()
                compose
                    .onAllNodes(hasScrollAction())
                    .onLast()
                    .performScrollToNode(hasText("Configure business settings"))
                compose.onNodeWithText("Configure business settings").performClick()
                compose
                    .onAllNodes(hasScrollAction())
                    .onLast()
                    .performScrollToNode(hasText("Cancel"))
                compose.onNodeWithText("Cancel").performClick()
                compose.onNodeWithText("Appearance").assertIsDisplayed()
            }
        } finally {
            runBlocking { app.appearance.setTheme(original) }
        }
    }

    @Test
    fun backgroundRecreationAndRelaunchKeepTheProcessUnlocked() {
        Vault(ApplicationProvider.getApplicationContext<Context>()).setPin("527491")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("draft")
            compose.onNodeWithText("draft").assertExists()
            repeat(2) {
                shell("input keyevent 3")
                compose.waitUntil(10000) { scenario.state == Lifecycle.State.CREATED }
                shell("am start -W -f 0x30000000 -n ke.carhire/.MainActivity")
                compose.waitUntil(10000) { scenario.state == Lifecycle.State.RESUMED }
                awaitLock(false)
                compose.onNodeWithText("PIN").assertDoesNotExist()
                compose.onNodeWithText("draft").assertIsDisplayed()
            }
            scenario.recreate()
            awaitLock(false)
            compose.onNodeWithText("PIN").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitLock(false)
            compose.onNodeWithText("PIN").assertDoesNotExist()
            compose.onNodeWithText("Fleet overview").assertIsDisplayed()
        }
    }

    private fun chooseTime(field: String, hour: String) {
        compose.onNodeWithTag("record-editor").performScrollToNode(hasTestTag("instant-$field"))
        compose.onNodeWithTag("instant-$field").performClick()
        compose.onNodeWithText("Next: time").performClick()
        val inputs =
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("date-time-clock")))
        inputs.onFirst().performTextReplacement(hour)
        inputs.onLast().performTextReplacement("00")
        compose.onNodeWithText("Use date and time").performClick()
    }

    @Test
    fun datePickersSaveAHireInUtcWithoutEnteringATimezone() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        Vault(app).setPin("527491")
        val vehicle = uuid()
        val customer = uuid()
        val registration = "SAVE" + vehicle.replace("-", "").take(8).uppercase()
        val customerName = "Hire customer $customer"
        runBlocking {
            app.repository.save(
                "vehicles",
                vehicle,
                buildJsonObject {
                    put("registration", registration)
                    put("make", "Toyota")
                    put("model", "Axio")
                    put("status", "Available")
                },
            )
            app.repository.save(
                "customers",
                customer,
                buildJsonObject {
                    put("name", customerName)
                    put("phone", "0700000000")
                },
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            openNavigation()
            compose.onNodeWithTag("workspace-navigation").performScrollToNode(hasText("Hires"))
            compose
                .onNode(hasText("Hires") and hasAnyAncestor(hasTestTag("workspace-navigation")))
                .performClick()
            compose.onNodeWithText("+ Add").performClick()
            compose.onNodeWithText("Vehicle: Choose").performClick()
            compose.onNodeWithText(registration).performClick()
            compose.onNodeWithText("Customer: Choose").performClick()
            compose.onNodeWithText(customerName).performClick()
            chooseTime("plannedStart", "10")
            chooseTime("plannedEnd", "12")
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasText("Agreed charge (KES)"))
            compose.onNodeWithText("Agreed charge (KES)").performTextReplacement("2500")
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasText("Save") and hasClickAction())
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("record-editor").fetchSemanticsNodes().isEmpty()
            }
            val saved = runBlocking {
                app.repository.dao.all().single { row ->
                    row.kind == "hires" && row.data().text("vehicleId") == vehicle
                }
            }
            org.junit.Assert.assertTrue(saved.data().text("plannedStart").endsWith("T07:00:00Z"))
            org.junit.Assert.assertTrue(saved.data().text("plannedEnd").endsWith("T09:00:00Z"))
            org.junit.Assert.assertFalse(saved.data().containsKey("actualStart"))
            org.junit.Assert.assertFalse(saved.data().containsKey("actualEnd"))
            compose.onNodeWithText("Search records").performTextReplacement(vehicle)
            hideKeyboard(scenario)
            compose.onNodeWithText("Open").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("record-editor").fetchSemanticsNodes().isNotEmpty()
            }
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedStart"))
            compose
                .onNodeWithTag("instant-plannedStart")
                .assertTextContains("10:00", substring = true)
        }
    }
}
