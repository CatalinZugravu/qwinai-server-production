package com.cyberflux.qwinai

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import timber.log.Timber

class VideoPlayerActivity : AppCompatActivity() {
    
    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var fullscreenButton: ImageButton
    
    private var isFullscreen = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        
        initializeViews()
        setupPlayer()
        setupControls()
        
        val videoUrl = intent.getStringExtra("video_url") ?: return finish()
        val videoTitle = intent.getStringExtra("video_title") ?: "Video"
        
        titleText.text = videoTitle
        loadVideo(videoUrl)
        
        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen()
                } else {
                    finish()
                }
            }
        })
    }
    
    private fun initializeViews() {
        playerView = findViewById(R.id.player_view)
        titleText = findViewById(R.id.video_title)
        closeButton = findViewById(R.id.close_button)
        fullscreenButton = findViewById(R.id.fullscreen_button)
    }
    
    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            // Show loading indicator
                        }
                        Player.STATE_READY -> {
                            // Hide loading indicator
                        }
                        Player.STATE_ENDED -> {
                            // Video ended
                        }
                    }
                }
            })
        }
    }
    
    private fun setupControls() {
        closeButton.setOnClickListener {
            finish()
        }
        
        fullscreenButton.setOnClickListener {
            if (isFullscreen) {
                exitFullscreen()
            } else {
                enterFullscreen()
            }
        }
    }
    
    private fun loadVideo(videoUrl: String) {
        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer?.let { player ->
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading video: $videoUrl")
            finish()
        }
    }
    
    private fun enterFullscreen() {
        isFullscreen = true
        
        // Hide system bars
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Hide title bar
        findViewById<View>(R.id.title_bar)?.visibility = View.GONE
        
        // Update fullscreen button icon
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
    }
    
    private fun exitFullscreen() {
        isFullscreen = false
        
        // Show system bars
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        
        // Allow screen to turn off
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Show title bar
        findViewById<View>(R.id.title_bar)?.visibility = View.VISIBLE
        
        // Update fullscreen button icon
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
    }
    
    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}