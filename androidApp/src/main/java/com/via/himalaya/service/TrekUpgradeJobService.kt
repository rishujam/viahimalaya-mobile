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
 * Brings every downloaded trek up to date, once the phone has signal: redoes a
 * download from before routes, which has the retired coordinate file and nothing
 * this build can draw (HANDOFF section 53 item 4), and one whose routes file the
 * server has since rebuilt. A download that is current costs one small API call.
 * Scheduled straight after
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
                trekRepository.refreshDownloadedTreks()
            } catch (e: Exception) {
                Log.w(TAG, "Upgrading old downloads failed", e)
                false
            }
            Log.d(TAG, if (allDone) "Downloads are up to date" else "Some downloads failed to refresh; retrying later")
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
