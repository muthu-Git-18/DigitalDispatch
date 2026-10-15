package com.united.digitaldispatch.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** safety net: tries again every 15 minutes while there is a network */
    fun schedulePeriodicSync(context: Context) {

        val request = PeriodicWorkRequestBuilder<DispatchSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                "dispatch_periodic_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /** upload now (queued after a running upload, never in parallel with it) */
    fun triggerImmediateSync(context: Context) {

        val request = OneTimeWorkRequestBuilder<DispatchSyncWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                "dispatch_immediate_sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
    }
}