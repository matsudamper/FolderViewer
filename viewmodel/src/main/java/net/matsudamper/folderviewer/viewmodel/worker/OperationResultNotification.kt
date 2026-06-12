package net.matsudamper.folderviewer.viewmodel.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.navigation3.runtime.NavKey
import net.matsudamper.folderviewer.navigation.NotificationDestination

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
        destination: NavKey,
    ) {
        createNotificationChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        NotificationDestination.putExtras(launchIntent, destination)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setTicker(content.title)
            .setSmallIcon(content.smallIcon)
            .setContentIntent(pendingIntent)
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
