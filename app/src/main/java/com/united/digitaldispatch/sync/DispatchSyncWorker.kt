package com.united.digitaldispatch.sync

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads the Dispatch and PSW Dispatch bales that are still PENDING / FAILED.
 * Runs only when WorkManager says there is a network.
 */
class DispatchSyncWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val repository = MasterDataRepository(
            ApiClient.instance,
            AppDatabase.getInstance(ctx)
        )

        val dispatch = repository.uploadPendingDispatchDetails().getOrNull()
        val psw = repository.uploadPendingPswDetails().getOrNull()

        if (dispatch == null || psw == null) {
            // network / server problem -> try again later (give up this round after 5 tries,
            // the periodic sync picks it up again)
            return@withContext if (runAttemptCount >= 5) Result.success() else Result.retry()
        }

        val uploaded = dispatch.uploaded + psw.uploaded
        val remaining = dispatch.remaining + psw.remaining

        if (uploaded > 0) {
            showToast("Dispatch bales uploaded: $uploaded")
        }

        // keep going only while the server is accepting bales (no endless loop on failures)
        if (remaining > 0 && uploaded > 0) {
            SyncScheduler.triggerImmediateSync(ctx)
        }

        Result.success()
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}