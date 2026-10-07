package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity

@Dao
interface DispatchHeaderDao {

    @Query("SELECT * FROM dispatch_header")
    fun getAll(): List<DispatchHeaderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<DispatchHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: DispatchHeaderEntity)

    @Query("SELECT * FROM dispatch_header WHERE shipmentNo = :shipmentNo LIMIT 1")
    fun getByShipmentNo(shipmentNo: String): DispatchHeaderEntity?

    @Query("DELETE FROM dispatch_header")
    fun deleteAll()

    @Query("DELETE FROM dispatch_header WHERE shipmentNo = :shipmentNo")
    fun deleteByShipmentNo(shipmentNo: String)
}