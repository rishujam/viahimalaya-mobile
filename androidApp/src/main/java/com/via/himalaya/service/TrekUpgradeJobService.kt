package com.via.himalaya.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.via.himalaya.domain.repo.TrekRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Redoes every trek downloaded before routes existed, once the phone has signal.
 *
 * Such a download has the retired coordinate file and no routes file, and this
 * build has no way to draw it (HANDOFF section 53 item 4). Scheduled straight after
 * the app updates ([AppUpdatedReceiver]), so it usually runs before the user ever
 * opens the trek - the update itself tends to arrive on Wi-Fi. Android's own job
 * scheduler holds it until there is a network, app open or not, and retries with
 * backoff while a trek fails. Opening a trek with signal does the same job
 * (TrekDetailViewModel), and TrekRepository serialises the two.
 *
 * JobScheduler rather than WorkManager: it is part of Android, so no new library,
 * and "once, when there is a network, retry on failure" is all this needs.
 */
class TrekUpgradeJobService : JobService(), KoinComponent {

    private val trekRepository: TrekRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val allDone = try {
                trekRepository.upgradeDownloadedTreks()
            } catch (e: Exception) {
                Log.w(TAG, "Upgrading old downloads failed", e)
                false
            }
            Log.d(TAG, if (allDone) "Old downloads are up to date" else "Some old downloads failed; retrying later")
            jobFinished(params, !allDone)
        }
        return true
    }

    /** Android stopped it (signal lost, say): ask to run again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    companion object {
        private const val TAG = "TrekUpgradeJob"
        private const val JOB_ID = 4201
        private const val BACKOFF_MS = 60_000L

        /**
         * Schedules the job. Safe to call often: the same id replaces the pending
         * one, and with nothing to upgrade the job ends at once.
         */
        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, TrekUpgradeJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val result = scheduler.schedule(job)
            Log.d(TAG, if (result == JobScheduler.RESULT_SUCCESS) "Scheduled" else "Could not schedule")
        }
    }
}

/**
 * Android sends MY_PACKAGE_REPLACED to the app itself right after it is updated,
 * whether or not it is opened. That is the moment to queue the upgrade of old
 * downloads.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TrekUpgradeJobService.schedule(context)
        }
    }
}
