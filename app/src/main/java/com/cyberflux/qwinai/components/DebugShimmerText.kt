package com.cyberflux.qwinai.components

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class DebugShimmerText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var composeView: ComposeView? = null
    private var currentStatus = ""
    private var isActive = false

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
                    modifier = Modifier.wrapContentSize()
                ) {
                    if (currentStatus.isNotEmpty()) {
                        Text(
                            text = currentStatus,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

}