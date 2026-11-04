package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "psw_dispatch_detail",
    indices = [Index(value = ["shipmentNo", "baleNumber"], unique = true)]
)
data class PSWDispatchDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentNo: String,
    val detailId: String,
    val baleNumber: String,
    val grade: String?,
    val markedWt: Double,
    val dispatchWeight: Double,
    val fromSubinventoryCode: String?,
    val toSubinventoryCode: String?,
    val status: String = "INT",
    val headerStatus: String = "Y",
    val createdBy: String?,
    val createdDate: String?,
    val senderOrgnCode: String?,
    val receiverOrgnCode: String?,
    val dispatchWeighmentStatus: String?,
    val processType: String?,
    val deviceId: String?,
    // local only (not in the API)
    val price: String? = null,
    val tbLotNumber: String? = null,
    val purchaseDate: String? = null,
    // sync
    val syncStatus: String = "PENDING",     // PENDING / SYNCED / FAILED
    val serverMessage: String? = null
)
{
    companion object {
        const val SYNC_PENDING = "PENDING"
        const val SYNC_SUCCESS = "SUCCESS"
        const val SYNC_FAILED = "FAILED"
    }
}
