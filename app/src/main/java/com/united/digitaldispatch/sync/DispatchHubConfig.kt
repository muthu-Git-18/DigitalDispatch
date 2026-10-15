package com.united.digitaldispatch.sync

/**
 * SignalR settings. Fill these in from the backend.
 *
 * While HUB_URL is blank the live connection is switched OFF and the app uses
 * pull-only sync (screen opens, network comes back, ...) - nothing breaks.
 *
 * The purchase app used:
 *   hub   : .../purchaseHub
 *   event : PurchaseUpdated(changeId: Long, headerId: String)
 *   group : send("JoinHeader", headerId) / send("LeaveHeader", headerId)
 * The dispatch hub is expected to be the same idea with the SHIPMENT NO as the group.
 */
object DispatchHubConfig {

    /** e.g. "http://103.165.4.170:85/LorryHireAndroidApplicationApiPublish/dispatchHub" */
    val HUB_URL: String = ""

    /** server -> app: a change happened   (changeId: Long, shipmentNo: String) */
    const val EVENT_CHANGED = "DispatchUpdated"

    /** app -> server: join / leave the group of one shipment */
    const val METHOD_JOIN = "JoinShipment"
    const val METHOD_LEAVE = "LeaveShipment"

    val enabled: Boolean
        get() = HUB_URL.isNotBlank()
}