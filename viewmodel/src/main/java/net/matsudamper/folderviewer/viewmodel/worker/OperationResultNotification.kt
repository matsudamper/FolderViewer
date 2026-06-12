package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

internal object OperationResultNotification {
    private const val CHANNEL_ID = "operation_result_channel"

    data class Content(
        val title: String,
        val text: String,
        val smallIcon: Int,
    )

    fun notify(
        context: Context,
        notificationId: Int,
        content: Content,
        contentIntent: PendingIntent,
    ) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setTicker(content.title)
            .setSmallIcon(content.smallIcon)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ファイル操作の結果",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "ファイル操作の完了、エラー、操作が必要な状態を表示します"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
