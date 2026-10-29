package com.united.digitaldispatch.sync

import android.app.Application
import android.content.Context
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Offline-first sync entry point (upload + return sync).
 *
 *  UPLOAD  (device -> server)
 *   - requestSync(ctx)   : after saving locally. Uploads right away when the network
 *                          is good, otherwise it waits (network turns good / 15 min timer)
 *   - syncNow(ctx)       : manual "send now"
 *
 *  DOWNLOAD (server -> device, "return sync")
 *   - pullChanges(ctx, shipmentNo, hintChangeId)
 *   - setActiveShipment(shipmentNo) : the shipment open on screen; it is downloaded
 *                          again whenever the network turns good
 */
object SyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var started = false
    private var lastWasStrong = false

    @Volatile
    private var activeShipmentNo: String? = null

    fun start(app: Application) {

        if (started) return
        started = true

        NetworkMonitor.start(app)

        // network turns from weak/none to strong -> send what is waiting, fetch what we missed
        NetworkMonitor.isHighQuality.observeForever { strong ->

            android.util.Log.d("DispatchPull", "Network quality changed: strong=$strong (was=$lastWasStrong)")

            if (strong && !lastWasStrong) {

                SyncScheduler.triggerImmediateSync(app)

                activeShipmentNo?.let {
                    pullChanges(app, it, source = "network-restored")
                }
            }

            lastWasStrong = strong
        }

        SyncScheduler.schedulePeriodicSync(app)
    }

    // ---------------- upload ----------------

    fun requestSync(context: Context) {
        if (NetworkMonitor.isHighQuality.value == true) {
            SyncScheduler.triggerImmediateSync(context)
        }
    }

    fun syncNow(context: Context) {
        SyncScheduler.triggerImmediateSync(context)
    }

    // ---------------- download ----------------

    fun setActiveShipment(shipmentNo: String?) {
        activeShipmentNo = shipmentNo
    }

    /**
     * Downloads the changes of one shipment (only when the network is good).
     * [hintChangeId] = id announced by SignalR; skipped when we already have it.
     * Tells the open screen to reload when something changed.
     */
    fun pullChanges(
        context: Context,
        shipmentNo: String,
        hintChangeId: Long? = null,
        source: String = "unknown"
    ) {

        android.util.Log.d(
            "DispatchPull",
            "[$source] pull requested: shipment=$shipmentNo hint=$hintChangeId"
        )

        if (NetworkMonitor.isHighQuality.value != true) {
            android.util.Log.w(
                "DispatchPull",
                "[$source] SKIPPED: network not good (shipment=$shipmentNo)"
            )
            return
        }

        val appContext = context.applicationContext

        scope.launch {

            val repository = MasterDataRepository(
                ApiClient.instance,
                AppDatabase.getInstance(appContext)
            )

            val result = repository.pullDispatchChanges(shipmentNo, hintChangeId)

            val applied = result.getOrDefault(0)

            android.util.Log.d(
                "DispatchPull",
                "[$source] pull finished: shipment=$shipmentNo applied=$applied success=${result.isSuccess}"
            )

            if (applied > 0) {
                SyncEvents.detailsChanged.tryEmit(shipmentNo)
                android.util.Log.d("DispatchPull", "[$source] screen reload event sent for $shipmentNo")
            }
        }
    }
}