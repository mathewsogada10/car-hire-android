package ke.carhire

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.domain.model.ThemePreference
import ke.carhire.security.Vault
import ke.carhire.ui.editor.Forms
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LockLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val lockText = "Unlock your private business workspace"
    private val originalZone = TimeZone.getDefault()

    @Before
    fun startWithALockedSession() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"))
        ApplicationProvider.getApplicationContext<CarHireApp>().session.lock()
    }

    @After
    fun restoreDeviceZone() {
        TimeZone.setDefault(originalZone)
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
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    private fun awaitFinancialCalculation() {
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Calculating monthly trend...")
                .fetchSemanticsNodes()
                .isEmpty()
        }
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

    private fun awaitDashboardAmount(key: String, amount: Long) {
        val expected = ke.carhire.ui.components.money(amount)
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("dashboard-$key").fetchSemanticsNodes().any { node ->
                node.config[SemanticsProperties.Text].any { it.text == expected }
            }
        }
        compose.onNodeWithTag("dashboard-$key").assertTextEquals(expected)
    }

    @Test
    fun hamburgerMenuReachesLowerSectionsAndDismissesWithoutLosingDrafts() {
        Vault(ApplicationProvider.getApplicationContext<Context>()).setPin("527491")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("draft")
            openNavigation()
            compose.onNodeWithText("Hire calendar").assertDoesNotExist()
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
    fun financialYearChartRetainsDatesAndReturnsHomeWithBack() {
        Vault(ApplicationProvider.getApplicationContext<Context>()).setPin("527491")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            openNavigation()
            compose
                .onNodeWithTag("workspace-navigation")
                .performScrollToNode(hasText("Financial report"))
            compose
                .onNode(
                    hasText("Financial report") and
                        hasAnyAncestor(hasTestTag("workspace-navigation"))
                )
                .performClick()
            compose.onNodeWithTag("workspace-page-title").assertTextEquals("Financial report")
            compose
                .onNode(hasText("From YYYY-MM-DD") and hasSetTextAction())
                .performTextReplacement("2025-07-01")
            compose
                .onNode(hasText("Through YYYY-MM-DD") and hasSetTextAction())
                .performTextReplacement("2026-06-30")
            hideKeyboard(scenario)
            compose
                .onNodeWithTag("financial-report")
                .performScrollToNode(
                    hasContentDescription(
                        "Monthly line graph for 2025-07 to 2026-06.",
                        substring = true,
                    )
                )
            val labels =
                listOf(
                    "Payments received, net refunds (solid)",
                    "Paid expenses (dashed)",
                    "Net profit, cash basis (dotted)",
                )
            var currentMask = 7
            for (mask in listOf(3, 5, 6, 1, 2, 4, 0, 1)) {
                labels.forEachIndexed { index, label ->
                    val checked = mask and (1 shl index) != 0
                    compose
                        .onNodeWithTag("financial-report")
                        .performScrollToNode(hasText(label) and isToggleable())
                    val checkbox = compose.onNode(hasText(label) and isToggleable())
                    if ((currentMask xor mask) and (1 shl index) != 0) checkbox.performClick()
                    if (checked) checkbox.assertIsOn() else checkbox.assertIsOff()
                }
                currentMask = mask
                if (mask == 0) {
                    compose.onNodeWithTag("financial-line-chart").assertDoesNotExist()
                    compose
                        .onNodeWithTag("financial-report")
                        .performScrollToNode(
                            hasText("Select at least one line to display the chart.")
                        )
                    compose
                        .onNodeWithText("Select at least one line to display the chart.")
                        .assertIsDisplayed()
                    compose
                        .onNodeWithTag("financial-report")
                        .performScrollToNode(hasText("2026-06"))
                    compose.onNodeWithText("2026-06").assertIsDisplayed()
                } else {
                    compose
                        .onNodeWithTag("financial-report")
                        .performScrollToNode(hasTestTag("financial-line-chart"))
                    val visible = labels.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                    compose
                        .onNodeWithTag("financial-line-chart")
                        .assert(
                            hasContentDescription(
                                "Visible lines: ${visible.joinToString("; ")}.",
                                substring = true,
                            )
                        )
                }
            }
            compose
                .onNodeWithTag("financial-report")
                .performScrollToNode(hasText("From YYYY-MM-DD"))
            compose
                .onNode(hasText("From YYYY-MM-DD") and hasSetTextAction())
                .performTextReplacement("2025-08-01")
            hideKeyboard(scenario)
            awaitFinancialCalculation()
            compose
                .onNodeWithTag("financial-report")
                .performScrollToNode(
                    hasContentDescription(
                        "Monthly line graph for 2025-08 to 2026-06.",
                        substring = true,
                    )
                )
            compose
                .onNodeWithTag("financial-line-chart")
                .assert(
                    hasContentDescription("Visible lines: ${labels.first()}.", substring = true)
                )
            compose.onNodeWithTag("financial-report").performScrollToNode(hasText("2026-06"))
            compose.onNodeWithText("2026-06").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("workspace-page-title").assertTextEquals("Financial report")
            awaitFinancialCalculation()
            labels.forEachIndexed { index, label ->
                compose
                    .onNodeWithTag("financial-report")
                    .performScrollToNode(hasText(label) and isToggleable())
                val checkbox = compose.onNode(hasText(label) and isToggleable())
                if (index == 0) checkbox.assertIsOn() else checkbox.assertIsOff()
            }
            compose
                .onNodeWithTag("financial-report")
                .performScrollToNode(hasText("From YYYY-MM-DD"))
            compose
                .onNode(hasText("From YYYY-MM-DD") and hasSetTextAction())
                .assertTextContains("2025-08-01")
            hideKeyboard(scenario)
            compose.waitUntil(10000) {
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() }
                focused
            }
            shell("input keyevent 4")
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("Fleet overview").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fleet overview").assertIsDisplayed()
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
        compose.onNodeWithTag("calendar-$field").performClick()
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
                .performScrollToNode(hasTestTag("instant-plannedStart"))
            compose
                .onNodeWithTag("instant-plannedStart")
                .performTextReplacement("${LocalDate.now(ZoneId.of("Africa/Nairobi"))}T10:30")
            hideKeyboard(scenario)
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
            org.junit.Assert.assertTrue(saved.data().text("plannedStart").endsWith("T07:30:00Z"))
            org.junit.Assert.assertTrue(saved.data().text("plannedEnd").endsWith("T09:00:00Z"))
            org.junit.Assert.assertEquals(
                saved.data().text("plannedStart"),
                saved.data().text("actualStart"),
            )
            org.junit.Assert.assertEquals(
                saved.data().text("plannedEnd"),
                saved.data().text("actualEnd"),
            )
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
                .assertTextContains("10:30", substring = true)
        }
    }

    private data class HireFixture(val hire: String, val vehicle: String, val registration: String)

    private fun seedHireFixture(
        app: CarHireApp,
        status: String = "Booked",
        notes: String = "",
    ): HireFixture {
        val vehicle = uuid()
        val customer = uuid()
        val hire = uuid()
        val registration = "PAY" + vehicle.replace("-", "").take(8).uppercase()
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
                    put("name", "Payment fixture $customer")
                    put("phone", "0700000000")
                },
            )
            app.repository.save(
                "hires",
                hire,
                buildJsonObject {
                    put("vehicleId", vehicle)
                    put("customerId", customer)
                    put("plannedStart", "2026-12-01T07:00:00Z")
                    put("plannedEnd", "2026-12-02T09:00:00Z")
                    put("status", status)
                    put("notes", notes)
                    put("chargeMinor", 250000)
                },
            )
        }
        return HireFixture(hire, vehicle, registration)
    }

    @Test
    fun paymentHireChoicesShowRegistrationAndLocalTimeWithTypedAndCalendarDates() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        Vault(app).setPin("527491")
        val fixture = seedHireFixture(app)
        val hire = fixture.hire
        val registration = fixture.registration
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            openNavigation()
            compose
                .onNodeWithTag("workspace-navigation")
                .performScrollToNode(hasText("Payments & deposits"))
            compose
                .onNode(
                    hasText("Payments & deposits") and
                        hasAnyAncestor(hasTestTag("workspace-navigation"))
                )
                .performClick()
            compose.onNodeWithText("+ Add").performClick()
            compose.onNodeWithText("Search Hire").performTextReplacement(registration.lowercase())
            hideKeyboard(scenario)
            compose.onNodeWithText("Hire: Choose").performClick()
            compose
                .onNode(hasText(registration, substring = true) and hasClickAction())
                .assertTextContains("10:00", substring = true)
                .performClick()
            compose.onNodeWithText("Search Hire").performTextReplacement("no matching vehicle")
            hideKeyboard(scenario)
            compose
                .onNode(
                    hasText("Hire: ", substring = true) and
                        hasText(registration, substring = true) and
                        hasClickAction()
                )
                .assertIsDisplayed()
            compose.onNodeWithTag("record-editor").performScrollToNode(hasText("Amount (KES)"))
            compose.onNodeWithText("Amount (KES)").performTextReplacement("1250")
            hideKeyboard(scenario)
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasText("Business date (YYYY-MM-DD)"))
            compose
                .onNodeWithText("Business date (YYYY-MM-DD)")
                .performTextReplacement("2026-12-03")
            hideKeyboard(scenario)
            compose
                .onNodeWithContentDescription("Choose Business date (YYYY-MM-DD) date")
                .performClick()
            compose
                .onNode(hasText("December 4", substring = true) and hasClickAction())
                .performClick()
            compose.onNodeWithText("Use date").performClick()
            compose.onNodeWithText("Business date (YYYY-MM-DD)").assertTextContains("2026-12-04")
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasText("Save") and hasClickAction())
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("record-editor").fetchSemanticsNodes().isEmpty()
            }
            val saved = runBlocking {
                app.repository.dao.all().single {
                    it.kind == "payments" && it.data().text("hireId") == hire
                }
            }
            org.junit.Assert.assertEquals("2026-12-04", saved.data().text("date"))
            org.junit.Assert.assertEquals("125000", saved.data().text("amountMinor"))
            val updatedHire = runBlocking { requireNotNull(app.repository.dao.find(hire)) }.data()
            org.junit.Assert.assertEquals(
                updatedHire.text("plannedStart"),
                updatedHire.text("actualStart"),
            )
            org.junit.Assert.assertEquals(
                updatedHire.text("plannedEnd"),
                updatedHire.text("actualEnd"),
            )
        }
    }

    @Test
    fun changingDeviceTimezoneRefreshesAnOpenEditorWithoutChangingTheStoredInstant() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        Vault(app).setPin("527491")
        val fixture = seedHireFixture(app)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            openNavigation()
            compose.onNodeWithTag("workspace-navigation").performScrollToNode(hasText("Hires"))
            compose
                .onNode(hasText("Hires") and hasAnyAncestor(hasTestTag("workspace-navigation")))
                .performClick()
            compose.onNodeWithText("Search records").performTextReplacement(fixture.vehicle)
            hideKeyboard(scenario)
            compose.onNodeWithText("Open").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("record-editor").fetchSemanticsNodes().isNotEmpty()
            }
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedStart"))
            compose.onNodeWithTag("instant-plannedStart").assertTextContains("2026-12-01T10:00")
            scenario.moveToState(Lifecycle.State.CREATED)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10000) {
                compose
                    .onNodeWithTag("instant-plannedStart")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText]
                    .text == "2026-11-30T23:00"
            }
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasText("Save") and hasClickAction())
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("record-editor").fetchSemanticsNodes().isEmpty()
            }
            val saved = runBlocking { app.repository.dao.all().single { it.id == fixture.hire } }
            org.junit.Assert.assertEquals("2026-12-01T07:00:00Z", saved.data().text("plannedStart"))
        }
    }

    @Test
    fun dashboardRegistrationSelectionFiltersResultsAndCanReturnToAllVehicles() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        Vault(app).setPin("527491")
        val first = seedHireFixture(app)
        val second = seedHireFixture(app)
        val today = LocalDate.now(ZoneId.of("Africa/Nairobi"))
        val category = uuid()
        runBlocking {
            app.repository.save(
                "categories",
                category,
                buildJsonObject { put("name", "Dashboard $category") },
            )
            listOf(first to 11100L, second to 22200L).forEach { (fixture, amount) ->
                app.repository.save(
                    "expenses",
                    uuid(),
                    buildJsonObject {
                        put("vehicleId", fixture.vehicle)
                        put("categoryId", category)
                        put("name", "Dashboard fixture expense")
                        put("date", today.toString())
                        put("paidDate", today.toString())
                        put("status", "Paid")
                        put("amountMinor", amount)
                    },
                )
            }
        }
        val total = runBlocking {
            ke.carhire.domain.rules.Rules.financials(
                    app.repository.dao.all(),
                    today.withDayOfYear(1),
                    today,
                )
                .getValue("cashExpensesMinor")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            awaitDashboardAmount("cashExpensesMinor", total)
            compose
                .onNodeWithTag("dashboard-vehicle-search")
                .performScrollTo()
                .performTextReplacement(first.vehicle)
            hideKeyboard(scenario)
            compose
                .onNodeWithText("No matching registrations. The report filter has not changed.")
                .performScrollTo()
                .assertIsDisplayed()
            awaitDashboardAmount("cashExpensesMinor", total)
            listOf(first to 11100L, second to 22200L).forEach { (fixture, expected) ->
                val query =
                    " ${fixture.registration.take(3).lowercase()} ${fixture.registration.drop(3).lowercase()} "
                compose
                    .onNodeWithTag("dashboard-vehicle-search")
                    .performScrollTo()
                    .performTextReplacement(query)
                hideKeyboard(scenario)
                compose
                    .onNodeWithTag("dashboard-vehicle-${fixture.vehicle}")
                    .performScrollTo()
                    .performClick()
                compose
                    .onNodeWithTag("dashboard-selected-vehicle")
                    .assertTextEquals("Report vehicle: ${fixture.registration}")
                awaitDashboardAmount("cashExpensesMinor", expected)
                compose
                    .onNodeWithTag("dashboard-vehicle-search")
                    .performScrollTo()
                    .performTextReplacement("ZZZ no match")
                hideKeyboard(scenario)
                awaitDashboardAmount("cashExpensesMinor", expected)
            }
            compose.onNodeWithText("All vehicles").performScrollTo().performClick()
            compose
                .onNodeWithTag("dashboard-selected-vehicle")
                .assertTextEquals("Report vehicle: All vehicles")
            awaitDashboardAmount("cashExpensesMinor", total)
        }
    }

    @Test
    fun systemBackRestoresSectionsFiltersAndDraftsBeforeLeavingHome() {
        Vault(ApplicationProvider.getApplicationContext<Context>()).setPin("527491")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            fun navigate(section: String) {
                openNavigation()
                compose.onNodeWithTag("workspace-navigation").performScrollToNode(hasText(section))
                compose
                    .onNode(hasText(section) and hasAnyAncestor(hasTestTag("workspace-navigation")))
                    .performClick()
                compose.onNodeWithTag("workspace-page-title").assertTextEquals(section)
            }
            fun back() {
                compose.waitForIdle()
                shell("input keyevent 4")
                compose.waitForIdle()
            }
            navigate("Customers")
            compose
                .onNodeWithText("Search records")
                .performTextReplacement("retained customer filter")
            hideKeyboard(scenario)
            navigate("Hires")
            compose.onNodeWithText("+ Add").performClick()
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedStart"))
            compose.onNodeWithTag("instant-plannedStart").performTextReplacement("2026-12-01T10:00")
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedEnd"))
            compose.onNodeWithTag("instant-plannedEnd").performTextReplacement("2026-12-02T12:00")
            hideKeyboard(scenario)
            compose.onNodeWithTag("hire-days").performScrollTo().assertTextContains("2")
            openNavigation()
            back()
            compose.onNodeWithContentDescription("Close navigation menu").assertDoesNotExist()
            compose.onNodeWithTag("record-editor").assertExists()
            navigate("Expenses")
            back()
            compose.onNodeWithTag("record-editor").assertExists()
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedStart"))
            compose.onNodeWithTag("instant-plannedStart").assertTextContains("2026-12-01T10:00")
            scenario.recreate()
            compose
                .onNodeWithTag("record-editor")
                .performScrollToNode(hasTestTag("instant-plannedEnd"))
            compose.onNodeWithTag("instant-plannedEnd").assertTextContains("2026-12-02T12:00")
            hideKeyboard(scenario)
            back()
            compose.onNodeWithTag("workspace-page-title").assertTextEquals("Hires")
            back()
            compose.onNodeWithTag("workspace-page-title").assertTextEquals("Customers")
            compose.onNodeWithText("Search records").assertTextContains("retained customer filter")
            back()
            compose.onNodeWithText("Fleet overview").assertIsDisplayed()
            org.junit.Assert.assertEquals(Lifecycle.State.RESUMED, scenario.state)
            back()
            compose.waitUntil(10000) { scenario.state != Lifecycle.State.RESUMED }
        }
    }

    @Test
    fun hireStatusFilterUsesDefinedChoicesAndCanBeCleared() {
        val app = ApplicationProvider.getApplicationContext<CarHireApp>()
        Vault(app).setPin("527491")
        val marker = uuid()
        val booked = seedHireFixture(app, "Booked", marker)
        val draft = seedHireFixture(app, "Draft", marker)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlock(scenario)
            openNavigation()
            compose.onNodeWithTag("workspace-navigation").performScrollToNode(hasText("Hires"))
            compose
                .onNode(hasText("Hires") and hasAnyAncestor(hasTestTag("workspace-navigation")))
                .performClick()
            compose.onNodeWithText("Search records").performTextReplacement(marker)
            hideKeyboard(scenario)
            compose.onNodeWithTag("record-status-filter").performClick()
            (listOf("All statuses") +
                    Forms.fields.getValue("hires").single { it.key == "status" }.options)
                .forEach { value ->
                    compose.onNode(hasText(value) and hasClickAction()).assertExists()
                }
            compose.onNode(hasText("Booked") and hasClickAction()).performClick()
            compose.onNodeWithTag("record-status-filter").assertTextContains("Status: Booked")
            compose
                .onNodeWithTag("record-list")
                .performScrollToNode(hasTestTag("record-${booked.hire}"))
            compose.onNodeWithTag("record-${booked.hire}").assertIsDisplayed()
            compose
                .onNodeWithTag("filtered-total")
                .assertTextContains("KES 2,500.00", substring = true)
            compose.onNodeWithTag("record-${draft.hire}").assertDoesNotExist()
            compose.onNodeWithTag("record-status-filter").performClick()
            compose.onNode(hasText("Completed") and hasClickAction()).performClick()
            compose
                .onNodeWithText("No matching records. Add one or adjust filters.")
                .assertIsDisplayed()
            compose.onNodeWithTag("filtered-total").assertTextContains("KES 0.00", substring = true)
            compose.onNodeWithTag("record-status-filter").performClick()
            compose.onNode(hasText("All statuses") and hasClickAction()).performClick()
            compose
                .onNodeWithTag("filtered-total")
                .assertTextContains("KES 5,000.00", substring = true)
            listOf(booked, draft).forEach { fixture ->
                compose
                    .onNodeWithTag("record-list")
                    .performScrollToNode(hasTestTag("record-${fixture.hire}"))
                compose.onNodeWithTag("record-${fixture.hire}").assertIsDisplayed()
            }
        }
    }
}
