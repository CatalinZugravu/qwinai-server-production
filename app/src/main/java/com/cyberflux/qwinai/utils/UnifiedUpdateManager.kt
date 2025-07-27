package com.cyberflux.qwinai.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import com.cyberflux.qwinai.MyApp
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import timber.log.Timber
import androidx.core.content.edit

/**
 * A unified update manager that handles both Google Play and Huawei HMS updates
 * with support for forced immediate updates on Google devices
 */
class UnifiedUpdateManager(private val context: Context) {
    companion object {
        private const val UPDATE_REQUEST_CODE = 500
        private const val PREFS_NAME = "update_manager_prefs"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_APP_FIRST_RUN = "is_app_first_run_session"
        private const val UPDATE_INTERVAL = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
    }

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    /**
     * Initialize the update manager - must be called before using other methods
     */
    fun initialize() {
        try {
            // Reset first run flag on initialization
            resetFirstRunFlag()

            // Only initialize Google Play components on non-Huawei devices
            if (!isHuaweiDevice()) {
                appUpdateManager = AppUpdateManagerFactory.create(context)
                installStateUpdatedListener = InstallStateUpdatedListener { state ->
                    when (state.installStatus()) {
                        InstallStatus.DOWNLOADED -> {
                            // Update has been downloaded, prompt user to complete installation
                            Timber.d("Update downloaded, prompting for installation")
                            appUpdateManager?.completeUpdate()
                        }
                        InstallStatus.INSTALLED -> {
                            Timber.d("Update installed successfully")
                        }
                        else -> {
                            Timber.d("Install state: ${state.installStatus()}")
                        }
                    }
                }
                appUpdateManager?.registerListener(installStateUpdatedListener!!)
                Timber.d("Google Play update manager initialized")
            } else {
                Timber.d("Skipping Google Play update manager on Huawei device")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize update manager: ${e.message}")
        }
    }

    /**
     * Check if this is a Huawei device with HMS Core available
     * Using the centralized detection method
     */
    fun isHuaweiDevice(): Boolean {
        return MyApp.isHuaweiDevice()
    }

    /**
     * Check for updates with throttling to prevent excessive update checks
     */
    fun checkForUpdates(activity: Activity, force: Boolean = false): Boolean {
        // Get first run status
        val prefs = getPrefs()
        val isFirstRun = prefs.getBoolean(KEY_APP_FIRST_RUN, true)

        // Skip if not first run, not forced, and we've checked recently
        if (!force && !isFirstRun && !shouldCheckForUpdates()) {
            Timber.d("Skipping update check due to throttling")
            return false
        }

        // Mark app as no longer in first run for this session
        if (isFirstRun) {
            prefs.edit { putBoolean(KEY_APP_FIRST_RUN, false) }
        }

        // Record this check time
        recordUpdateCheck()

        if (isHuaweiDevice()) {
            // Use HMS update mechanism for Huawei devices
            return checkHuaweiUpdates()
        } else {
            // Use Google Play update mechanism for Google devices
            return checkGoogleUpdates(activity)
        }
    }

    /**
     * Check if we should check for updates based on time elapsed
     */
    private fun shouldCheckForUpdates(): Boolean {
        val prefs = getPrefs()
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val now = System.currentTimeMillis()

        // Check if enough time has passed since the last check
        return (now - lastCheck) >= UPDATE_INTERVAL
    }

    /**
     * Record that we checked for updates
     */
    private fun recordUpdateCheck() {
        val prefs = getPrefs()
        prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()) }
    }

    /**
     * Get shared preferences for update manager
     */
    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Reset first run flag (useful for app restart)
     */
    fun resetFirstRunFlag() {
        val prefs = getPrefs()
        prefs.edit { putBoolean(KEY_APP_FIRST_RUN, true) }
    }

    /**
     * Check for updates on Huawei devices
     */
    private fun checkHuaweiUpdates(): Boolean {
        try {
            Timber.d("Checking for updates on Huawei device")

            // Send broadcast to check for updates
            val intent = Intent("com.huawei.hms.update.action.CHECK_UPDATE")
                .setPackage("com.huawei.appmarket")
                .putExtra("package", context.packageName)
            context.sendBroadcast(intent)

            // Also register for updates
            val registerIntent = Intent("com.huawei.hms.update.action.REGISTER")
                .setPackage("com.huawei.appmarket")
                .putExtra("package", context.packageName)
            context.sendBroadcast(registerIntent)

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error triggering Huawei update check: ${e.message}")
            return false
        }
    }

    /**
     * Check for updates on Google devices and force immediate update
     */
    private fun checkGoogleUpdates(activity: Activity): Boolean {
        try {
            if (appUpdateManager == null) {
                Timber.e("Update manager not initialized")
                return false
            }

            val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                    // An update is available and can be immediate, start update
                    Timber.d("Update available, starting immediate update")
                    startImmediateUpdate(activity, appUpdateInfo)
                } else {
                    Timber.d("No update available or immediate update not allowed")
                }
            }

            appUpdateInfoTask.addOnFailureListener { exception ->
                Timber.e(exception, "Failed to check for updates: ${exception.message}")
            }

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error checking for Google updates: ${e.message}")
            return false
        }
    }

    /**
     * Start immediate update that blocks app usage until update is complete
     */
    private fun startImmediateUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                .setAllowAssetPackDeletion(true)
                .build()

            appUpdateManager!!.startUpdateFlow(
                appUpdateInfo,
                activity,
                updateOptions
            )
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e, "Error launching update flow: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Unknown error launching update: ${e.message}")
        }
    }

    /**
     * Check if a previously started update is in progress
     * (Call in Activity.onResume to ensure updates aren't interrupted)
     */
    fun checkOngoingUpdates(activity: Activity) {
        if (isHuaweiDevice() || appUpdateManager == null) {
            return
        }

        try {
            appUpdateManager!!.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                // If an in-app update is already running, resume the update
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    Timber.d("Update in progress, resuming")
                    try {
                        val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                            .setAllowAssetPackDeletion(true)
                            .build()

                        appUpdateManager!!.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            updateOptions
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Timber.e(e, "Error resuming update: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking ongoing updates: ${e.message}")
        }
    }

    /**
     * Handle the result of the update activity
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode == UPDATE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Timber.d("Update flow failed! Result code: $resultCode")
                // User rejected or update failed, you can force the user again
                // or implement a blocking UI until they update
            } else {
                Timber.d("Update flow succeeded! Result code: $resultCode")
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            if (appUpdateManager != null && installStateUpdatedListener != null) {
                appUpdateManager!!.unregisterListener(installStateUpdatedListener!!)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up update manager: ${e.message}")
        }
    }
}