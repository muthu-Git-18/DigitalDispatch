package com.united.digitaldispatch.sync

/**
 * SignalR settings. Fill these in from the backend.
 *
 * While HUB_URL is blank the live connection is switched OFF and the app uses
 * pull-only sync (screen opens, network comes back, ...) - nothing breaks.
 */
private const val HUB_BASE_URL =
    "http://103.165.4.170:85/LorryHireAndroidApplicationApiPublish"

/** Live return sync of bales (one group per shipment). */
object DispatchHubConfig {

    val HUB_URL: String = "$HUB_BASE_URL/dispatchHub"

    /** server -> app: a change happened (changeId: Long, shipmentNo: String) */
    const val EVENT_CHANGED = "DispatchUpdated"

    /** app -> server: must match DispatchHub method names exactly */
    const val METHOD_JOIN = "JoinShipmentGroup"
    const val METHOD_LEAVE = "LeaveShipmentGroup"

    val enabled: Boolean
        get() = HUB_URL.isNotBlank()
}

/** Shipment completed on another device. */
object DispatchCompleteHubConfig {

    val HUB_URL: String = "$HUB_BASE_URL/dispatchCompleteHub"

    /** server -> app: a shipment was completed (shipmentNo: String) */
    const val EVENT_COMPLETED = "DispatchCompleted"     // confirm with backend

    /** app -> server: must match the complete hub's method names exactly */
    const val METHOD_JOIN = "JoinShipmentGroup"         // confirm with backend
    const val METHOD_LEAVE = "LeaveShipmentGroup"       // confirm with backend

    val enabled: Boolean
        get() = HUB_URL.isNotBlank()
}