// ===== data/local/entity/ReceiptDetailEntity.kt =====
package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per bale of a shipment.
 * Rows are filled by the receipt-details download API (next step);
 * scanning a bale in the details screen UPDATES its row to status RCV + syncStatus PENDING.
 * Upload step later picks: status = 'RCV' AND syncStatus IN ('PENDING','FAILED').
 */
@Entity(
    tableName = "receipt_detail",
    indices = [Index(value = ["shipmentNo", "baleNumber"], unique = true)]
)
data class ReceiptDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentNo: String,                 // shipmenT_NO
    val detailId: String,                   // detaiL_ID
    val baleNumber: String,                 // gpiL_BALE_NUMBER
    val grade: String?,
    val markedWt: Double,                   // markeD_WT
    val dispatchWeight: Double,             // dispatcH_WEIGHT
    val receiptWeight: Double? = null,      // receipT_WEIGHT (null until received)
    val fromSubinventoryCode: String?,
    val status: String = "INT",             // "RCV" once received
    val headerStatus: String = "Y",
    val lastUpdatedBy: String? = null,
    val lastUpdatedDate: String? = null,
    val senderOrgnCode: String?,
    val receiverOrgnCode: String?,
    val recevWeighType: String? = null,     // receV_WEIGH_TYPE  TWT / HND
    val weightStatus: String? = null,       // weighT_STATUS     Y weighed / N not weighed
    val processType: String? = null,        // filled in the upload step
    val deviceId: String? = null,
    // sync
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
