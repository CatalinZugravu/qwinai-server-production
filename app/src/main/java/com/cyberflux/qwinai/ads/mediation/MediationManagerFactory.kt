package com.cyberflux.qwinai.ads.mediation

import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Factory that creates the appropriate mediation manager based on device type.
 * Uses centralized device detection from MyApp.
 */
object MediationManagerFactory {
    private var googleMediationManager: GoogleDeviceMediationManager? = null
    private var huaweiMediationManager: HuaweiDeviceMediationManager? = null

    // Thread safety for initialization
    private val lock = ReentrantLock()

    /**
     * Gets the appropriate mediation manager for the current device.
     */
    fun getMediationManager(): AdMediationManager {
        return lock.withLock {
            if (com.cyberflux.qwinai.MyApp.isHuaweiDevice()) {
                getHuaweiMediationManager()
            } else {
                getGoogleMediationManager()
            }
        }
    }

    /**
     * Get a Google mediation manager instance (lazy initialization)
     */
    private fun getGoogleMediationManager(): GoogleDeviceMediationManager {
        return googleMediationManager ?: GoogleDeviceMediationManager().also {
            googleMediationManager = it
            Timber.d("Created Google device mediation manager")
        }
    }

    /**
     * Get a Huawei mediation manager instance (lazy initialization)
     */
    private fun getHuaweiMediationManager(): HuaweiDeviceMediationManager {
        return huaweiMediationManager ?: HuaweiDeviceMediationManager().also {
            huaweiMediationManager = it
            Timber.d("Created Huawei device mediation manager")
        }
    }

    /**
     * Release resources from all mediation managers
     * Call this in your Application's onTerminate or when you're completely done with ads
     */
    fun release() {
        googleMediationManager?.release()
        googleMediationManager = null

        huaweiMediationManager?.release()
        huaweiMediationManager = null

        Timber.d("Released all mediation managers")
    }
}