package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Return sync" bookmark (same idea as SyncState of the purchase app):
 * the last server change id that was applied for ONE shipment.
 * The next download asks the server only for changes after this id.
 */
@Entity(tableName = "dispatch_sync_state")
data class DispatchSyncState(
    @PrimaryKey
    val shipmentNo: String,
    val lastChangeId: Long
)