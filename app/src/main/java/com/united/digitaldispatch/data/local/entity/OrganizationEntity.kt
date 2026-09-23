package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organization_table")
data class OrganizationEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val organizationCode: String,
    val organizationName: String?,
    val organizationType: String?,
    val organizationAddress5: String?,
    val organizationAddress6: String?,
    val insuranceValue: String?,
    val status: String?,
    val attribute1: String?,
    val attribute2: String?,
    val attribute3: String?,
    val attribute4: String?,
    val port: String?,
    val syncID: String?,
    val syncPassword: String?,
    val variety: String?
)