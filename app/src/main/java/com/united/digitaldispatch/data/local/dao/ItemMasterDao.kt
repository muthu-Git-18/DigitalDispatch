package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.ItemMasterEntity

@Dao
interface ItemMasterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<ItemMasterEntity>)

    @Query("SELECT COUNT(*) FROM item_master_table")
    fun count(): Int

    @Query("SELECT * FROM item_master_table")
    fun getAll(): List<ItemMasterEntity>

    @Query("DELETE FROM item_master_table")
    fun deleteAll()
}