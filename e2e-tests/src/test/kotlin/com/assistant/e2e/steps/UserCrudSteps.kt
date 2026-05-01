package com.assistant.e2e.steps

import io.cucumber.java.en.*
import net.serenitybdd.annotations.Managed
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.abilities.BrowseTheWeb
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver

/**
 * Step definitions for 016-UserCrud.feature — Given/When steps.
 *
 * Covers E2E-UI-01 through E2E-UI-12: CRUD operations,
 * validation, status changes, delete confirmation, and regression.
 *
 * Reuses CommonSteps for auth, navigation, and generic clicks.
 * Then-assertions are in UserCrudAssertSteps.kt.
 */
class UserCrudSteps {

    @Managed(driver = "chrome")
    lateinit var driver: WebDriver

    private val actor = Actor.named("UserCrudUser")

    private fun ensureBrowse() {
        actor.can(BrowseTheWeb.with(driver))
    }

    // ── Given — Page state ──────────────────────────────────

    @Given("the admin is on the User Management page")
    fun adminOnUserManagementPage() {
        ensureBrowse()
        TestHelper.navigateTo(driver, "user_management")
        UserCrudHelper.waitForUserList(driver)
    }

    @Given("the admin has opened the {string} form")
    fun adminOpenedForm(formName: String) {
        ensureBrowse()
        TestHelper.navigateTo(driver, "user_management")
        UserCrudHelper.waitForUserList(driver)
        UserCrudHelper.clickButtonByText(driver, "Add User")
        UserCrudHelper.waitForFormVisible(driver)
    }

    @Given("the User Directory contains at least one user")
    fun directoryContainsUsers() {
        ensureBrowse()
        UserCrudHelper.waitForUserList(driver)
    }

    @Given("the admin has opened the Detail Panel for a user")
    fun adminOpenedDetailPanel() {
        ensureBrowse()
        UserCrudHelper.waitForUserList(driver)
        UserCrudHelper.clickFirstUserRow(driver)
        UserCrudHelper.waitForDetailPanel(driver)
    }

    @Given("the admin has opened the Detail Panel for an ACTIVE user")
    fun adminOpenedDetailForActiveUser() {
        adminOpenedDetailPanel()
    }

    @Given("the admin has opened the Detail Panel for a DISABLED user")
    fun adminOpenedDetailForDisabledUser() {
        adminOpenedDetailPanel()
    }

    @Given("the admin has opened the Detail Panel for a deletable user")
    fun adminOpenedDetailForDeletableUser() {
        adminOpenedDetailPanel()
        SharedTestContext.lastResponseBody = UserCrudHelper.extractDetailPanelName(driver)
    }

    @Given("the admin has opened the delete confirmation dialog")
    fun adminOpenedDeleteDialog() {
        adminOpenedDetailPanel()
        UserCrudHelper.clickButtonByText(driver, "Delete")
        UserCrudHelper.waitForDialog(driver)
    }

    @Given("the admin navigates to the User Management page")
    fun adminNavigatesToPage() {
        ensureBrowse()
        TestHelper.navigateTo(driver, "user_management")
        UserCrudHelper.waitForUserList(driver)
    }

    @Given("the admin selects a user in the User Directory")
    fun adminSelectsUser() {
        ensureBrowse()
        UserCrudHelper.waitForUserList(driver)
        UserCrudHelper.clickFirstUserRow(driver)
    }

    // ── When — Actions ──────────────────────────────────────

    @When("the admin clicks the {string} button")
    fun adminClicksButton(text: String) {
        UserCrudHelper.clickButtonByText(driver, text)
        TestHelper.waitForOverlayGone(driver)
    }

    @When("the admin fills in name {string} and email {string}")
    fun adminFillsNameAndEmail(name: String, email: String) {
        // SCRUM-50: Cleanup any leftover test user from previous runs
        // to prevent 409 Conflict on create
        UserCrudHelper.cleanupTestUserByEmail(email)
        UserCrudHelper.fillInput(driver, "name", name)
        UserCrudHelper.fillInput(driver, "email", email)
    }

    @When("the admin selects role {string}")
    fun adminSelectsRole(role: String) {
        UserCrudHelper.selectRole(driver, role)
    }

    @When("the admin leaves the name field empty")
    fun adminLeavesNameEmpty() {
        UserCrudHelper.fillInput(driver, "name", "")
    }

    @When("the admin enters email {string}")
    fun adminEntersEmail(email: String) {
        UserCrudHelper.fillInput(driver, "email", email)
    }

    @When("the admin clicks on a user row")
    fun adminClicksUserRow() {
        UserCrudHelper.clickFirstUserRow(driver)
    }

    @When("the admin changes the name to {string}")
    fun adminChangesName(newName: String) {
        UserCrudHelper.fillInput(driver, "name", newName)
    }

    @When("the confirmation dialog appears")
    fun confirmationDialogAppears() {
        UserCrudHelper.waitForDialog(driver)
    }

    @When("the admin confirms the action")
    fun adminConfirmsAction() {
        UserCrudHelper.clickButtonByText(driver, "Confirm")
        TestHelper.waitForOverlayGone(driver)
    }

    @When("the admin types the user's name in the confirmation input")
    fun adminTypesUserNameInConfirmation() {
        val name = SharedTestContext.lastResponseBody.ifBlank {
            UserCrudHelper.extractDetailPanelName(driver)
        }
        UserCrudHelper.fillInput(driver, "confirm", name)
    }

    @When("the admin clicks the confirm delete button")
    fun adminClicksConfirmDelete() {
        UserCrudHelper.clickButtonByText(driver, "Confirm")
        TestHelper.waitForOverlayGone(driver)
    }

    @When("the admin types {string} in the confirmation input")
    fun adminTypesInConfirmation(text: String) {
        UserCrudHelper.fillInput(driver, "confirm", text)
    }

    @When("the admin changes the user's role via the role dropdown")
    fun adminChangesRoleViaDropdown() {
        val selects = driver.findElements(By.cssSelector(".um-role-select, select"))
        if (selects.isNotEmpty()) {
            TestHelper.js(driver).executeScript(
                "arguments[0].value='READER'; arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                selects.first()
            )
        }
        TestHelper.waitForOverlayGone(driver)
    }

    @When("the admin toggles one of the permissions")
    fun adminTogglesPermission() {
        val toggles = driver.findElements(
            By.cssSelector("input[type='checkbox'], [role='switch'], [class*='toggle']")
        ).filter { it.isDisplayed }
        if (toggles.isNotEmpty()) {
            TestHelper.js(driver).executeScript("arguments[0].click()", toggles.first())
        }
        TestHelper.waitForOverlayGone(driver)
    }
}
