
package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.PswReceiptDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PswReceiptDetailDao {

    @Query("""
        SELECT * FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
        AND baleNumber = :baleNo
        LIMIT 1
    """)
    suspend fun findBale(
        shipmentNo: String,
        baleNo: String
    ): PswReceiptDetailEntity?

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
    """)
    fun observeTotal(shipmentNo: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo AND status = 'RCV'
    """)
    fun observeReceived(shipmentNo: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
        AND status = 'RCV' AND weightStatus = 'Y'
    """)
    fun observeWeighed(shipmentNo: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo AND status = 'RCV'
    """)
    suspend fun receivedCount(shipmentNo: String): Int

    @Query("""
        SELECT recevWeighType FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
        AND status = 'RCV'
        AND recevWeighType IS NOT NULL
        AND recevWeighType <> ''
        LIMIT 1
    """)
    suspend fun firstReceivedWeighType(shipmentNo: String): String?

    @Query("""
        UPDATE psw_receipt_detail SET
            receiptWeight = :receiptWeight,
            weightStatus = :weightStatus,
            status = 'RCV',
            headerStatus = 'Y',
            recevWeighType = :weighType,
            lastUpdatedBy = :userId,
            lastUpdatedDate = :date,
            deviceId = :deviceId,
            syncStatus = 'PENDING',
            serverMessage = NULL,
            attemptCount = 0
        WHERE shipmentNo = :shipmentNo
        AND baleNumber = :baleNo
    """)
    suspend fun markReceived(
        shipmentNo: String,
        baleNo: String,
        receiptWeight: Double,
        weightStatus: String,
        weighType: String,
        userId: String,
        date: String,
        deviceId: String
    ): Int

    @Query("""
        UPDATE psw_receipt_detail
        SET recevWeighType = :weighType
        WHERE shipmentNo = :shipmentNo AND status = 'RCV'
    """)
    suspend fun updateReceivedWeighType(
        shipmentNo: String,
        weighType: String
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(
        items: List<PswReceiptDetailEntity>
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(
        items: List<PswReceiptDetailEntity>
    ): List<Long>

    @Query("""
        SELECT * FROM psw_receipt_detail
        WHERE status = 'RCV'
        AND (
            syncStatus = 'PENDING'
            OR (syncStatus = 'FAILED' AND attemptCount < :maxAttempts)
        )
        LIMIT :limit
    """)
    suspend fun getUnsynced(
        maxAttempts: Int,
        limit: Int
    ): List<PswReceiptDetailEntity>

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE status = 'RCV'
        AND syncStatus IN ('PENDING', 'FAILED')
    """)
    suspend fun countUnsynced(): Int

    @Query("""
        UPDATE psw_receipt_detail
        SET syncStatus = 'SUCCESS',
            serverMessage = :msg
        WHERE shipmentNo = :shipmentNo
        AND baleNumber = :bale
        AND status = 'RCV'
    """)
    suspend fun markSynced(
        shipmentNo: String,
        bale: String,
        msg: String
    )

    @Query("""
        UPDATE psw_receipt_detail
        SET syncStatus = 'FAILED',
            serverMessage = :msg,
            attemptCount = attemptCount + 1
        WHERE shipmentNo = :shipmentNo
        AND baleNumber = :bale
        AND status = 'RCV'
    """)
    suspend fun markFailed(
        shipmentNo: String,
        bale: String,
        msg: String
    )

    @Query("""
        UPDATE psw_receipt_detail
        SET receiptWeight = :receiptWeight,
            status = :status,
            weightStatus = :weightStatus,
            recevWeighType = :weighType,
            unloadingDatetime = :unloading,
            syncStatus = 'SUCCESS',
            serverMessage = 'Synced from server',
            attemptCount = 0,
            processType = :processType,
            deviceId = :deviceId
        WHERE shipmentNo = :shipmentNo
        AND baleNumber = :bale
    """)
    suspend fun applyRemoteReceipt(
        shipmentNo: String,
        bale: String,
        receiptWeight: Double?,
        status: String,
        weightStatus: String?,
        weighType: String?,
        unloading: String?,
        processType: String?,
        deviceId: String?
    )

    @Query("""
        SELECT COUNT(*) FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
        AND status = 'RCV'
        AND syncStatus != 'SUCCESS'
    """)
    fun observePending(shipmentNo: String): Flow<Int>

    @Query("""
        SELECT * FROM psw_receipt_detail
        WHERE shipmentNo = :shipmentNo
        ORDER BY id
    """)
    fun observeByShipment(
        shipmentNo: String
    ): Flow<List<PswReceiptDetailEntity>>
}
