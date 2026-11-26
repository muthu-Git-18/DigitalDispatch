
package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "psw_receipt_detail",
    indices = [
        Index(value = ["shipmentNo", "baleNumber"], unique = true)
    ]
)
data class PswReceiptDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val shipmentNo: String,
    val detailId: String,
    val baleNumber: String,
    val grade: String?,
    val markedWt: Double,
    val dispatchWeight: Double,
    val receiptWeight: Double? = null,
    val fromSubinventoryCode: String?,
    val status: String = "INT",
    val headerStatus: String = "Y",
    val lastUpdatedBy: String? = null,
    val lastUpdatedDate: String? = null,
    val senderOrgnCode: String?,
    val receiverOrgnCode: String?,
    val recevWeighType: String? = null,
    val weightStatus: String? = null,
    val processType: String? = null,
    val deviceId: String? = null,

    val toSubinventoryCode: String? = null,
    val remarks: String? = null,
    val loadingDatetime: String? = null,
    val unloadingDatetime: String? = null,
    val createdBy: String? = null,
    val createdDate: String? = null,
    val dispatchWeighmentStatus: String? = null,

    val syncStatus: String = SYNC_PENDING,
    val serverMessage: String? = null,
    val attemptCount: Int = 0
) {
    companion object {
        const val SYNC_PENDING = "PENDING"
        const val SYNC_SUCCESS = "SUCCESS"
        const val SYNC_FAILED = "FAILED"
        const val STATUS_RECEIVED = "RCV"
    }
}
