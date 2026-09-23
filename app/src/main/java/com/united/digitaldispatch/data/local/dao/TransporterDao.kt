
package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.TransporterEntity

@Dao
interface TransporterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(transporters: List<TransporterEntity>)

    @Query("SELECT COUNT(*) FROM transporter_table")
    fun count(): Int
}