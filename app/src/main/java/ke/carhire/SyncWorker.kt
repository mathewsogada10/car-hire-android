package ke.carhire

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ke.carhire.security.Vault
import ke.carhire.sync.SyncClient

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CarHireApp
        val vault = Vault(app)
        if (vault.get("backendUrl").isNullOrEmpty()) return Result.success()
        return try {
            SyncClient(app.repository, vault, app).run()
            Result.success()
        } catch (e: java.io.IOException) {
            app.repository.dao.meta(
                Meta("syncError", "Network interrupted; durable operations will retry")
            )
            Result.retry()
        } catch (e: IllegalStateException) {
            app.repository.dao.meta(Meta("syncError", e.message ?: "Sync requires attention"))
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "carhire-sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build(),
                )
        }
    }
}
