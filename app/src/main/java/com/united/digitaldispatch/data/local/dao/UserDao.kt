package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.UserMasterEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(users: List<UserMasterEntity>)

    /**
     * Deliberately split from [findByEmployeeCodeAndOrg] + password check so the
     * login flow can tell "employee ID not found" apart from "wrong password" —
     * matches the old apps' behaviour (WH/PPD LoginActivity).
     */
    @Query("SELECT * FROM user_master_table WHERE employeeCode = :employeeCode AND organizationCode = :organizationCode LIMIT 1")
    fun findByEmployeeCodeAndOrg(employeeCode: String, organizationCode: String): UserMasterEntity?

    @Query("SELECT COUNT(*) FROM user_master_table")
    fun count(): Int
}
