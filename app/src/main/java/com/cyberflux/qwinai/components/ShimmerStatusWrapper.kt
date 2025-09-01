package com.cyberflux.qwinai.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset

class ShimmerStatusWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var composeView: ComposeView? = null
    private var currentStatus = ""
    private var isActive = false
    private var statusColor = 0

    init {
        setupComposeView()
    }

    private fun setupComposeView() {
        composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        addView(composeView)
        updateContent()
    }

    private fun updateContent() {
        composeView?.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(2.dp)
                ) {
                    if (isActive && currentStatus.isNotEmpty()) {
                        SeamlessShimmerText(
                            text = currentStatus,
                            enabled = isActive,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            baseColor = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (currentStatus.isNotEmpty()) {
                        // Show static text when not active
                        Text(
                            text = currentStatus,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    fun setStatus(status: String, color: Int, active: Boolean) {
        currentStatus = status
        statusColor = color
        isActive = active
        
        android.util.Log.d("ShimmerStatusWrapper", "setStatus: '$status', active: $active")
        
        // Instant visibility changes for seamless transitions
        visibility = if (active && status.isNotEmpty()) VISIBLE else GONE
        alpha = 1f // Ensure full visibility
        updateContent()
    }

    fun hideStatus() {
        isActive = false
        // Instant hide to prevent gaps between indicators
        visibility = GONE
        alpha = 1f // Reset for next use
        updateContent()
    }

}