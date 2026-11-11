package dev.jdtech.jellyfin.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.jdtech.jellyfin.repository.UserDataSyncRequester
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Enqueues [SyncWorker], which sends the watch progress, played state and favourites that could
 * not reach the server. It is asked at app start and whenever a write is marked to be synced, so
 * progress made while the server was unreachable does not wait for the next start (#281).
 *
 * The work is unique and kept: asking while a run is waiting for the network or for a retry does
 * nothing. Call it off the main thread, because the first call sets up WorkManager (#250).
 */
@Singleton
class UserDataSync @Inject constructor(private val workManager: Provider<WorkManager>) :
    UserDataSyncRequester {

    override fun request() = request(initialDelaySeconds = 0)

    fun request(initialDelaySeconds: Long) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val syncWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                .build()

        workManager
            .get()
            .enqueueUniqueWork(
                uniqueWorkName = UNIQUE_WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.KEEP,
                request = syncWorkRequest,
            )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "syncUserData"
    }
}
