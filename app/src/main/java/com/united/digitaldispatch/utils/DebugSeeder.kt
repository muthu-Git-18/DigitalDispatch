package com.united.digitaldispatch.utils

import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.ModuleType
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity

/**
 * TEMPORARY. The sync API that normally populates organization_table /
 * user_master_table isn't ready yet, so there's no other way to exercise
 * the login validation end-to-end. This seeds one organization + one user
 * per module, ONLY if the tables are empty.
 *
 * When the real sync API is wired in, delete this class and the one call
 * to [seedIfEmpty] from [com.united.digitaldispatch.Login.LoginActivity].
 * Nothing else in the login flow depends on this file.
 *
 * Flip [ENABLED] to false any time to stop seeding without deleting the file.
 */
object DebugSeeder {

    private const val ENABLED = true

    /** password for every seeded test user is "test123" */
    private const val TEST_PASSWORD = "test123"

    fun seedIfEmpty(db: AppDatabase) {
        if (!ENABLED) return

        val orgDao = db.organizationDao()
        val userDao = db.userDao()

        if (orgDao.count() > 0 || userDao.count() > 0) return

        val organizations = ModuleType.ALL.map { module ->
            OrganizationEntity(
                organizationCode = "${module}001",
                organizationName = "$module Test Location",
                organizationType = module,
                organizationAddress = null,
                variety = null
            )
        }
        orgDao.insertAll(organizations)

        val users = ModuleType.ALL.map { module ->
            UserMasterEntity(
                employeeCode = "${module.lowercase()}user",
                userName = "$module Test User",
                passwordHash = PasswordUtils.hash(TEST_PASSWORD),
                organizationCode = "${module}001",
                userRights = "ALL"
            )
        }
        userDao.insertAll(users)
    }
}
