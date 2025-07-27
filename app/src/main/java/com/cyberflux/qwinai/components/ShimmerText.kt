package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    shimmerColor: Color = Color.White.copy(alpha = 0.7f),
    backgroundColor: Color = Color.Transparent,
    animationDuration: Int = 2000,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )
    
    val shimmerGradient = Brush.horizontalGradient(
        colors = listOf(
            backgroundColor,
            shimmerColor,
            backgroundColor
        ),
        startX = shimmerPosition * 1000f - 500f,
        endX = shimmerPosition * 1000f + 500f
    )
    
    if (enabled) {
        Box(
            modifier = modifier
                .background(
                    brush = shimmerGradient,
                    shape = RectangleShape
                )
        ) {
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent
            )
        }
    } else {
        Text(
            text = text,
            style = textStyle,
            modifier = modifier
        )
    }
}

@Composable
fun ShimmerStatusIndicator(
    status: String,
    modifier: Modifier = Modifier,
    shimmerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_shimmer")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            shimmerColor,
            Color.Transparent
        ),
        startX = shimmerOffset * 800f,
        endX = shimmerOffset * 800f + 400f
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = textColor
            )
            
            if (isActive) {
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedDots()
            }
        }
        
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = shimmerBrush,
                        alpha = 0.3f
                    )
            )
        }
    }
}

@Composable
private fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha = when (index) {
                0 -> dotAlpha1
                1 -> dotAlpha2
                else -> dotAlpha3
            }
            
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        Color.White.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (isVisible) {
        ShimmerStatusIndicator(
            status = "Thinking",
            modifier = modifier,
            shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            textColor = MaterialTheme.colorScheme.onPrimary,
            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun AnalyzingIndicator(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (isVisible) {
        ShimmerStatusIndicator(
            status = "Analyzing",
            modifier = modifier,
            shimmerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            textColor = MaterialTheme.colorScheme.onSecondary,
            backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun SearchingWebIndicator(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (isVisible) {
        ShimmerStatusIndicator(
            status = "Searching the web",
            modifier = modifier,
            shimmerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
            textColor = MaterialTheme.colorScheme.onTertiary,
            backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun ProcessingIndicator(
    text: String = "Processing",
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (isVisible) {
        ShimmerStatusIndicator(
            status = text,
            modifier = modifier,
            shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }
}