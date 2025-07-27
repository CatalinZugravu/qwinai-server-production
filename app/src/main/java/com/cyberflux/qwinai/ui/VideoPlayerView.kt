package com.cyberflux.qwinai.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cyberflux.qwinai.R

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView

    init {
        LayoutInflater.from(context).inflate(R.layout.video_player_view, this, true)
        playerView = findViewById(R.id.player_view)
        setupPlayer()
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            playerView.player = player
            
            // Configure player settings
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.playWhenReady = false
            
            // Set up player controls
            playerView.useController = true
            playerView.controllerAutoShow = true
            playerView.controllerHideOnTouch = true
        }
    }

    fun loadVideo(videoUrl: String) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun setVideoSize(width: Int, height: Int) {
        val layoutParams = this.layoutParams as? ViewGroup.LayoutParams
            ?: ViewGroup.LayoutParams(width, height)
        layoutParams.width = width
        layoutParams.height = height
        this.layoutParams = layoutParams
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}