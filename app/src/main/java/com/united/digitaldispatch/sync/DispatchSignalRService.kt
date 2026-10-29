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
 * Live "return sync" through SignalR.
 *
 * Runs only while a shipment is open on the Details screen:
 *   start(ctx, shipmentNo)        -> connect, join the group of that shipment,
 *                                    download the changes once
 *   server event "DispatchUpdated"-> SyncManager.pullChanges(...)
 *   start(ctx, otherShipmentNo)   -> leave the old group, join the new one
 *   stop(ctx, shipmentNo)         -> leave the group and disconnect
 *                                    (ignored if the service already moved to another shipment)
 */
class DispatchSignalRService : Service() {

    companion object {

        private const val TAG = "DispatchSignalR"
        private const val EXTRA_SHIPMENT_NO = "extra_shipment_no"
        private const val ACTION_STOP = "action_stop"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "dispatch_signalr_channel"

        fun start(context: Context, shipmentNo: String) {

            if (!DispatchHubConfig.enabled) return

            val intent = Intent(context, DispatchSignalRService::class.java)
                .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the live sync service", e)
            }
        }

        /** Only stops the service if [shipmentNo] is still the shipment it is serving. */
        fun stop(context: Context, shipmentNo: String) {

            val intent = Intent(context, DispatchSignalRService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)

            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send stop to the live sync service", e)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var hubConnection: HubConnection? = null
    private lateinit var notificationManager: NotificationManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var currentShipmentNo: String? = null
    private var joinedShipmentNo: String? = null

    private var reconnectJob: Job? = null

    @Volatile
    private var isConnecting = false

    @Volatile
    private var isStopping = false

    @Volatile
    private var reconnectAttempt = 0

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

        // ---- stop request from a screen ----
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

            currentShipmentNo = shipmentNo
            joinedShipmentNo = null

            if (old != null) leaveGroup(old)

            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                joinGroup(shipmentNo)
                SyncManager.pullChanges(applicationContext, shipmentNo)
            } else {
                connect()
            }

        } else if (hubConnection == null) {
            connect()
        }

        // tied to the open screen -> do not restart on its own
        return START_NOT_STICKY
    }

    // ------------------------------------------------
    private fun connect() {

        if (isStopping || isConnecting || hubConnection != null) return
        if (!DispatchHubConfig.enabled) return

        isConnecting = true

        val hub = HubConnectionBuilder
            .create(DispatchHubConfig.HUB_URL)
            .build()

        // detect dead connections quickly (mobile networks drop silently)
        hub.keepAliveInterval = 10_000L
        hub.serverTimeout = 30_000L

        // server tells us a change happened
        hub.on(
            DispatchHubConfig.EVENT_CHANGED,
            { changeId: Long, shipmentNo: String ->
                Log.d(TAG, "Signal received: shipment=$shipmentNo changeId=$changeId")
                if (shipmentNo == currentShipmentNo) {
                    SyncManager.pullChanges(applicationContext, shipmentNo, changeId)
                }
            },
            Long::class.javaObjectType,
            String::class.java
        )

        hub.onClosed { error ->
            // an old hub closing must not touch the new one
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

                // service moved on / stopped while we were connecting
                if (hubConnection !== hub || isStopping) {
                    try { hub.stop() } catch (_: Exception) {}
                    return@launch
                }

                reconnectAttempt = 0
                isConnecting = false

                updateNotification("Listening for dispatch updates")

                currentShipmentNo?.let { shipmentNo ->
                    // join FIRST, then pull: nothing can be missed between the two
                    joinGroup(shipmentNo)
                    SyncManager.pullChanges(applicationContext, shipmentNo)
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
                ?.invoke(DispatchHubConfig.METHOD_JOIN, shipmentNo)
                ?.subscribe(
                    {
                        joinedShipmentNo = shipmentNo
                        Log.d(TAG, "Joined group $shipmentNo")
                    },
                    { error ->
                        Log.e(TAG, "Join rejected by server for $shipmentNo", error)
                    }
                )
        } catch (e: Exception) {
            Log.e(TAG, "Join failed", e)
        }
    }

    private fun leaveGroup(shipmentNo: String) {
        try {
            hubConnection?.send(DispatchHubConfig.METHOD_LEAVE, shipmentNo)
            Log.d(TAG, "Left group $shipmentNo")
        } catch (e: Exception) {
            Log.e(TAG, "Leave failed", e)
        }
    }

    // ------------------------------------------------
    private fun scheduleReconnect() {

        if (isStopping || currentShipmentNo == null) return
        if (reconnectJob?.isActive == true) return   // one pending retry at a time

        reconnectJob = scope.launch {

            // 2s, 4s, 8s ... up to 60s
            val wait = minOf(60_000L, 2_000L shl minOf(reconnectAttempt, 5))
            reconnectAttempt++

            delay(wait)

            if (!isStopping && hubConnection == null && currentShipmentNo != null) connect()
        }
    }

    private fun registerNetworkCallback() {

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                if (hubConnection == null) {
                    reconnectAttempt = 0
                    connect()
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

        currentShipmentNo?.let { leaveGroup(it) }

        try {
            networkCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }

        try {
            hubConnection?.stop()
        } catch (_: Exception) {
        }

        hubConnection = null
        scope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Dispatch live sync",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dispatch sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}