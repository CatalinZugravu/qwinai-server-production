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
import com.cyberflux.qwinai.utils.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreditResetWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        // Get the app context
        val appContext = applicationContext

        // Get the prefs
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastResetDate = prefs.getLong("last_reset_date", 0L)
        val currentDate = System.currentTimeMillis()

        // Check if we need to reset
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val isSameDay = sdf.format(Date(lastResetDate)) == sdf.format(Date(currentDate))

        if (!isSameDay) {
            // Reset counters
            prefs.edit {
                putInt("free_messages_left", 10)  // CHANGED: from 5 to 10
                putInt("ads_watched_today", 0)
                putLong("last_reset_date", currentDate)
            }

            // Don't send notification here - CreditManager will handle unified notification
            // Only send notification if user is not subscribed
            // if (!PrefsManager.isSubscribed(appContext)) {
            //     sendResetNotification(appContext)
            // }
        }

        return Result.success()
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