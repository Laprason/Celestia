package com.hoyopass.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "pass_reminder"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, "パス更新リマインダー", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "月パス・シーズンパスの期限が近づくと通知します" }
            mgr.createNotificationChannel(ch)
        }
    }

    /** タップで該当ゲームの課金センターを開く通知。 */
    fun notify(
        context: Context, game: GameDef, type: PassType, passName: String, daysLeft: Long, url: String
    ) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, (game.id + passName).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = if (type == PassType.SEASON) {
            "アップデートが来ました。タップで新シーズンのパスを購入できます"
        } else {
            if (daysLeft < 0) "期限切れです。タップで課金センターを開きます"
            else "期限まであと${daysLeft}日。タップで課金センターを開きます"
        }

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${game.name}：${passName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(context)
            .notify((game.id + passName).hashCode(), n)
    }
}
