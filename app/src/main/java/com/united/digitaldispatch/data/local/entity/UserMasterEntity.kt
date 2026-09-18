package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local (offline-first) copy of a user record, trimmed from the old
 * `UserMasterData` model. Populated from the sync API once it's ready;
 * until then, seeded via [com.united.digitaldispatch.utils.DebugSeeder]
 * (debug builds only) so login validation can be built/tested end to end.
 *
 * [passwordHash] stores the same SHA-256-based hash the old apps used
 * (see [com.united.digitaldispatch.utils.PasswordUtils]) so credentials
 * synced from the existing server format stay compatible.
 */
@Entity(tableName = "user_master_table")
data class UserMasterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val employeeCode: String,      // what the user types into "Employee / Partner ID"
    val userName: String? = null,
    val passwordHash: String,
    val organizationCode: String,  // links to OrganizationEntity.organizationCode
    val userRights: String? = null,
    val designation: String? = null,
    val department: String? = null
)
