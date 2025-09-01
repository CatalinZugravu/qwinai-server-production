package com.cyberflux.qwinai.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.NotificationConstants
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.utils.PrefsManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreditResetWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        try {
            // Get the CreditManager instance - this will automatically trigger 
            // checkAndResetDaily() if needed, without forcing a reset
            val creditManager = CreditManager.getInstance(applicationContext)
            
            // Simply check credits to trigger the natural daily reset check
            // The CreditManager will handle the daily reset logic internally
            val chatCredits = creditManager.getChatCredits()
            val imageCredits = creditManager.getImageCredits()
            
            Timber.d("CreditResetWorker: Credits checked - Chat: $chatCredits, Image: $imageCredits")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error in CreditResetWorker: ${e.message}")
            return Result.failure()
        }
    }

    private fun sendResetNotification(context: Context) {
        // Skip notification for subscribers
        if (PrefsManager.isSubscribed(context)) {
            return
        }

        // Create an intent that opens the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Always use FLAG_IMMUTABLE for security
        val pendingIntentFlag =
            PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlag)

        // Create the notification - using the global constants
        val builder = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using default icon
            .setContentTitle("Free Credits Reset")
            .setContentText("Your 10 free daily credits have been reset. Open the app to start chatting!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Show notification
        try {
            with(NotificationManagerCompat.from(context)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {
                    notify(NotificationConstants.NOTIFICATION_ID, builder.build())
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }

}