package com.united.digitaldispatch.Login

/**
 * Field-level validation for the login screen, ported from the WH/PPD
 * LoginActivity's `onClick` if/else-if chain — same rules, just isolated
 * out of the Activity so they're readable and (if you ever add tests)
 * testable without touching Android classes:
 *
 *   - employee id required, length 3..15   (old: R.string.valid_user_name)
 *   - password required, length 2..15      (old: R.string.valid_user_password)
 *   - module (TAP/PPD/GLT/WH) must be selected
 *   - organization must be selected
 *
 * Returns the FIRST failing rule only, same as the old chain, so the user
 * sees one message at a time instead of a wall of errors.
 */
object LoginValidator {

    fun validate(
        employeeId: String,
        password: String,
        selectedModule: String?,
        selectedOrganizationCode: String?
    ): String? {
        if (employeeId.isBlank()) {
            return "Please enter your Employee / Partner ID"
        }
        if (employeeId.length < 3 || employeeId.length > 15) {
            return "Employee / Partner ID must be between 3 and 15 characters"
        }
        if (password.isBlank()) {
            return "Please enter your password"
        }
        if (password.length < 2 || password.length > 15) {
            return "Password must be between 2 and 15 characters"
        }
        if (selectedModule.isNullOrBlank()) {
            return "Please select a module"
        }
        if (selectedOrganizationCode.isNullOrBlank()) {
            return "Please select an organization"
        }
        return null // no errors
    }
}
