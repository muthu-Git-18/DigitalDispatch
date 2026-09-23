package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_master_table")
data class UserMasterEntity(
    @PrimaryKey
    val userId: String,
    val empCode: String?,
    val userName: String?,
    val password: String?,
    val userErpName: String?,
    val designation: String?,
    val department: String?,
    val userRights: String?,
    val syncId: String?,
    val mobileNo: String?,
    val attribute1: String?,
    val attribute2: String?
)