package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.StockEntity

@Dao
interface StockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stock: List<StockEntity>)

    @Query("DELETE FROM stock_table")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM stock_table")
    fun count(): Int

    @Query("SELECT * FROM stock_table")
    fun getAll(): List<StockEntity>
}

