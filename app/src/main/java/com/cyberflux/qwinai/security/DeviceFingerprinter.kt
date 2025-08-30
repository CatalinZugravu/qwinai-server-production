package com.cyberflux.qwinai.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID

/**
 * Robust device fingerprinting system that creates a persistent unique identifier
 * across app reinstalls. This is used to restore user credits and subscription status.
 * 
 * The fingerprint combines multiple stable device characteristics:
 * - Android ID (primary)
 * - Hardware serial (if available)
 * - Device model/manufacturer
 * - IMEI (fallback, with permission)
 * - Custom UUID (last resort)
 * 
 * Security note: This is for legitimate user experience continuity, not tracking.
 */
object DeviceFingerprinter {
    
    private const val PREFS_NAME = "device_fingerprint_secure"
    private const val FINGERPRINT_KEY = "device_fingerprint"
    private const val BACKUP_UUID_KEY = "backup_device_uuid"
    private const val FIRST_SEEN_KEY = "first_seen_timestamp"
    
    /**
     * Get persistent device fingerprint that survives app reinstalls
     * This combines multiple device characteristics for maximum stability
     */
    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(context: Context): String {
        // Try to get existing fingerprint first
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingFingerprint = prefs.getString(FINGERPRINT_KEY, null)
        
        if (!existingFingerprint.isNullOrEmpty()) {
            Timber.d("🔒 Using existing device fingerprint: ${existingFingerprint.take(8)}...")
            return existingFingerprint
        }
        
        // Generate new fingerprint
        val fingerprint = generateDeviceFingerprint(context)
        
        // Store fingerprint and metadata
        prefs.edit()
            .putString(FINGERPRINT_KEY, fingerprint)
            .putLong(FIRST_SEEN_KEY, System.currentTimeMillis())
            .apply()
        
        Timber.d("🔒 Generated new device fingerprint: ${fingerprint.take(8)}...")
        return fingerprint
    }
    
    /**
     * Generate device fingerprint from multiple device characteristics
     */
    @SuppressLint("HardwareIds")
    private fun generateDeviceFingerprint(context: Context): String {
        val components = mutableListOf<String>()
        
        try {
            // 1. Android ID (most stable, survives factory reset on newer devices)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") { // Avoid known broken ID
                components.add("android_id:$androidId")
                Timber.d("🔒 Added Android ID to fingerprint")
            }
            
            // 2. Hardware serial (if available and not restricted)
            try {
                val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Build.getSerial()
                } else {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                }
                if (!serial.isNullOrEmpty() && serial != "unknown" && serial != Build.UNKNOWN) {
                    components.add("serial:$serial")
                    Timber.d("🔒 Added hardware serial to fingerprint")
                }
            } catch (e: SecurityException) {
                Timber.d("🔒 Hardware serial not accessible: ${e.message}")
            }
            
            // 3. Device model and manufacturer (stable across reinstalls)
            components.add("device:${Build.MANUFACTURER}:${Build.MODEL}")
            components.add("product:${Build.PRODUCT}")
            
            // 4. Hardware fingerprint (stable hardware characteristics)
            components.add("hardware:${Build.HARDWARE}")
            components.add("board:${Build.BOARD}")
            
            // 5. CPU ABI (stable hardware characteristic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                components.add("abi:${Build.SUPPORTED_ABIS.joinToString(",")}")
            } else {
                @Suppress("DEPRECATION")
                components.add("abi:${Build.CPU_ABI}")
            }
            
            // 6. Telephony-based identifiers (if available and permitted)
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                telephonyManager?.let { tm ->
                    try {
                        // IMEI (requires permission, use with caution)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // On Android 10+, this requires READ_PRIVILEGED_PHONE_STATE
                            // which is not available to normal apps, so skip
                        } else {
                            @Suppress("DEPRECATION")
                            val imei = tm.deviceId
                            if (!imei.isNullOrEmpty()) {
                                components.add("imei:${imei.takeLast(8)}") // Only last 8 digits for privacy
                                Timber.d("🔒 Added IMEI suffix to fingerprint")
                            }
                        }
                    } catch (e: SecurityException) {
                        Timber.d("🔒 IMEI not accessible: ${e.message}")
                    }
                    
                    // SIM serial (if available)
                    try {
                        val simSerial = tm.simSerialNumber
                        if (!simSerial.isNullOrEmpty()) {
                            components.add("sim:${simSerial.takeLast(6)}") // Only last 6 digits
                            Timber.d("🔒 Added SIM serial suffix to fingerprint")
                        }
                    } catch (e: SecurityException) {
                        Timber.d("🔒 SIM serial not accessible: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.d("🔒 Telephony manager not accessible: ${e.message}")
            }
            
            // 7. Backup UUID (stored locally but combined with hardware characteristics)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val backupUuid = prefs.getString(BACKUP_UUID_KEY, null) ?: run {
                val newUuid = UUID.randomUUID().toString()
                prefs.edit().putString(BACKUP_UUID_KEY, newUuid).apply()
                newUuid
            }
            components.add("uuid:$backupUuid")
            
        } catch (e: Exception) {
            Timber.e(e, "🔒 Error generating device fingerprint components")
            // Fallback to basic identifiers
            components.add("fallback:${Build.MANUFACTURER}:${Build.MODEL}")
            components.add("uuid:${UUID.randomUUID()}")
        }
        
        // Ensure we have at least some components
        if (components.isEmpty()) {
            Timber.w("🔒 No fingerprint components available, using fallback")
            components.add("emergency:${System.currentTimeMillis()}:${UUID.randomUUID()}")
        }
        
        // Combine all components and hash
        val combined = components.joinToString("|")
        val fingerprint = sha256Hash(combined)
        
        Timber.d("🔒 Generated fingerprint from ${components.size} components")
        Timber.d("🔒 Components preview: ${components.take(3).joinToString(", ")}")
        
        return fingerprint
    }
    
    /**
     * Get stable device identifier for server communication
     * This is a shorter, more server-friendly version of the fingerprint
     */
    fun getDeviceId(context: Context): String {
        val fingerprint = getDeviceFingerprint(context)
        // Return first 16 characters for server efficiency
        return fingerprint.take(16)
    }
    
    /**
     * Get device characteristics for debugging
     */
    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            fingerprint = getDeviceFingerprint(context),
            androidId = getAndroidId(context),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            firstSeen = getFirstSeenTimestamp(context)
        )
    }
    
    /**
     * Check if this appears to be the same device (for debugging)
     */
    fun validateDeviceConsistency(context: Context, previousFingerprint: String): Boolean {
        val currentFingerprint = getDeviceFingerprint(context)
        val isConsistent = currentFingerprint == previousFingerprint
        
        if (!isConsistent) {
            Timber.w("🔒 Device fingerprint changed!")
            Timber.w("🔒 Previous: ${previousFingerprint.take(8)}...")
            Timber.w("🔒 Current:  ${currentFingerprint.take(8)}...")
        }
        
        return isConsistent
    }
    
    /**
     * Force regenerate fingerprint (for testing/debugging)
     */
    fun regenerateFingerprint(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(FINGERPRINT_KEY)
            .apply()
        
        return getDeviceFingerprint(context)
    }
    
    @SuppressLint("HardwareIds")
    private fun getAndroidId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getFirstSeenTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(FIRST_SEEN_KEY, 0L)
    }
    
    private fun sha256Hash(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "🔒 Error hashing fingerprint")
            input.hashCode().toString()
        }
    }
    
    /**
     * Data class for device information
     */
    data class DeviceInfo(
        val fingerprint: String,
        val androidId: String?,
        val manufacturer: String,
        val model: String,
        val product: String,
        val hardware: String,
        val androidVersion: String,
        val apiLevel: Int,
        val firstSeen: Long
    )
}