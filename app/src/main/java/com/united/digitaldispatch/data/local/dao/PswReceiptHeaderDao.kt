package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.united.digitaldispatch.data.local.entity.PswReceiptHeaderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PswReceiptHeaderDao {

    @Query("SELECT * FROM psw_receipt_headers ORDER BY senderDate DESC")
    fun observeAll(): Flow<List<PswReceiptHeaderEntity>>

    @Query("""
        SELECT * FROM psw_receipt_headers
        WHERE shipmentNo = :shipmentNo
        LIMIT 1
    """)
    suspend fun getByShipmentNo(
        shipmentNo: String
    ): PswReceiptHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PswReceiptHeaderEntity>)

    @Query("DELETE FROM psw_receipt_headers")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<PswReceiptHeaderEntity>) {
        clear()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }

    data class WeighTypeRow(
        val shipmentNo: String,
        val recevWeighType: String?
    )

    @Query("""
        SELECT shipmentNo, recevWeighType
        FROM psw_receipt_headers
    """)
    suspend fun getAllWeighTypes(): List<WeighTypeRow>

    @Query("""
        UPDATE psw_receipt_headers
        SET recevWeighType = :type
        WHERE shipmentNo = :shipmentNo
        AND (recevWeighType IS NULL OR recevWeighType = '')
    """)
    suspend fun setWeighTypeIfBlank(
        shipmentNo: String,
        type: String
    )
}