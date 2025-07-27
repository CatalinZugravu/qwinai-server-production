package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RealisticShimmerText(
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
    val textMeasurer = rememberTextMeasurer()
    
    val infiniteTransition = rememberInfiniteTransition(label = "realistic_shimmer")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000, // Much slower for realistic effect
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
            }
    ) {
        if (componentSize != IntSize.Zero) {
            Canvas(
                modifier = Modifier
                    .size(
                        width = with(density) { componentSize.width.toDp() },
                        height = with(density) { componentSize.height.toDp() }
                    )
                    .clipToBounds()
            ) {
                val canvasSize = size
                val textResult = textMeasurer.measure(text, textStyle)
                val textWidth = textResult.size.width.toFloat()
                val textHeight = textResult.size.height.toFloat()
                val textX = (canvasSize.width - textWidth) / 2
                val textY = (canvasSize.height - textHeight) / 2
                
                // Draw base text
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = textStyle.copy(color = baseColor),
                    topLeft = Offset(textX, textY)
                )
                
                if (enabled) {
                    // Calculate shimmer position - diagonal from top-right to bottom-left
                    val totalDistance = textWidth + textHeight
                    val shimmerPosition = shimmerProgress * (totalDistance + 200f) - 100f
                    
                    // Create diagonal shimmer path
                    val angle = -45f // Top-right to bottom-left
                    val angleRad = Math.toRadians(angle.toDouble())
                    val cosAngle = cos(angleRad).toFloat()
                    val sinAngle = sin(angleRad).toFloat()
                    
                    // Shimmer stripe width
                    val shimmerWidth = 80f
                    
                    // Calculate shimmer center position
                    val shimmerCenterX = textX + shimmerPosition * cosAngle
                    val shimmerCenterY = textY + shimmerPosition * sinAngle
                    
                    // Create shimmer gradient - very smooth and subtle
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.8f),
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        start = Offset(
                            shimmerCenterX - shimmerWidth * cosAngle,
                            shimmerCenterY - shimmerWidth * sinAngle
                        ),
                        end = Offset(
                            shimmerCenterX + shimmerWidth * cosAngle,
                            shimmerCenterY + shimmerWidth * sinAngle
                        )
                    )
                    
                    // Clip to text bounds and draw shimmer
                    clipRect(
                        left = textX,
                        top = textY,
                        right = textX + textWidth,
                        bottom = textY + textHeight
                    ) {
                        // Draw shimmer only over the text area
                        drawRect(
                            brush = shimmerBrush,
                            topLeft = Offset(textX - shimmerWidth, textY - shimmerWidth),
                            size = Size(textWidth + shimmerWidth * 2, textHeight + shimmerWidth * 2),
                            blendMode = BlendMode.Plus // Additive blending for shine effect
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetallicReflectionText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "metallic_reflection")
    
    val reflectionOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 4000, // Slower for more realistic effect
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "reflection_offset"
    )
    
    Box(modifier = modifier) {
        // Base text layer
        Text(
            text = text,
            style = textStyle,
            color = baseColor
        )
        
        if (enabled) {
            // Shimmer reflection layer
            val reflectionBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.5f),
                    Color.White.copy(alpha = 0.8f),
                    Color.White.copy(alpha = 0.5f),
                    Color.White.copy(alpha = 0.2f),
                    Color.Transparent,
                    Color.Transparent
                ),
                start = Offset(
                    x = reflectionOffset * 200f - 100f,
                    y = reflectionOffset * 50f - 25f
                ),
                end = Offset(
                    x = reflectionOffset * 200f + 100f,
                    y = reflectionOffset * 50f + 25f
                )
            )
            
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .graphicsLayer {
                        // Apply blend mode for realistic shine
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = reflectionBrush,
                            blendMode = BlendMode.Plus
                        )
                    }
            )
        }
    }
}

@Composable
fun SmoothShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smooth_shimmer")
    
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -400f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3500, // Slower, more natural
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    
    Box(modifier = modifier) {
        // Base text
        Text(
            text = text,
            style = textStyle,
            color = baseColor
        )
        
        if (enabled) {
            // Smooth shimmer overlay with continuous coverage
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.7f),
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f)
                ),
                start = Offset(shimmerX - 80f, -30f),
                end = Offset(shimmerX + 80f, 30f)
            )
            
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                modifier = Modifier
                    .graphicsLayer(alpha = 0.8f)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = shimmerBrush,
                            blendMode = BlendMode.Plus
                        )
                    }
            )
        }
    }
}