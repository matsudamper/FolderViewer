package net.matsudamper.folderviewer.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Inject
import net.matsudamper.folderviewer.OperationDetailActivity
import net.matsudamper.folderviewer.viewmodel.worker.OperationNotificationIntentFactory

@Module
@InstallIn(SingletonComponent::class)
internal interface OperationNotificationModule {
    @Binds
    fun bindOperationNotificationIntentFactory(
        impl: OperationNotificationIntentFactoryImpl,
    ): OperationNotificationIntentFactory
}

internal class OperationNotificationIntentFactoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OperationNotificationIntentFactory {
    override fun createUploadDetailIntent(workerId: String): PendingIntent {
        return createPendingIntent(
            requestCode = workerId.hashCode(),
            intent = OperationDetailActivity.createUploadDetailIntent(context, workerId),
        )
    }

    override fun createPasteDetailIntent(jobId: Long): PendingIntent {
        return createPendingIntent(
            requestCode = jobId.toInt(),
            intent = OperationDetailActivity.createPasteDetailIntent(context, jobId),
        )
    }

    override fun createDeleteDetailIntent(operationId: Long): PendingIntent {
        return createPendingIntent(
            requestCode = operationId.toInt(),
            intent = OperationDetailActivity.createDeleteDetailIntent(context, operationId),
        )
    }

    private fun createPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
