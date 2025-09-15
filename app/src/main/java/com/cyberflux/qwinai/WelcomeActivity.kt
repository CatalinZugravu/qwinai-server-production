package com.cyberflux.qwinai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cyberflux.qwinai.utils.BaseThemedActivity

/**
 * Welcome/Onboarding Activity for new users
 */
class WelcomeActivity : BaseThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For now, directly start MainActivity
        // In the future, implement proper welcome/onboarding flow
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        /**
         * Start WelcomeActivity from any context
         */
        fun start(context: Context) {
            val intent = Intent(context, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        }
    }
}