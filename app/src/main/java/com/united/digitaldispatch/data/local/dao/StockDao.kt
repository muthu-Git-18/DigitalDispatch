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

    @Query("SELECT * FROM stock_table WHERE gpiL_BALE_NUMBER = :baleNo LIMIT 1")
    fun getByBaleNumber(baleNo: String): StockEntity?


    @Query("""
    SELECT DISTINCT TRIM(crop)
    FROM stock_table
    WHERE crop IS NOT NULL
      AND TRIM(crop) != ''
    ORDER BY TRIM(crop)
""")
    fun getDistinctCrops(): List<String>

    @Query("""
    SELECT DISTINCT TRIM(variety)
    FROM stock_table
    WHERE variety IS NOT NULL
      AND TRIM(variety) != ''
      AND (:crop IS NULL OR TRIM(crop) = :crop)
    ORDER BY TRIM(variety)
""")
    fun getDistinctVarieties(crop: String?): List<String>

    @Query("""
    SELECT *
    FROM stock_table
    WHERE (:crop IS NULL OR TRIM(crop) = :crop)
      AND (:variety IS NULL OR TRIM(variety) = :variety)
    ORDER BY sno
""")
    fun getFilteredStock(
        crop: String?,
        variety: String?
    ): List<StockEntity>

}

