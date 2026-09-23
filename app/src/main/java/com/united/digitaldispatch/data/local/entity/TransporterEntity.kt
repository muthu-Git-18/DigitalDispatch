package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transporter_table")
data class TransporterEntity(
    @PrimaryKey
    val transporterCode: String,
    val transporterName: String?,
    val destination: String?,
    val amount: String?,
    val status: String?
)