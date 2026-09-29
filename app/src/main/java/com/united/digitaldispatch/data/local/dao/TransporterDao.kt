
package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity

@Dao
interface TransporterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(transporters: List<TransporterEntity>)

    @Query("SELECT COUNT(*) FROM transporter_table")
    fun count(): Int

    @Query("SELECT * FROM transporter_table ORDER BY transporterName")
    fun getTransporters(): List<TransporterEntity>

    //TRUCK MASTER

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTruckMaster(trucks: List<TruckMasterEntity>)

    @Query("DELETE FROM truck_master_table")
    fun deleteTruckMaster()

    @Query("SELECT * FROM truck_master_table")
    fun getTruckMaster(): List<TruckMasterEntity>

    @Query("""
    SELECT * FROM truck_master_table
    WHERE fromOrgn = :fromOrgn
    AND toOrgn = :toOrgn
""")
    fun getTruckMasterByOrganizations(
        fromOrgn: String,
        toOrgn: String?
    ): List<TruckMasterEntity>


    @Query("""
    SELECT DISTINCT toOrgn
    FROM truck_master_table
    WHERE toOrgn IS NOT NULL
    ORDER BY toOrgn
""")
    fun getDistinctToOrganizations(): List<String>
}