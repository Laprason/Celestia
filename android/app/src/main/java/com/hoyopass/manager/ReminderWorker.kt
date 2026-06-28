package com.hoyopass.manager

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 定期的にパスの期限を確認し、しきい値以内なら通知を出す。 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = PassRepository(applicationContext)
        val data = repo.snapshot()
        val today = logicalToday()

        data.passes.values.forEach { entry ->
            val rem = entry.remaining(today)
            val shouldNotify = when (entry.type) {
                // 月パスは期限の lead 日前から
                PassType.MONTHLY -> rem.daysLeft <= data.leadDays
                // シーズンはアップデート当日（残り0日）かつ正午以降＝アプデ後
                PassType.SEASON -> rem.daysLeft == 0L && LocalTime.now().hour >= SEASON_UPDATE_HOUR
            }
            if (shouldNotify) {
                val game = gameById(entry.gameId)
                val passName = if (entry.type == PassType.MONTHLY) game.monthName else game.seasonName
                NotificationHelper.notify(
                    applicationContext, game, entry.type, passName, rem.daysLeft, data.urlFor(entry.gameId)
                )
            }
        }
        PaymentWidget().updateAll(applicationContext) // ウィジェットも最新化
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "pass_reminder_periodic"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}
