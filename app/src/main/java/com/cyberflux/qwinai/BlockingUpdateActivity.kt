package com.cyberflux.qwinai

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.cyberflux.qwinai.utils.BaseThemedActivity
import com.cyberflux.qwinai.utils.UiUtils
import timber.log.Timber

/**
 * Activity that blocks app usage until user updates
 * Use this if you want to completely prevent app usage when an update is required
 */
class BlockingUpdateActivity : BaseThemedActivity() {

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Don't allow back navigation
            Toast.makeText(this@BlockingUpdateActivity, "Please update the app to continue", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_blocking_update)
        
        // Register the back pressed callback
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Get the "Update Now" button
        val updateButton = findViewById<Button>(R.id.buttonUpdate)

        // Set up click listener to open the appropriate store
        updateButton.setOnClickListener {
            openAppStore()
        }
    }

    /**
     * Open appropriate app store based on device type
     */
    private fun openAppStore() {
        try {
            val packageName = packageName
            val updateManager = MyApp.getUpdateManager()

            if (updateManager != null && updateManager.isHuaweiDevice()) {
                // Open Huawei AppGallery
                try {
                    val intent = Intent("com.huawei.appmarket.intent.action.AppDetail")
                        .setPackage("com.huawei.appmarket")
                        .putExtra("package", packageName)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    // If AppGallery app is not available, try to open browser
                    val webIntent = Intent(Intent.ACTION_VIEW,
                        "https://appgallery.huawei.com/app/$packageName".toUri())
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(webIntent)
                }
            } else {
                // Open Google Play Store for non-Huawei devices
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                        "market://details?id=$packageName".toUri())
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    // If Play Store app is not available, open in browser
                    val webIntent = Intent(Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$packageName".toUri())
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(webIntent)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open app store: ${e.message}")
            Toast.makeText(this, "Could not open app store. Please update manually.", Toast.LENGTH_LONG).show()
        }
    }

}