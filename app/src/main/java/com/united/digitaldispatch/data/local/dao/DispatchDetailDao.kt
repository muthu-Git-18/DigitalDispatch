package com.united.digitaldispatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.united.digitaldispatch.Dispatch.models.BaleDetail

@Dao
interface DispatchDetailDao {

    @Query("SELECT * FROM dispatch_detail WHERE shipmentNo = :shipmentNo ORDER BY createdDate DESC")
    fun getByShipment(shipmentNo: String): List<BaleDetail>

    /** the bale in ANY shipment (a bale can only be dispatched once) */
    @Query("SELECT * FROM dispatch_detail WHERE gpilBaleNumber = :baleNo LIMIT 1")
    fun getByBale(baleNo: String): BaleDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(detail: BaleDetail)

    @Query("DELETE FROM dispatch_detail WHERE detailId = :detailId")
    fun deleteByDetailId(detailId: String)

    /** used when the server says a bale was removed */
    @Query("DELETE FROM dispatch_detail WHERE gpilBaleNumber = :baleNo")
    fun deleteByBale(baleNo: String)

    // ---------------- upload ----------------

    @Query(
        "SELECT * FROM dispatch_detail " +
                "WHERE syncStatus != 'SUCCESS' AND attemptCount < :maxAttempts " +
                "ORDER BY createdDate ASC LIMIT :limit"
    )
    fun getUnsynced(maxAttempts: Int, limit: Int): List<BaleDetail>

    @Query("SELECT COUNT(*) FROM dispatch_detail WHERE syncStatus != 'SUCCESS'")
    fun countUnsynced(): Int

    @Query("UPDATE dispatch_detail SET syncStatus = 'SUCCESS', serverMessage = :message WHERE gpilBaleNumber = :baleNo")
    fun markSynced(baleNo: String, message: String)

    @Query(
        "UPDATE dispatch_detail SET syncStatus = 'FAILED', serverMessage = :message, " +
                "attemptCount = attemptCount + 1 " +
                "WHERE gpilBaleNumber = :baleNo AND syncStatus != 'SUCCESS'"
    )
    fun markFailed(baleNo: String, message: String)

    /** manual "send again" for bales that stopped after too many failures */
    @Query("UPDATE dispatch_detail SET attemptCount = 0 WHERE syncStatus = 'FAILED'")
    fun resetFailedAttempts()
}