package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SimpleShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "simple_shimmer")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
            }
    ) {
        // Base text
        Text(
            text = text,
            style = textStyle,
            color = baseColor.copy(alpha = 0.7f)
        )
        
        if (enabled && componentSize != IntSize.Zero) {
            // Shimmer overlay
            val shimmerColors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.3f),
                Color.White.copy(alpha = 0.7f),
                Color.White.copy(alpha = 0.9f),
                Color.White.copy(alpha = 0.7f),
                Color.White.copy(alpha = 0.3f),
                Color.Transparent
            )
            
            val widthPx = with(density) { componentSize.width.toDp().toPx() }
            val heightPx = with(density) { componentSize.height.toDp().toPx() }
            
            // Calculate diagonal shimmer position
            val diagonalLength = kotlin.math.sqrt(widthPx * widthPx + heightPx * heightPx)
            val shimmerWidth = diagonalLength * 0.3f
            val angle = -45f // Top-right to bottom-left
            
            val centerX = widthPx / 2 + shimmerOffset * widthPx
            val centerY = heightPx / 2 + shimmerOffset * heightPx
            
            val shimmerBrush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(
                    x = centerX - shimmerWidth * cos(Math.toRadians(angle.toDouble())).toFloat() / 2,
                    y = centerY - shimmerWidth * sin(Math.toRadians(angle.toDouble())).toFloat() / 2
                ),
                end = Offset(
                    x = centerX + shimmerWidth * cos(Math.toRadians(angle.toDouble())).toFloat() / 2,
                    y = centerY + shimmerWidth * sin(Math.toRadians(angle.toDouble())).toFloat() / 2
                )
            )
            
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .background(
                        brush = shimmerBrush,
                        alpha = 0.8f
                    )
            )
        }
    }
}

@Composable
fun WorkingShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "working_shimmer")
    
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )
    
    Box(modifier = modifier) {
        // Base text - always visible
        Text(
            text = text,
            style = textStyle,
            color = baseColor
        )
        
        if (enabled) {
            // Diagonal shimmer gradient overlay
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.3f),
                    Color.White.copy(alpha = 0.7f),
                    Color.White.copy(alpha = 1f),
                    Color.White.copy(alpha = 0.7f),
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                start = Offset(
                    x = shimmerPosition * 800f - 200f,
                    y = shimmerPosition * 200f - 50f
                ),
                end = Offset(
                    x = shimmerPosition * 800f + 200f,
                    y = shimmerPosition * 200f + 50f
                )
            )
            
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .background(
                        brush = shimmerBrush,
                        alpha = 0.8f
                    )
            )
        }
    }
}