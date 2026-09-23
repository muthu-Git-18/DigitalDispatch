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

    @Query("""
        SELECT * FROM user_master_table
        WHERE empCode = :empCode
        LIMIT 1
    """)
    fun findByEmployeeCode(empCode: String): UserMasterEntity?

    @Query("SELECT COUNT(*) FROM user_master_table")
    fun count(): Int
}