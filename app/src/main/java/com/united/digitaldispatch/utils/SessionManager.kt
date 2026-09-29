package com.united.digitaldispatch.utils

import android.content.Context

class SessionManager(context: Context) {

    private val preferences =
        context.getSharedPreferences("digital_dispatch_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMPLOYEE_CODE = "employee_code"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ORGANIZATION_CODE = "organization_code"
        private const val KEY_MODULE_TYPE = "module_type"
        private const val KEY_USER_RIGHTS = "user_rights"
    }

    fun saveSession(
        employeeCode: String,
        userName: String?,
        organizationCode: String,
        moduleType: String,
        userRights: String?,
        userId: String? = null
    ) {
        preferences.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMPLOYEE_CODE, employeeCode)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_ORGANIZATION_CODE, organizationCode)
            .putString(KEY_MODULE_TYPE, moduleType)
            .putString(KEY_USER_RIGHTS, userRights)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return preferences.getBoolean(KEY_LOGGED_IN, false)
    }

    fun getUserId(): String? {
        return preferences.getString(KEY_USER_ID, null)
    }

    fun getEmployeeCode(): String? {
        return preferences.getString(KEY_EMPLOYEE_CODE, null)
    }

    fun getUserName(): String? {
        return preferences.getString(KEY_USER_NAME, null)
    }

    fun getOrganizationCode(): String? {
        return preferences.getString(KEY_ORGANIZATION_CODE, null)
    }

    fun getModuleType(): String? {
        return preferences.getString(KEY_MODULE_TYPE, null)
    }

    fun getUserRights(): String? {
        return preferences.getString(KEY_USER_RIGHTS, null)
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }
}