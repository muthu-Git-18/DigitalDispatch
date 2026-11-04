package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PswDispatchDetailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: PSWDispatchDetailEntity)

    @Update
    fun update(item: PSWDispatchDetailEntity)

    @Query("SELECT * FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo AND baleNumber = :baleNo LIMIT 1")
    fun getBale(shipmentNo: String, baleNo: String): PSWDispatchDetailEntity?

    @Query("SELECT * FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo ORDER BY id DESC")
    fun getByShipmentNo(shipmentNo: String): List<PSWDispatchDetailEntity>

    @Query("SELECT COUNT(*) FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo")
    fun observeCount(shipmentNo: String): Flow<Int>

    @Query("DELETE FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo AND baleNumber = :baleNo")
    fun deleteBale(shipmentNo: String, baleNo: String)

    @Query("DELETE FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo")
    fun deleteByShipmentNo(shipmentNo: String)

    @Query("SELECT * FROM psw_dispatch_detail WHERE shipmentNo = :shipmentNo ORDER BY id DESC")
    fun observeByShipment(shipmentNo: String): Flow<List<PSWDispatchDetailEntity>>

    /** the bale in any shipment, or null */
    @Query("SELECT * FROM psw_dispatch_detail WHERE baleNumber = :baleNo LIMIT 1")
    fun getByBale(baleNo: String): PSWDispatchDetailEntity?
}

