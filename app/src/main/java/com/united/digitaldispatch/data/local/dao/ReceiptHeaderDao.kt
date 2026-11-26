package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptHeaderDao {

    @Query("SELECT * FROM receipt_headers ORDER BY senderDate DESC")
    fun observeAll(): Flow<List<ReceiptHeaderEntity>>

    @Query("SELECT * FROM receipt_headers WHERE shipmentNo = :shipmentNo LIMIT 1")
    suspend fun getByShipmentNo(shipmentNo: String): ReceiptHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReceiptHeaderEntity>)

    @Query("DELETE FROM receipt_headers")
    suspend fun clear()

    // server is the source of truth for receipts -> swap the whole list
    @Transaction
    suspend fun replaceAll(items: List<ReceiptHeaderEntity>) {
        clear()
        insertAll(items)
    }


    // =====================================================================
// A) ReceiptHeaderDao -- ADD these
// =====================================================================
    data class WeighTypeRow(val shipmentNo: String, val recevWeighType: String?)

    @Query("SELECT shipmentNo, recevWeighType FROM receipt_headers")
    suspend fun getAllWeighTypes(): List<WeighTypeRow>

    // only fills the type if the header has none yet
    @Query("UPDATE receipt_headers SET recevWeighType = :type WHERE shipmentNo = :shipmentNo AND (recevWeighType IS NULL OR recevWeighType = '')")
    suspend fun setWeighTypeIfBlank(shipmentNo: String, type: String)


    @Query("DELETE FROM receipt_headers WHERE shipmentNo = :shipmentNo")
    suspend fun deleteByShipmentNo(shipmentNo: String)
}
