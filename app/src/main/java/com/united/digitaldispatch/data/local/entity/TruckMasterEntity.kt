package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "truck_master_table")
data class TruckMasterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fromOrgn: String?,
    val toOrgn: String?,
    val truckType: String?,
    val freightValue: Double?
)
