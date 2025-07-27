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
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AdvancedShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    shimmerColors: List<Color> = listOf(
        Color.White.copy(alpha = 0.0f),
        Color.White.copy(alpha = 0.8f),
        Color.White.copy(alpha = 0.0f)
    ),
    backgroundColor: Color = Color.Transparent,
    animationDuration: Int = 2000,
    shimmerWidth: Float = 0.3f,
    angle: Float = 0f,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "advanced_shimmer")
    
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -shimmerWidth,
        targetValue = 1f + shimmerWidth,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    val shimmerBrush = remember(shimmerTranslate, shimmerColors, angle) {
        val angleRad = Math.toRadians(angle.toDouble())
        val cosAngle = cos(angleRad).toFloat()
        val sinAngle = sin(angleRad).toFloat()
        
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(
                x = shimmerTranslate * 1000f - 500f,
                y = 0f
            ),
            end = Offset(
                x = shimmerTranslate * 1000f + 500f,
                y = 0f
            )
        )
    }
    
    if (enabled) {
        Box(
            modifier = modifier
        ) {
            Text(
                text = text,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .background(
                        brush = shimmerBrush,
                        alpha = 1f
                    )
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
fun PulsingShimmerIndicator(
    status: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    isActive: Boolean = true,
    cornerRadius: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_shimmer")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = -0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val shimmerBrush = remember(shimmerOffset, primaryColor, accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                primaryColor.copy(alpha = 0.4f),
                accentColor.copy(alpha = 0.6f),
                primaryColor.copy(alpha = 0.4f),
                Color.Transparent
            ),
            startX = shimmerOffset * 600f,
            endX = shimmerOffset * 600f + 300f
        )
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.1f),
                        accentColor.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (isActive) {
                Spacer(modifier = Modifier.width(10.dp))
                PulsingDots()
            }
        }
        
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = shimmerBrush,
                        alpha = 0.5f
                    )
            )
        }
    }
}

@Composable
private fun PulsingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dots")
    
    val dots = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    delayMillis = index * 200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$index"
        )
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEach { dotAlpha ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha.value),
                                MaterialTheme.colorScheme.secondary.copy(alpha = dotAlpha.value * 0.7f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun WaveShimmerIndicator(
    status: String,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_shimmer")
    
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_amplitude"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor.copy(alpha = 0.9f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (isActive) {
                Spacer(modifier = Modifier.width(8.dp))
                WaveAnimation(
                    wavePhase = wavePhase,
                    amplitude = waveAmplitude,
                    color = waveColor
                )
            }
        }
    }
}

@Composable
private fun WaveAnimation(
    wavePhase: Float,
    amplitude: Float,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val height = remember(wavePhase, amplitude, index) {
                val phase = wavePhase + (index * 0.5f)
                val waveHeight = sin(phase) * amplitude + 0.5f
                (waveHeight * 8 + 4).dp
            }
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .background(
                        color = color.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun StatusIndicatorCollection(
    currentStatus: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    when (currentStatus.lowercase()) {
        "thinking" -> {
            PulsingShimmerIndicator(
                status = "Thinking",
                modifier = modifier,
                primaryColor = MaterialTheme.colorScheme.primary,
                accentColor = MaterialTheme.colorScheme.secondary,
                isActive = isActive
            )
        }
        "analyzing" -> {
            WaveShimmerIndicator(
                status = "Analyzing",
                modifier = modifier,
                waveColor = MaterialTheme.colorScheme.tertiary,
                isActive = isActive
            )
        }
        "searching the web", "searching web" -> {
            AdvancedShimmerText(
                text = "Searching the web",
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shimmerColors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    Color.Transparent
                ),
                animationDuration = 1800,
                enabled = isActive
            )
        }
        else -> {
            PulsingShimmerIndicator(
                status = currentStatus,
                modifier = modifier,
                isActive = isActive
            )
        }
    }
}