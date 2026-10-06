package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity

@Dao
interface PswDispatchHeaderDao {

    @Query("SELECT * FROM psw_dispatch_header")
    fun getAll(): List<PswDispatchHeaderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<PswDispatchHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: PswDispatchHeaderEntity)

    @Query("DELETE FROM psw_dispatch_header")
    fun deleteAll()

    @Query("DELETE FROM psw_dispatch_header WHERE shipmentNo = :shipmentNo")
    fun deleteByShipmentNo(shipmentNo: String)
}