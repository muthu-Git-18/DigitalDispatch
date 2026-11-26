package com.united.digitaldispatch.sync

/**
 * SignalR settings. Fill these in from the backend.
 *
 * While HUB_URL is blank the live connection is switched OFF and the app uses
 * pull-only sync (screen opens, network comes back, ...) - nothing breaks.
 */
/*private const val HUB_BASE_URL =
    "http://103.165.4.170:85/LorryHireAndroidApplicationApiPublish"*/


private const val HUB_BASE_URL =
    "https://leafapiuat.godfreyphillips.co.in/LorryHireAndroidApplicationApiPublishTest"

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

/** Live return sync of PSW bales (one group per shipment). */
object PswDispatchHubConfig {

    // TODO: confirm the hub path, event and method names with the backend
    val HUB_URL: String = "$HUB_BASE_URL/pswDispatchHub"

    /** server -> app: a change happened (changeId: Long, shipmentNo: String) */
    const val EVENT_CHANGED = "PSW_DispatchUpdated"

    /** app -> server: must match the hub's method names exactly */
    const val METHOD_JOIN = "JoinShipmentGroup"
    const val METHOD_LEAVE = "LeaveShipmentGroup"

    /** blank url = live connection off, pull-only sync keeps working */
    val enabled: Boolean
        get() = HUB_URL.isNotBlank()
}

object PswDispatchCompleteHubConfig  {

    val HUB_URL: String = "$HUB_BASE_URL/pswDispatchCompleteHub"

    /** server -> app: a shipment was completed (shipmentNo: String) */
    const val EVENT_COMPLETED = "PSW_DispatchCompleted"

    /** app -> server: must match the complete hub's method names exactly */
    const val METHOD_JOIN = "JoinShipmentGroup"
    const val METHOD_LEAVE = "LeaveShipmentGroup"

    val enabled: Boolean
        get() = HUB_URL.isNotBlank()
}


object ReceiptHubConfig {
    val HUB_URL: String = "$HUB_BASE_URL/receiptHub"          // TODO confirm hub path

    const val EVENT_CHANGED = "ReceiptUpdated"                // TODO confirm event name

    const val METHOD_JOIN = "JoinShipmentGroup"
    const val METHOD_LEAVE = "LeaveShipmentGroup"
    val enabled: Boolean get() = HUB_URL.isNotBlank()

}


object ReceiptCompleteHubConfig {

    val HUB_URL: String = "$HUB_BASE_URL/receiptCompleteHub"   // TODO confirm hub path with backend

    /** server -> app: a receipt was completed (object with ShipmentNo) */
    const val EVENT_COMPLETED = "ReceiptCompleted"             // TODO confirm event name with backend

    const val METHOD_JOIN = "JoinShipmentGroup"                // TODO confirm
    const val METHOD_LEAVE = "LeaveShipmentGroup"              // TODO confirm

    val enabled: Boolean get() = HUB_URL.isNotBlank()
}