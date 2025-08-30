package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun SeamlessShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "seamless_shimmer")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    
    Box(modifier = modifier) {
        // Base text
        Text(
            text = text,
            style = textStyle,
            color = baseColor
        )
        
        if (enabled) {
            // Seamless shimmer overlay
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .graphicsLayer(alpha = 0.8f)
                    .drawWithContent {
                        drawContent()
                        
                        // Calculate shimmer position with extended range for seamless effect
                        val totalWidth = size.width + 200f // Extra width for seamless transition
                        val shimmerX = (shimmerProgress * totalWidth) - 100f
                        
                        val shimmerBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.1f)
                            ),
                            start = Offset(shimmerX - 80f, -30f),
                            end = Offset(shimmerX + 80f, 30f)
                        )
                        
                        drawRect(
                            brush = shimmerBrush,
                            blendMode = BlendMode.Plus
                        )
                    }
            )
        }
    }
}