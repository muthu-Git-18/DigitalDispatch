
// ===== data/local/dao/ReceiptDetailDao.kt =====
package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDetailDao {

    @Query("SELECT * FROM receipt_detail WHERE shipmentNo = :shipmentNo AND baleNumber = :baleNo LIMIT 1")
    suspend fun findBale(shipmentNo: String, baleNo: String): ReceiptDetailEntity?

    // total bales of the shipment
    @Query("SELECT COUNT(*) FROM receipt_detail WHERE shipmentNo = :shipmentNo")
    fun observeTotal(shipmentNo: String): Flow<Int>

    // received bales
    @Query("SELECT COUNT(*) FROM receipt_detail WHERE shipmentNo = :shipmentNo AND status = 'RCV'")
    fun observeReceived(shipmentNo: String): Flow<Int>

    // received AND weighed bales (weightStatus = Y)
    @Query("SELECT COUNT(*) FROM receipt_detail WHERE shipmentNo = :shipmentNo AND status = 'RCV' AND weightStatus = 'Y'")
    fun observeWeighed(shipmentNo: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipt_detail WHERE shipmentNo = :shipmentNo AND status = 'RCV'")
    suspend fun receivedCount(shipmentNo: String): Int

    @Query("SELECT recevWeighType FROM receipt_detail WHERE shipmentNo = :shipmentNo AND status = 'RCV' AND recevWeighType IS NOT NULL AND recevWeighType <> '' LIMIT 1")
    suspend fun firstReceivedWeighType(shipmentNo: String): String?

    @Query(
        """UPDATE receipt_detail SET
             receiptWeight = :receiptWeight, weightStatus = :weightStatus,
             status = 'RCV', headerStatus = 'Y', recevWeighType = :weighType,
             lastUpdatedBy = :userId, lastUpdatedDate = :date, deviceId = :deviceId,
             syncStatus = 'PENDING', serverMessage = NULL, attemptCount = 0
           WHERE shipmentNo = :shipmentNo AND baleNumber = :baleNo"""
    )
    suspend fun markReceived(
        shipmentNo: String, baleNo: String, receiptWeight: Double, weightStatus: String,
        weighType: String, userId: String, date: String, deviceId: String
    ): Int

    @Query("UPDATE receipt_detail SET recevWeighType = :weighType WHERE shipmentNo = :shipmentNo AND status = 'RCV'")
    suspend fun updateReceivedWeighType(shipmentNo: String, weighType: String)

    // Used by the details-download step. IGNORE (not REPLACE) so a re-download never
    // wipes a bale that was already received on this device.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ReceiptDetailEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(list: List<ReceiptDetailEntity>): List<Long>
}