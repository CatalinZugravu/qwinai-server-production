package com.cyberflux.qwinai.credits

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cyberflux.qwinai.MainActivity
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.utils.PrefsManager
import timber.log.Timber
import java.security.MessageDigest
import java.util.*

/**
 * Secure Credit Manager for handling separate credit systems for Chat and Image Generation
 * Features:
 * - Separate credit pools for chat and image generation
 * - Daily credit reset mechanism
 * - Anti-cheat protection using encrypted storage and hash verification
 * - Daily 20 credits baseline + up to 50 additional credits from ads
 * - Credits don't stack - reset daily to prevent accumulation
 */
class CreditManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "credit_system_secure"
        private const val DAILY_BASE_CREDITS = 20
        private const val MAX_AD_CREDITS = 50
        private const val MAX_TOTAL_CREDITS = DAILY_BASE_CREDITS + MAX_AD_CREDITS
        
        // Different maximum credits for different types
        private const val MAX_IMAGE_CREDITS = 50
        private const val MAX_CHAT_CREDITS = MAX_TOTAL_CREDITS
        
        // Different daily base credits for different types
        private const val DAILY_BASE_CHAT_CREDITS = 20
        private const val DAILY_BASE_IMAGE_CREDITS = 20
        
        // Keys for SharedPreferences
        private const val CHAT_CREDITS_KEY = "chat_credits"
        private const val IMAGE_CREDITS_KEY = "image_credits"
        private const val LAST_RESET_DATE_KEY = "last_reset_date"
        private const val CHAT_HASH_KEY = "chat_hash"
        private const val IMAGE_HASH_KEY = "image_hash"
        private const val INSTALL_ID_KEY = "install_id"
        private const val LAST_CHAT_CONSUMPTION_TIME_KEY = "last_chat_consumption_time"
        private const val LAST_IMAGE_CONSUMPTION_TIME_KEY = "last_image_consumption_time"
        private const val CHAT_CONSUMPTION_COUNT_KEY = "chat_consumption_count"
        private const val IMAGE_CONSUMPTION_COUNT_KEY = "image_consumption_count"
        
        // Notification constants
        private const val CHANNEL_ID = "credits_channel"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile
        private var INSTANCE: CreditManager? = null
        
        fun getInstance(context: Context): CreditManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CreditManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val installId: String by lazy {
        encryptedPrefs.getString(INSTALL_ID_KEY, null) ?: generateInstallId()
    }

    enum class CreditType {
        CHAT, IMAGE_GENERATION
    }

    init {
        // Ensure daily reset check on initialization
        checkAndResetDaily()
    }

    private fun generateInstallId(): String {
        val uuid = UUID.randomUUID().toString()
        encryptedPrefs.edit().putString(INSTALL_ID_KEY, uuid).apply()
        return uuid
    }

    private fun generateHash(credits: Int, type: CreditType): String {
        val data = "$credits:$type:$installId:${getCurrentDate()}:${System.currentTimeMillis() / 10000}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentDate(): String {
        return Calendar.getInstance().let {
            "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}-${it.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    private fun checkAndResetDaily() {
        val currentDate = getCurrentDate()
        val lastResetDate = encryptedPrefs.getString(LAST_RESET_DATE_KEY, "")
        
        if (currentDate != lastResetDate) {
            Timber.d("Daily credit reset triggered")
            resetDailyCredits()
            encryptedPrefs.edit().putString(LAST_RESET_DATE_KEY, currentDate).apply()
        }
    }

    private fun resetDailyCredits() {
        // Reset both credit types to their respective daily base amounts
        setCreditsSafe(DAILY_BASE_CHAT_CREDITS, CreditType.CHAT)
        setCreditsSafe(DAILY_BASE_IMAGE_CREDITS, CreditType.IMAGE_GENERATION)
        
        // Reset consumption tracking for new day
        encryptedPrefs.edit()
            .putInt(CHAT_CONSUMPTION_COUNT_KEY, 0)
            .putInt(IMAGE_CONSUMPTION_COUNT_KEY, 0)
            .putLong(LAST_CHAT_CONSUMPTION_TIME_KEY, 0)
            .putLong(LAST_IMAGE_CONSUMPTION_TIME_KEY, 0)
            .putInt("ad_count_CHAT", 0)
            .putInt("ad_count_IMAGE_GENERATION", 0)
            .putLong("last_ad_time_CHAT", 0)
            .putLong("last_ad_time_IMAGE_GENERATION", 0)
            .apply()
        
        Timber.d("Credits reset - Chat: $DAILY_BASE_CHAT_CREDITS, Image: $DAILY_BASE_IMAGE_CREDITS")
        
        // Send notification about credit reset (only for non-subscribers)
        if (!PrefsManager.isSubscribed(context)) {
            sendCreditResetNotification()
        }
    }

    private fun setCreditsSafe(credits: Int, type: CreditType) {
        val hash = generateHash(credits, type)
        val editor = encryptedPrefs.edit()
        
        when (type) {
            CreditType.CHAT -> {
                editor.putInt(CHAT_CREDITS_KEY, credits)
                editor.putString(CHAT_HASH_KEY, hash)
            }
            CreditType.IMAGE_GENERATION -> {
                editor.putInt(IMAGE_CREDITS_KEY, credits)
                editor.putString(IMAGE_HASH_KEY, hash)
            }
        }
        
        editor.apply()
    }

    private fun getCreditsInternal(type: CreditType): Int {
        checkAndResetDaily()
        
        val credits = when (type) {
            CreditType.CHAT -> encryptedPrefs.getInt(CHAT_CREDITS_KEY, DAILY_BASE_CHAT_CREDITS)
            CreditType.IMAGE_GENERATION -> encryptedPrefs.getInt(IMAGE_CREDITS_KEY, DAILY_BASE_IMAGE_CREDITS)
        }
        
        val storedHash = when (type) {
            CreditType.CHAT -> encryptedPrefs.getString(CHAT_HASH_KEY, "")
            CreditType.IMAGE_GENERATION -> encryptedPrefs.getString(IMAGE_HASH_KEY, "")
        }
        
        val expectedHash = generateHash(credits, type)
        
        // Anti-cheat: verify hash
        if (storedHash != expectedHash) {
            Timber.w("Credit hash mismatch detected for $type, resetting to base amount")
            val baseCredits = when (type) {
                CreditType.CHAT -> DAILY_BASE_CHAT_CREDITS
                CreditType.IMAGE_GENERATION -> DAILY_BASE_IMAGE_CREDITS
            }
            setCreditsSafe(baseCredits, type)
            return baseCredits
        }
        
        return credits
    }

    /**
     * Get current chat credits
     */
    fun getChatCredits(): Int = getCreditsInternal(CreditType.CHAT)

    /**
     * Get current image generation credits
     */
    fun getImageCredits(): Int = getCreditsInternal(CreditType.IMAGE_GENERATION)

    /**
     * Consume chat credits
     * @param amount Amount to consume
     * @return true if successful, false if insufficient credits
     */
    fun consumeChatCredits(amount: Int = 1): Boolean {
        // Security validation
        if (amount <= 0 || amount > 10) {
            Timber.w("Invalid credit amount: $amount")
            return false
        }
        
        // Rate limiting check
        val currentTime = System.currentTimeMillis()
        val lastConsumptionTime = encryptedPrefs.getLong(LAST_CHAT_CONSUMPTION_TIME_KEY, 0)
        val timeDiff = currentTime - lastConsumptionTime
        
        if (timeDiff < 1000) { // Minimum 1 second between consumptions
            Timber.w("Rate limit exceeded for chat credits")
            return false
        }
        
        // Double-check credits before consuming (security measure)
        val currentCredits = getChatCredits()
        if (currentCredits >= amount) {
            setCreditsSafe(currentCredits - amount, CreditType.CHAT)
            
            // Update consumption tracking
            encryptedPrefs.edit()
                .putLong(LAST_CHAT_CONSUMPTION_TIME_KEY, currentTime)
                .putInt(CHAT_CONSUMPTION_COUNT_KEY, 
                    encryptedPrefs.getInt(CHAT_CONSUMPTION_COUNT_KEY, 0) + amount)
                .apply()
            
            Timber.d("Consumed $amount chat credits. Remaining: ${currentCredits - amount}")
            return true
        }
        Timber.w("Insufficient chat credits. Required: $amount, Available: $currentCredits")
        return false
    }

    /**
     * Consume image generation credits
     * @param amount Amount to consume
     * @return true if successful, false if insufficient credits
     */
    fun consumeImageCredits(amount: Int = 1): Boolean {
        // Security validation
        if (amount <= 0 || amount > 20) {
            Timber.w("Invalid image credit amount: $amount")
            return false
        }
        
        // Rate limiting check
        val currentTime = System.currentTimeMillis()
        val lastConsumptionTime = encryptedPrefs.getLong(LAST_IMAGE_CONSUMPTION_TIME_KEY, 0)
        val timeDiff = currentTime - lastConsumptionTime
        
        if (timeDiff < 2000) { // Minimum 2 seconds between image generations
            Timber.w("Rate limit exceeded for image credits")
            return false
        }
        
        // Double-check credits before consuming (security measure)
        val currentCredits = getImageCredits()
        if (currentCredits >= amount) {
            setCreditsSafe(currentCredits - amount, CreditType.IMAGE_GENERATION)
            
            // Update consumption tracking
            encryptedPrefs.edit()
                .putLong(LAST_IMAGE_CONSUMPTION_TIME_KEY, currentTime)
                .putInt(IMAGE_CONSUMPTION_COUNT_KEY, 
                    encryptedPrefs.getInt(IMAGE_CONSUMPTION_COUNT_KEY, 0) + amount)
                .apply()
            
            Timber.d("Consumed $amount image credits. Remaining: ${currentCredits - amount}")
            return true
        }
        Timber.w("Insufficient image credits. Required: $amount, Available: $currentCredits")
        return false
    }

    /**
     * Check if user has sufficient credits for chat
     * @param amount Amount needed
     * @return true if user has enough credits
     */
    fun hasSufficientChatCredits(amount: Int = 1): Boolean {
        return getChatCredits() >= amount
    }

    /**
     * Check if user has sufficient credits for image generation
     * @param amount Amount needed
     * @return true if user has enough credits
     */
    fun hasSufficientImageCredits(amount: Int = 1): Boolean {
        return getImageCredits() >= amount
    }

    /**
     * Add credits from watching ads
     * @param type Type of credits to add
     * @param amount Amount to add (default 5 credits per ad)
     * @return true if successful, false if already at maximum
     */
    fun addCreditsFromAd(type: CreditType, amount: Int = 5): Boolean {
        // Security validation
        if (amount <= 0 || amount > 10) {
            Timber.w("Invalid ad credit amount: $amount")
            return false
        }
        
        // Rate limiting for ad rewards
        val currentTime = System.currentTimeMillis()
        val lastAdTime = encryptedPrefs.getLong("last_ad_time_${type.name}", 0)
        val timeDiff = currentTime - lastAdTime
        
        if (timeDiff < 30000) { // Minimum 30 seconds between ad rewards
            Timber.w("Rate limit exceeded for ad credits")
            return false
        }
        
        val currentCredits = getCreditsInternal(type)
        val maxCredits = getMaxCredits(type)
        
        // Check if user can earn more credits
        if (currentCredits >= maxCredits) {
            Timber.d("Already at maximum credits for $type: $currentCredits")
            return false
        }
        
        val newCredits = minOf(currentCredits + amount, maxCredits)
        setCreditsSafe(newCredits, type)
        
        // Update ad tracking
        encryptedPrefs.edit()
            .putLong("last_ad_time_${type.name}", currentTime)
            .putInt("ad_count_${type.name}", 
                encryptedPrefs.getInt("ad_count_${type.name}", 0) + 1)
            .apply()
        
        Timber.d("Added $amount credits from ad for $type. New total: $newCredits")
        return true
    }

    /**
     * Check if user can earn more credits from ads
     */
    fun canEarnMoreCredits(type: CreditType): Boolean {
        return getCreditsInternal(type) < getMaxCredits(type)
    }

    /**
     * Get maximum possible credits for a specific type
     */
    fun getMaxCredits(type: CreditType): Int {
        return when (type) {
            CreditType.CHAT -> MAX_CHAT_CREDITS
            CreditType.IMAGE_GENERATION -> MAX_IMAGE_CREDITS
        }
    }
    
    /**
     * Get maximum possible credits (legacy function for backwards compatibility)
     */
    fun getMaxCredits(): Int = MAX_TOTAL_CREDITS

    /**
     * Get daily base credits amount
     */
    fun getDailyBaseCredits(): Int = DAILY_BASE_CREDITS

    /**
     * Get credits needed to reach maximum
     */
    fun getCreditsToMax(type: CreditType): Int {
        return maxOf(0, getMaxCredits(type) - getCreditsInternal(type))
    }

    /**
     * Force reset credits (for testing purposes)
     */
    fun forceReset() {
        resetDailyCredits()
    }

    /**
     * Send notification when credits are reset
     */
    private fun sendCreditResetNotification() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Daily Credits Reset!")
                .setContentText("Your daily credits have been reset. Chat: $DAILY_BASE_CHAT_CREDITS, Images: $DAILY_BASE_IMAGE_CREDITS")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Your daily credits have been reset! You now have $DAILY_BASE_CHAT_CREDITS chat credits and $DAILY_BASE_IMAGE_CREDITS image generation credits. Watch ads to earn more!"))

            with(NotificationManagerCompat.from(context)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {
                    notify(NOTIFICATION_ID, builder.build())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending credit reset notification")
        }
    }

    /**
     * Get debug info
     */
    fun getDebugInfo(): String {
        return """
            Chat Credits: ${getChatCredits()}
            Image Credits: ${getImageCredits()}
            Max Credits: $MAX_TOTAL_CREDITS
            Daily Base: $DAILY_BASE_CREDITS
            Last Reset: ${encryptedPrefs.getString(LAST_RESET_DATE_KEY, "Never")}
            Install ID: $installId
        """.trimIndent()
    }
}