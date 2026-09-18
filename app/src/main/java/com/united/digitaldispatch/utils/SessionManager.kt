package com.united.digitaldispatch.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Replaces the scattered SharedPreferences reads/writes the old apps did
 * directly inside LoginActivity/LoginPresenter (PreferenceHelper.kt) with
 * one small typed wrapper. Only stores what the unified app actually needs
 * post-login: who's logged in, which module they picked, and which org.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        employeeCode: String,
        userName: String?,
        organizationCode: String,
        moduleType: String,
        userRights: String?
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_EMPLOYEE_CODE, employeeCode)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_ORG_CODE, organizationCode)
            .putString(KEY_MODULE_TYPE, moduleType)
            .putString(KEY_USER_RIGHTS, userRights)
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getModuleType(): String? = prefs.getString(KEY_MODULE_TYPE, null)

    fun getOrganizationCode(): String? = prefs.getString(KEY_ORG_CODE, null)

    fun getEmployeeCode(): String? = prefs.getString(KEY_EMPLOYEE_CODE, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getUserRights(): String? = prefs.getString(KEY_USER_RIGHTS, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "digital_dispatch_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_EMPLOYEE_CODE = "employee_code"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ORG_CODE = "org_code"
        private const val KEY_MODULE_TYPE = "module_type"
        private const val KEY_USER_RIGHTS = "user_rights"
    }
}
