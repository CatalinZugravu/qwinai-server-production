package com.cyberflux.qwinai.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cyberflux.qwinai.MyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ConversationCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return runBlocking(Dispatchers.IO) {
            try {
                val conversationDao = MyApp.database.conversationDao()
                val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

                // Only delete unsaved conversations that are older than 30 days
                val deletedCount = conversationDao.deleteOldConversations(thirtyDaysAgo)

                Timber.d("Conversation cleanup completed: deleted $deletedCount old unsaved conversations")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Error during conversation cleanup: ${e.message}")
                Result.failure()
            }
        }
    }
}