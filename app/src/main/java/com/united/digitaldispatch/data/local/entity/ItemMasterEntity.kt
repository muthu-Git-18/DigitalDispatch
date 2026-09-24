package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item_master_table")
data class ItemMasterEntity(
    @PrimaryKey
    val id: Int,
    val itemCode: String?,
    val itemCodeGrp: String?,
    val itemGrp: String?,
    val itemType: String?,
    val itemDescription: String?,
    val crop: String?,
    val variety: String?,
    val costCategory: String?,
    val orgnType: String?,
    val status: String?,
    val flag: String?,
    val attribute2: String?,
    val attribute3: String?
)
