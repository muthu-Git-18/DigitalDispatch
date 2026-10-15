package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.DispatchSyncState

@Dao
interface DispatchSyncStateDao {

    @Query("SELECT lastChangeId FROM dispatch_sync_state WHERE shipmentNo = :shipmentNo")
    fun getLastChangeId(shipmentNo: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(state: DispatchSyncState)
}