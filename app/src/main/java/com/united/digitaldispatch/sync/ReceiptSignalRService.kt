package com.united.digitaldispatch.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.JsonElement
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live "return sync" of Receipt through SignalR (two hubs).
 *
 *  receiptHub          -> "ReceiptUpdated"   : bales received on another device -> pull changes
 *  receiptCompleteHub  -> "ReceiptCompleted" : receipt completed on another device
 *                                              -> delete it locally + tell the open screens
 */
class ReceiptSignalRService : Service() {

    companion object {

        private const val TAG = "ReceiptSignalR"
        private const val EXTRA_SHIPMENT_NO = "extra_receipt_shipment_no"
        private const val ACTION_STOP = "action_receipt_stop"
        private const val NOTIFICATION_ID = 2003
        private const val CHANNEL_ID = "dispatch_signalr_channel"

        fun start(context: Context, shipmentNo: String) {

            if (!ReceiptHubConfig.enabled) return

            val intent = Intent(context, ReceiptSignalRService::class.java)
                .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the receipt live sync service", e)
            }
        }

        /** Only stops the service if [shipmentNo] is still the shipment it is serving. */
        fun stop(context: Context, shipmentNo: String) {

            val intent = Intent(context, ReceiptSignalRService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)

            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send stop to the receipt live sync service", e)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // bale hub
    private var hubConnection: HubConnection? = null

    // completion hub
    private var completeHub: HubConnection? = null

    private lateinit var notificationManager: NotificationManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var currentShipmentNo: String? = null
    private var joinedShipmentNo: String? = null
    private var joinedCompleteShipmentNo: String? = null

    private var reconnectJob: Job? = null

    @Volatile private var isConnecting = false
    @Volatile private var isConnectingComplete = false
    @Volatile private var isStopping = false
    @Volatile private var reconnectAttempt = 0

    // ------------------------------------------------
    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(NotificationManager::class.java)

        createNotificationChannel()
        startForegroundCompat("Connecting...")
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val shipmentNo = intent?.getStringExtra(EXTRA_SHIPMENT_NO)

        // ---- stop request from the screen ----
        if (intent?.action == ACTION_STOP) {
            if (shipmentNo == currentShipmentNo) {
                stopSelf()
            } else {
                Log.d(TAG, "Stop ignored, now serving $currentShipmentNo (asked by $shipmentNo)")
            }
            return START_NOT_STICKY
        }

        // ---- start / switch shipment ----
        if (!shipmentNo.isNullOrBlank() && shipmentNo != currentShipmentNo) {

            val old = joinedShipmentNo
            val oldComplete = joinedCompleteShipmentNo

            currentShipmentNo = shipmentNo
            joinedShipmentNo = null
            joinedCompleteShipmentNo = null

            if (old != null) leaveGroup(old)
            if (oldComplete != null) leaveCompleteGroup(oldComplete)

            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                joinGroup(shipmentNo)
                SyncManager.pullReceiptChanges(applicationContext, shipmentNo, source = "receipt-switch")
            } else {
                connect()
            }

            if (completeHub?.connectionState == HubConnectionState.CONNECTED) {
                joinCompleteGroup(shipmentNo)
            } else {
                startCompleteHub()
            }

        } else {
            if (hubConnection == null) connect()
            if (completeHub == null) startCompleteHub()
        }

        // tied to the open screen -> do not restart on its own
        return START_NOT_STICKY
    }

    // ================================================
    //  RECEIPT BALE HUB
    // ================================================
    private fun connect() {

        if (isStopping || isConnecting || hubConnection != null) return
        if (!ReceiptHubConfig.enabled) return

        isConnecting = true

        val hub = HubConnectionBuilder
            .create(ReceiptHubConfig.HUB_URL)
            .build()

        hub.keepAliveInterval = 10_000L
        hub.serverTimeout = 30_000L

        hub.on(
            ReceiptHubConfig.EVENT_CHANGED,
            { changeId: Long, shipmentNo: String ->
                Log.d(TAG, "Signal received: shipment=$shipmentNo changeId=$changeId")
                if (shipmentNo == currentShipmentNo) {
                    SyncManager.pullReceiptChanges(
                        applicationContext, shipmentNo, changeId, source = "receipt-signalr"
                    )
                }
            },
            Long::class.javaObjectType,
            String::class.java
        )

        hub.onClosed { error ->
            if (hubConnection !== hub) return@onClosed

            Log.w(TAG, "Hub closed", error)

            hubConnection = null
            joinedShipmentNo = null
            isConnecting = false

            if (!isStopping) {
                updateNotification("Offline - reconnecting")
                scheduleReconnect()
            }
        }

        hubConnection = hub

        scope.launch {
            try {
                hub.start().blockingAwait()

                if (hubConnection !== hub || isStopping) {
                    try { hub.stop() } catch (_: Exception) {}
                    return@launch
                }

                reconnectAttempt = 0
                isConnecting = false

                updateNotification("Listening for receipt updates")

                currentShipmentNo?.let { shipmentNo ->
                    // join FIRST, then pull: nothing can be missed between the two
                    joinGroup(shipmentNo)
                    SyncManager.pullReceiptChanges(applicationContext, shipmentNo, source = "receipt-connected")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Hub connect failed", e)

                if (hubConnection === hub) hubConnection = null
                isConnecting = false

                if (!isStopping) scheduleReconnect()
            }
        }
    }

    private fun joinGroup(shipmentNo: String) {
        try {
            hubConnection
                ?.invoke(ReceiptHubConfig.METHOD_JOIN, shipmentNo)
                ?.subscribe(
                    {
                        joinedShipmentNo = shipmentNo
                        Log.d(TAG, "Joined group $shipmentNo")
                    },
                    { error -> Log.e(TAG, "Join rejected by server for $shipmentNo", error) }
                )
        } catch (e: Exception) {
            Log.e(TAG, "Join failed", e)
        }
    }

    private fun leaveGroup(shipmentNo: String) {
        try {
            hubConnection?.send(ReceiptHubConfig.METHOD_LEAVE, shipmentNo)
            Log.d(TAG, "Left group $shipmentNo")
        } catch (e: Exception) {
            Log.e(TAG, "Leave failed", e)
        }
    }

    // ================================================
    //  COMPLETION HUB
    // ================================================
    private fun startCompleteHub() {

        if (isStopping || isConnectingComplete || completeHub != null) return
        if (!ReceiptCompleteHubConfig.enabled) return

        isConnectingComplete = true

        val hub = HubConnectionBuilder
            .create(ReceiptCompleteHubConfig.HUB_URL)
            .build()

        hub.keepAliveInterval = 10_000L
        hub.serverTimeout = 30_000L

        // the server sends an OBJECT { ShipmentNo, ... }, not a string (same as dispatch)
        hub.on(
            ReceiptCompleteHubConfig.EVENT_COMPLETED,
            { raw: JsonElement ->

                Log.d(TAG, "Completed RAW: $raw")

                val completed = try {
                    raw.asJsonObject.entrySet()
                        .firstOrNull { it.key.equals("shipmentNo", ignoreCase = true) }
                        ?.value?.asString?.trim()
                } catch (e: Exception) {
                    Log.e(TAG, "Completed payload could not be read", e)
                    null
                }

                Log.d(TAG, "Completed signal: shipment=$completed current=$currentShipmentNo")

                if (!completed.isNullOrEmpty() && completed == currentShipmentNo?.trim()) {
                    SyncManager.onReceiptShipmentCompleted(applicationContext, completed)
                }
            },
            JsonElement::class.java
        )

        hub.onClosed { error ->
            if (completeHub !== hub) return@onClosed

            Log.w(TAG, "Complete hub closed", error)

            completeHub = null
            joinedCompleteShipmentNo = null
            isConnectingComplete = false

            if (!isStopping) scheduleReconnect()
        }

        completeHub = hub

        scope.launch {
            try {
                hub.start().blockingAwait()

                if (completeHub !== hub || isStopping) {
                    try { hub.stop() } catch (_: Exception) {}
                    return@launch
                }

                isConnectingComplete = false

                currentShipmentNo?.let { joinCompleteGroup(it) }

            } catch (e: Exception) {
                Log.e(TAG, "Complete hub connect failed", e)

                if (completeHub === hub) completeHub = null
                isConnectingComplete = false

                if (!isStopping) scheduleReconnect()
            }
        }
    }

    private fun joinCompleteGroup(shipmentNo: String) {
        try {
            completeHub
                ?.invoke(ReceiptCompleteHubConfig.METHOD_JOIN, shipmentNo)
                ?.subscribe(
                    {
                        joinedCompleteShipmentNo = shipmentNo
                        Log.d(TAG, "Joined complete group $shipmentNo")
                    },
                    { error -> Log.e(TAG, "Complete join rejected by server for $shipmentNo", error) }
                )
        } catch (e: Exception) {
            Log.e(TAG, "Complete join failed", e)
        }
    }

    private fun leaveCompleteGroup(shipmentNo: String) {
        try {
            completeHub?.send(ReceiptCompleteHubConfig.METHOD_LEAVE, shipmentNo)
            Log.d(TAG, "Left complete group $shipmentNo")
        } catch (e: Exception) {
            Log.e(TAG, "Complete leave failed", e)
        }
    }

    // ------------------------------------------------
    private fun scheduleReconnect() {

        if (isStopping || currentShipmentNo == null) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {

            // 2s, 4s, 8s ... up to 60s
            val wait = minOf(60_000L, 2_000L shl minOf(reconnectAttempt, 5))
            reconnectAttempt++

            delay(wait)

            if (isStopping || currentShipmentNo == null) return@launch

            if (hubConnection == null) connect()
            if (completeHub == null) startCompleteHub()
        }
    }

    private fun registerNetworkCallback() {

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                if (hubConnection == null || completeHub == null) {
                    reconnectAttempt = 0
                    if (hubConnection == null) connect()
                    if (completeHub == null) startCompleteHub()
                }
            }

            override fun onLost(network: Network) {
                updateNotification("No internet")
            }
        }

        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    // ------------------------------------------------
    override fun onDestroy() {

        isStopping = true
        reconnectJob?.cancel()

        currentShipmentNo?.let {
            leaveGroup(it)
            leaveCompleteGroup(it)
        }

        try {
            networkCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}

        try { hubConnection?.stop() } catch (_: Exception) {}
        try { completeHub?.stop() } catch (_: Exception) {}

        hubConnection = null
        completeHub = null
        scope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dispatch live sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Receipt sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}