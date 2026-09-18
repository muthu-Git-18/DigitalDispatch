package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.OrganizationEntity

@Dao
interface OrganizationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(organizations: List<OrganizationEntity>)

    /** Distinct module codes available locally, e.g. [TAP, PPD, GLT, WH]. */
    @Query("SELECT DISTINCT organizationType FROM organization_table WHERE organizationType IS NOT NULL ORDER BY organizationType")
    fun getAvailableModules(): List<String>

    @Query("SELECT * FROM organization_table WHERE organizationType = :moduleType ORDER BY organizationName")
    fun getOrganizationsForModule(moduleType: String): List<OrganizationEntity>

    @Query("SELECT * FROM organization_table WHERE organizationCode = :code LIMIT 1")
    fun getByCode(code: String): OrganizationEntity?

    @Query("SELECT COUNT(*) FROM organization_table")
    fun count(): Int
}
