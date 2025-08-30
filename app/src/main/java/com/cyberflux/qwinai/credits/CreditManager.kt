package com.cyberflux.qwinai.credits

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.cyberflux.qwinai.security.UserStateManager
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
        
        // Chat Credits: 15 free daily + 10 from ads (1 per ad) = 25 max
        private const val DAILY_BASE_CHAT_CREDITS = 15
        private const val MAX_AD_CHAT_CREDITS = 10
        private const val MAX_CHAT_CREDITS = DAILY_BASE_CHAT_CREDITS + MAX_AD_CHAT_CREDITS
        
        // Image Credits: 20 free daily + 10 from ads (1 per ad) = 30 max  
        private const val DAILY_BASE_IMAGE_CREDITS = 20
        private const val MAX_AD_IMAGE_CREDITS = 10
        private const val MAX_IMAGE_CREDITS = DAILY_BASE_IMAGE_CREDITS + MAX_AD_IMAGE_CREDITS
        
        // Legacy constants for backwards compatibility
        private const val DAILY_BASE_CREDITS = DAILY_BASE_CHAT_CREDITS
        private const val MAX_AD_CREDITS = MAX_AD_CHAT_CREDITS
        private const val MAX_TOTAL_CREDITS = MAX_CHAT_CREDITS
        
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
                INSTANCE ?: CreditManager(context.applicationContext).also { 
                    INSTANCE = it
                    it.createNotificationChannel()
                }
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
    
    // User state manager for server-side credit tracking
    private val userStateManager: UserStateManager by lazy {
        UserStateManager.getInstance(context)
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
        // Use stable data that doesn't change frequently - only date, not timestamp
        val data = "$credits:$type:$installId:${getCurrentDate()}"
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
            Timber.d("🔄 Daily credit reset triggered - Current: $currentDate, Last: $lastResetDate")
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
        
        // Send enhanced notification about credit reset (only for non-subscribers)
        if (!PrefsManager.isSubscribed(context)) {
            sendEnhancedCreditResetNotification()
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
        
        // Anti-cheat: verify hash (but be more forgiving to prevent false positives)
        if (!storedHash.isNullOrEmpty() && storedHash != expectedHash) {
            Timber.w("Credit hash mismatch detected for $type - stored: ${storedHash.take(8)}..., expected: ${expectedHash.take(8)}...")
            
            // Only reset if the credits value seems impossible (negative or way too high)
            val maxPossibleCredits = getMaxCredits(type) + 5 // Allow small buffer
            if (credits < 0 || credits > maxPossibleCredits) {
                Timber.w("Credit value $credits is impossible for $type, resetting to base amount")
                val baseCredits = when (type) {
                    CreditType.CHAT -> DAILY_BASE_CHAT_CREDITS
                    CreditType.IMAGE_GENERATION -> DAILY_BASE_IMAGE_CREDITS
                }
                setCreditsSafe(baseCredits, type)
                return baseCredits
            } else {
                // Hash mismatch but credits seem reasonable - just update the hash
                Timber.d("Hash mismatch but credits ($credits) seem valid, updating hash")
                setCreditsSafe(credits, type)
            }
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
        // Enhanced security validation
        if (amount <= 0 || amount > 5) {
            Timber.e("🚨 SECURITY: Invalid credit amount: $amount - BLOCKED")
            return false
        }
        
        // Anti-cheat: Validate calling context
        val stackTrace = Thread.currentThread().stackTrace
        val isAuthorizedCall = stackTrace.any { 
            it.className.contains("MainActivity") || 
            it.className.contains("ImageGenerationActivity") ||
            it.className.contains("CreditManager")
        }
        if (!isAuthorizedCall) {
            Timber.e("🚨 SECURITY: consumeChatCredits called from unauthorized location - BLOCKED")
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
            
            val newCredits = currentCredits - amount
            Timber.d("✅ Consumed $amount chat credits: $currentCredits → $newCredits")
            
            // CRITICAL: Record consumption server-side to prevent reinstall abuse
            userStateManager.recordCreditConsumption(CreditType.CHAT, amount)
            
            return true
        }
        Timber.w("❌ Insufficient chat credits. Required: $amount, Available: $currentCredits")
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
            
            // CRITICAL: Record consumption server-side to prevent reinstall abuse
            userStateManager.recordCreditConsumption(CreditType.IMAGE_GENERATION, amount)
            
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
     * @param amount Amount to add (default 1 credit per ad)
     * @return true if successful, false if already at maximum
     */
    @Synchronized
    fun addCreditsFromAd(type: CreditType, amount: Int = 1): Boolean {
        // Security validation - each ad should give exactly 1 credit
        if (amount != 1) {
            Timber.w("🚨 SECURITY: Invalid ad credit amount: $amount - BLOCKED")
            return false
        }
        
        // Anti-cheat: Validate calling context (should only be called by AdManager)
        val stackTrace = Thread.currentThread().stackTrace
        val isCalledByAdManager = stackTrace.any { 
            it.className.contains("AdManager") || it.className.contains("RewardedAd")
        }
        if (!isCalledByAdManager) {
            Timber.e("🚨 SECURITY: addCreditsFromAd called from unauthorized location - BLOCKED")
            return false
        }
        
        // Enhanced rate limiting for ad rewards
        val currentTime = System.currentTimeMillis()
        val lastAdTime = encryptedPrefs.getLong("last_ad_time_${type.name}", 0)
        val timeDiff = currentTime - lastAdTime
        
        // Minimum 30 seconds between ads + daily limit check
        if (timeDiff < 30000) {
            Timber.w("🚨 SECURITY: Rate limit exceeded for ad credits (${timeDiff}ms) - BLOCKED")
            return false
        }
        
        // Daily ad limit check (max 10 ads per day per type)
        val adCountToday = getAdCountToday(type)
        val maxAdsPerDay = when (type) {
            CreditType.CHAT -> MAX_AD_CHAT_CREDITS
            CreditType.IMAGE_GENERATION -> MAX_AD_IMAGE_CREDITS
        }
        
        if (adCountToday >= maxAdsPerDay) {
            Timber.w("🚨 SECURITY: Daily ad limit reached ($adCountToday/$maxAdsPerDay) - BLOCKED")
            return false
        }
        
        val currentCredits = getCreditsInternal(type)
        val maxCredits = getMaxCredits(type)
        
        // Double-check if user can earn more credits
        if (currentCredits >= maxCredits) {
            Timber.d("Already at maximum credits for $type: $currentCredits")
            return false
        }
        
        val newCredits = minOf(currentCredits + amount, maxCredits)
        setCreditsSafe(newCredits, type)
        
        // Update ad tracking with timestamp validation
        encryptedPrefs.edit()
            .putLong("last_ad_time_${type.name}", currentTime)
            .putInt("ad_count_${type.name}", adCountToday + 1)
            .putString("last_ad_date_${type.name}", getCurrentDate())
            .apply()
        
        Timber.d("✅ Added $amount credits from ad for $type: $currentCredits → $newCredits")
        return true
    }

    /**
     * Get today's ad count for security validation
     */
    private fun getAdCountToday(type: CreditType): Int {
        val today = getCurrentDate()
        val lastAdDate = encryptedPrefs.getString("last_ad_date_${type.name}", "")
        
        return if (lastAdDate == today) {
            encryptedPrefs.getInt("ad_count_${type.name}", 0)
        } else {
            // Different day, reset count
            0
        }
    }

    /**
     * Check if user can earn more credits from ads
     */
    fun canEarnMoreCredits(type: CreditType): Boolean {
        val currentCredits = getCreditsInternal(type)
        val maxCredits = getMaxCredits(type)
        val adCountToday = getAdCountToday(type)
        val maxAdsPerDay = when (type) {
            CreditType.CHAT -> MAX_AD_CHAT_CREDITS
            CreditType.IMAGE_GENERATION -> MAX_AD_IMAGE_CREDITS
        }
        
        return currentCredits < maxCredits && adCountToday < maxAdsPerDay
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
     * Refund credits (when errors occur)
     * @param amount Amount to refund
     * @param type Type of credits to refund
     * @param reason Reason for refund (for logging)
     * @return true if successful, false if already at maximum
     */
    fun refundCredits(amount: Int, type: CreditType, reason: String = "Error occurred"): Boolean {
        // Security validation
        if (amount <= 0 || amount > 10) {
            Timber.w("Invalid refund amount: $amount")
            return false
        }
        
        // Anti-cheat: Validate calling context
        val stackTrace = Thread.currentThread().stackTrace
        val isAuthorizedCall = stackTrace.any { 
            it.className.contains("MainActivity") || 
            it.className.contains("ImageGenerationActivity") ||
            it.className.contains("ImageGenerationManager") ||
            it.className.contains("StreamingHandler") ||
            it.className.contains("CreditManager") ||
            it.className.contains("ErrorManager")
        }
        if (!isAuthorizedCall) {
            Timber.e("🚨 SECURITY: refundCredits called from unauthorized location - BLOCKED")
            return false
        }
        
        val currentCredits = getCreditsInternal(type)
        val maxCredits = getMaxCredits(type)
        
        if (currentCredits >= maxCredits) {
            Timber.d("Already at maximum credits for $type, cannot refund")
            return false
        }
        
        val newCredits = minOf(currentCredits + amount, maxCredits)
        setCreditsSafe(newCredits, type)
        
        // Record the refund with server-side tracking
        // TODO: Implement recordCreditRefund in UserStateManager
        // userStateManager.recordCreditRefund(type, amount, reason)
        
        Timber.d("💰 REFUNDED $amount $type credits: $currentCredits → $newCredits (Reason: $reason)")
        return true
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
     * Create notification channel for credit notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Credit System"
            val descriptionText = "Notifications about daily credit resets and credit-related updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Timber.d("✅ Credit notification channel created: $CHANNEL_ID")
        }
    }
    
    /**
     * Enhanced notification with better logging
     */
    private fun sendEnhancedCreditResetNotification() {
        try {
            // Ensure notification channel exists
            createNotificationChannel()
            
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
                .setContentTitle("Daily Credits Reset! 🎉")
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
                    Timber.d("✅ Enhanced credit reset notification sent successfully")
                } else {
                    Timber.w("⚠️ Notification permission not granted for credit reset notification")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error sending enhanced credit reset notification")
        }
    }

    /**
     * Get debug info
     */
    fun getDebugInfo(): String {
        return """
            🔒 CREDIT SYSTEM DEBUG INFO
            Chat Credits: ${getChatCredits()}/${getMaxCredits(CreditType.CHAT)}
            Image Credits: ${getImageCredits()}/${getMaxCredits(CreditType.IMAGE_GENERATION)}
            Chat Ads Today: ${getAdCountToday(CreditType.CHAT)}/$MAX_AD_CHAT_CREDITS
            Image Ads Today: ${getAdCountToday(CreditType.IMAGE_GENERATION)}/$MAX_AD_IMAGE_CREDITS
            Last Reset: ${encryptedPrefs.getString(LAST_RESET_DATE_KEY, "Never")}
            Install ID: ${installId.take(8)}...
            Is Subscribed: ${PrefsManager.isSubscribed(context)}
        """.trimIndent()
    }
}