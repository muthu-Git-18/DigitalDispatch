package com.united.digitaldispatch.sync

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository
import com.united.digitaldispatch.Dispatch.models.UploadSummary
import com.united.digitaldispatch.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads the Dispatch, PSW Dispatch and Receipt bales that are still PENDING / FAILED.
 * Runs only when WorkManager says there is a network.
 * One module failing never stops the others; add new modules as one line in [uploaders].
 */
class DispatchSyncWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val db = AppDatabase.getInstance(ctx)
        val master = MasterDataRepository(ApiClient.instance, db)
        val receipt = ReceiptRepository(ApiClient.instance, db)

        // add PSW Receipt / GTD here later, one line each
        val uploaders: List<suspend () -> kotlin.Result<UploadSummary>> = listOf(
            { master.uploadPendingDispatchDetails() },
            { master.uploadPendingPswDetails() },
            { receipt.uploadPendingReceiptDetails() }
        )

        var uploaded = 0
        var remaining = 0
        var anyFailed = false

        for (upload in uploaders) {
            val summary = upload().getOrNull()
            if (summary == null) {
                anyFailed = true            // this module failed, the others still run
            } else {
                uploaded += summary.uploaded
                remaining += summary.remaining
            }
        }

        if (uploaded > 0) {
            showToast("Bales uploaded: $uploaded")
        }

        // keep going only while the server is accepting bales (no endless loop on failures)
        if (remaining > 0 && uploaded > 0) {
            SyncScheduler.triggerImmediateSync(ctx)
        }

        // network / server problem in any module -> try again later
        // (give up this round after 5 tries, the periodic sync picks it up again)
        if (anyFailed && runAttemptCount < 5) Result.retry() else Result.success()
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}