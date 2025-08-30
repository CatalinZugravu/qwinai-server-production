package com.cyberflux.qwinai.ads

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Manages user consent for personalized advertising across different ad networks.
 * Improved implementation with single-display logic.
 */
class ConsentManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "ad_consent_prefs"
        private const val PREF_CONSENT_GIVEN = "consent_given"
        private const val PREF_PERSONALIZED_ADS = "personalized_ads"
        private const val PREF_CONSENT_REGION = "consent_region"
        private const val PREF_CONSENT_DATE = "consent_date"

        // Region identifiers
        const val REGION_EEA = "eea" // European Economic Area
        const val REGION_CCPA = "ccpa" // California Consumer Privacy Act
        const val REGION_UNKNOWN = "unknown"
    }

    // Shared preferences to store consent
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Keep a weak reference to the current dialog to prevent window leaks
    private var currentDialog: WeakReference<AlertDialog>? = null

    // Region detection (basic implementation)
    private val userRegion by lazy {
        detectUserRegion()
    }

    // Check if we need to show the consent dialog
    fun shouldShowConsentDialog(): Boolean {
        return !prefs.getBoolean(PREF_CONSENT_GIVEN, false)
    }
    
    // Dismiss any active consent dialog to prevent window leaks
    fun dismissDialog() {
        try {
            currentDialog?.get()?.let { dialog ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                    Timber.d("🚫 Dismissed consent dialog to prevent window leak")
                }
            }
            currentDialog = null
        } catch (e: Exception) {
            Timber.e(e, "Error dismissing consent dialog")
        }
    }

    // Save user consent
    private fun saveConsent(consented: Boolean) {
        prefs.edit {
            putBoolean(PREF_CONSENT_GIVEN, true)
                .putBoolean(PREF_PERSONALIZED_ADS, consented)
                .putString(PREF_CONSENT_REGION, userRegion)
                .putLong(PREF_CONSENT_DATE, System.currentTimeMillis())
        }

        // Apply consent to Google AdMob
        applyConsentToAdMob(consented)

        // Apply consent to Huawei Ads Kit
        applyConsentToHuaweiAds(consented)

        Timber.d("User consent saved: personalized=$consented, region=$userRegion")
    }

    /**
     * Applies consent settings to Google AdMob
     */
    private fun applyConsentToAdMob(consented: Boolean) {
        try {
            // For Google ads
            val mobileAdsClass = Class.forName("com.google.android.gms.ads.MobileAds")

            // Get request configuration constants
            val requestConfigClass = Class.forName("com.google.android.gms.ads.RequestConfiguration")

            // TFCD = Tag For Child Directed treatment
            // We want to avoid treating as child-directed if user consented to personalized ads
            val tagForChildField = if (consented) {
                requestConfigClass.getField("TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE")
            } else {
                requestConfigClass.getField("TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE")
            }
            val consentStatus = tagForChildField.getInt(null)

            // Get current configuration
            val getRequestConfigMethod = mobileAdsClass.getMethod("getRequestConfiguration")
            val currentConfig = getRequestConfigMethod.invoke(null)

            // Create builder
            val toBuilderMethod = requestConfigClass.getMethod("toBuilder")
            val builder = toBuilderMethod.invoke(currentConfig)

            // Set tag for child-directed treatment
            val builderClass = builder.javaClass
            val setTagMethod = builderClass.getMethod("setTagForChildDirectedTreatment", Int::class.javaPrimitiveType)
            setTagMethod.invoke(builder, consentStatus)

            // Also set max ad content rating based on consent
            val setMaxAdContentRatingMethod = builderClass.getMethod("setMaxAdContentRating", String::class.java)
            val adContentRating = if (consented) "MA" else "G" // MA = Mature, G = General
            setMaxAdContentRatingMethod.invoke(builder, adContentRating)

            // Build new configuration
            val buildMethod = builderClass.getMethod("build")
            val newConfig = buildMethod.invoke(builder)

            // Set the new configuration
            val setRequestConfigMethod = mobileAdsClass.getMethod("setRequestConfiguration", requestConfigClass)
            setRequestConfigMethod.invoke(null, newConfig)

            Timber.d("Applied consent to Google AdMob: personalized=$consented, rating=$adContentRating")
        } catch (e: Exception) {
            Timber.e(e, "Error applying consent to Google AdMob")
        }
    }

    /**
     * Applies consent settings to Huawei Ads Kit using reflection
     */
    private fun applyConsentToHuaweiAds(consented: Boolean) {
        try {
            // Try to access Huawei Ads Kit classes using reflection
            val hwAdsClass = Class.forName("com.huawei.hms.ads.HwAds")

            // Get request options
            val getRequestOptionsMethod = hwAdsClass.getMethod("getRequestOptions")
            val requestOptions = getRequestOptionsMethod.invoke(null)

            // Get builder
            val requestOptionsClass = requestOptions.javaClass
            val toBuilderMethod = requestOptionsClass.getMethod("toBuilder")
            val builder = toBuilderMethod.invoke(requestOptions)

            // Set tag for child protection
            val builderClass = builder.javaClass
            val consentStatus = if (consented) 0 else 1 // 0 = not child-directed, 1 = child-directed
            val setTagMethod = builderClass.getMethod("setTagForChildProtection", Int::class.javaPrimitiveType)
            setTagMethod.invoke(builder, consentStatus)

            // Set non-personalized ads flag (opposite of consented)
            val setNonPersonalizedMethod = builderClass.getMethod("setNonPersonalizedAd", Int::class.javaPrimitiveType)
            val nonPersonalizedValue = if (consented) 0 else 1 // 0 = personalized, 1 = non-personalized
            setNonPersonalizedMethod.invoke(builder, nonPersonalizedValue)

            // Build new options
            val buildMethod = builderClass.getMethod("build")
            val newOptions = buildMethod.invoke(builder)

            // Set the new options
            val setRequestOptionsMethod = hwAdsClass.getMethod("setRequestOptions", requestOptionsClass)
            setRequestOptionsMethod.invoke(null, newOptions)

            Timber.d("Applied consent to Huawei Ads Kit: personalized=$consented")
        } catch (e: Exception) {
            // Huawei HMS not available or error occurred
            Timber.d("Huawei Ads Kit not available, skipping consent settings")
        }
    }

    /**
     * Detect user region based on locale and timezone.
     * This is a basic implementation and could be enhanced with IP geolocation.
     */
    private fun detectUserRegion(): String {
        try {
            // Get device locale
            val locale =
                context.resources.configuration.locales.get(0)

            // Check for EEA countries
            val eeaCountries = arrayOf(
                "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU",
                "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
                "IS", "LI", "NO"
            )

            if (locale.country in eeaCountries) {
                return REGION_EEA
            }

            // Check for California
            if (locale.country == "US") {
                // This is a very simple check - in production you'd want to use geolocation
                val timeZone = java.util.TimeZone.getDefault().id
                if (timeZone.contains("America/Los_Angeles")) {
                    return REGION_CCPA
                }
            }

            return REGION_UNKNOWN
        } catch (e: Exception) {
            Timber.e(e, "Error detecting user region")
            return REGION_UNKNOWN
        }
    }

    /**
     * Show the consent dialog with region-specific messaging
     * Now with tracking to prevent duplicate display
     */
    fun showConsentDialog(activity: Activity, onComplete: () -> Unit) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                Timber.e("Activity is finishing or destroyed, cannot show consent dialog")
                onComplete()
                return
            }

            // Dismiss any existing dialog first
            dismissDialog()

            // Determine message based on region
            val title = when (userRegion) {
                REGION_EEA -> "Your Privacy Choices (GDPR)"
                REGION_CCPA -> "Your Privacy Choices (CCPA)"
                else -> "Ads & Privacy"
            }

            val message = when (userRegion) {
                REGION_EEA -> "To provide you with a free app experience, we use advertising. Would you like to " +
                        "allow personalized ads tailored to your interests? Your data will be processed according " +
                        "to our Privacy Policy.\n\nYou can change this setting later in the app settings."

                REGION_CCPA -> "We use advertising to provide this app free of charge. California residents have the " +
                        "right to opt out of the sale of personal information. Would you like to allow personalized " +
                        "ads tailored to your interests?\n\nYou can change this setting later in the app settings."

                else -> "We use advertising to keep our app free. To provide personalized ads, " +
                        "we would like to use data collected from your device. " +
                        "\n\nWould you allow us to use your data for personalized advertising?"
            }

            // Only create and show the dialog if the activity is still valid
            if (!activity.isFinishing && !activity.isDestroyed) {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false) // User must make a choice
                    .setPositiveButton("Allow Personalized Ads") { _, _ ->
                        currentDialog = null
                        saveConsent(true)
                        onComplete()
                    }
                    .setNegativeButton("Use Non-Personalized Ads") { _, _ ->
                        currentDialog = null
                        saveConsent(false)
                        onComplete()
                    }
                    .setOnDismissListener {
                        currentDialog = null
                    }
                    .create()
                
                // Store weak reference before showing
                currentDialog = WeakReference(dialog)
                dialog.show()
            } else {
                // If activity is no longer valid, just call the completion handler
                onComplete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing consent dialog")
            currentDialog = null
            // Still call onComplete in case of error to avoid blocking the app
            onComplete()
        }
    }
}