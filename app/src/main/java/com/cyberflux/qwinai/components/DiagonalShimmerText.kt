package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
fun DiagonalShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    shimmerColor: Color = Color.White,
    animationDuration: Int = 2000,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    
    val infiniteTransition = rememberInfiniteTransition(label = "diagonal_shimmer")
    
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )
    
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
            }
    ) {
        if (componentSize != IntSize.Zero) {
            Canvas(
                modifier = Modifier.size(
                    width = with(density) { componentSize.width.toDp() },
                    height = with(density) { componentSize.height.toDp() }
                )
            ) {
                val canvasSize = size
                
                // Draw base text
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = textStyle.copy(color = baseColor),
                    topLeft = Offset(
                        x = (canvasSize.width - textMeasurer.measure(text, textStyle).size.width) / 2,
                        y = (canvasSize.height - textMeasurer.measure(text, textStyle).size.height) / 2
                    )
                )
                
                if (enabled) {
                    // Create diagonal shimmer effect
                    val textResult = textMeasurer.measure(text, textStyle)
                    val textWidth = textResult.size.width.toFloat()
                    val textHeight = textResult.size.height.toFloat()
                    val textX = (canvasSize.width - textWidth) / 2
                    val textY = (canvasSize.height - textHeight) / 2
                    
                    // Calculate diagonal shimmer parameters
                    val diagonalLength = kotlin.math.sqrt(textWidth * textWidth + textHeight * textHeight)
                    val shimmerWidth = diagonalLength * 0.3f
                    val shimmerAngle = -45f // Top-right to bottom-left
                    
                    // Calculate shimmer position along diagonal
                    val shimmerProgress = shimmerPosition
                    val shimmerCenter = shimmerProgress * (diagonalLength + shimmerWidth) - shimmerWidth / 2
                    
                    // Create shimmer gradient
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor.copy(alpha = 0.3f),
                            shimmerColor.copy(alpha = 0.8f),
                            shimmerColor.copy(alpha = 1f),
                            shimmerColor.copy(alpha = 0.8f),
                            shimmerColor.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        start = Offset(
                            x = textX + shimmerCenter * cos(Math.toRadians(shimmerAngle.toDouble())).toFloat() - shimmerWidth / 2,
                            y = textY + shimmerCenter * sin(Math.toRadians(shimmerAngle.toDouble())).toFloat() - shimmerWidth / 2
                        ),
                        end = Offset(
                            x = textX + shimmerCenter * cos(Math.toRadians(shimmerAngle.toDouble())).toFloat() + shimmerWidth / 2,
                            y = textY + shimmerCenter * sin(Math.toRadians(shimmerAngle.toDouble())).toFloat() + shimmerWidth / 2
                        )
                    )
                    
                    // Clip to text bounds and draw shimmer
                    clipRect(
                        left = textX,
                        top = textY,
                        right = textX + textWidth,
                        bottom = textY + textHeight
                    ) {
                        // Draw shimmer overlay on text
                        drawRect(
                            brush = shimmerBrush,
                            topLeft = Offset(textX - shimmerWidth, textY - shimmerWidth),
                            size = Size(textWidth + shimmerWidth * 2, textHeight + shimmerWidth * 2),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetallicShimmerText(
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
    
    val infiniteTransition = rememberInfiniteTransition(label = "metallic_shimmer")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2500,
                easing = FastOutSlowInEasing
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
        if (componentSize != IntSize.Zero) {
            Canvas(
                modifier = Modifier.size(
                    width = with(density) { componentSize.width.toDp() },
                    height = with(density) { componentSize.height.toDp() }
                )
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
                    // Create metallic shimmer effect
                    val shimmerWidth = textWidth * 0.4f
                    val shimmerHeight = textHeight * 1.2f
                    
                    // Calculate diagonal movement (top-right to bottom-left)
                    val diagonalDistance = textWidth + textHeight
                    val shimmerX = textX + shimmerOffset * (textWidth + shimmerWidth) - shimmerWidth / 2
                    val shimmerY = textY + shimmerOffset * (textHeight + shimmerHeight) - shimmerHeight / 2
                    
                    // Create glossy shimmer gradient
                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerX, shimmerY),
                        end = Offset(shimmerX + shimmerWidth, shimmerY + shimmerHeight)
                    )
                    
                    // Create the shimmer path (diagonal)
                    val shimmerPath = Path().apply {
                        moveTo(shimmerX, shimmerY)
                        lineTo(shimmerX + shimmerWidth * 0.8f, shimmerY)
                        lineTo(shimmerX + shimmerWidth, shimmerY + shimmerHeight)
                        lineTo(shimmerX + shimmerWidth * 0.2f, shimmerY + shimmerHeight)
                        close()
                    }
                    
                    // Clip to text bounds and draw shimmer
                    clipRect(
                        left = textX,
                        top = textY,
                        right = textX + textWidth,
                        bottom = textY + textHeight
                    ) {
                        drawPath(
                            path = shimmerPath,
                            brush = shimmerBrush,
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerStatusIndicator(
    text: String,
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .wrapContentSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLoadingIndicator && isActive) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        }
        
        if (isActive) {
            MetallicShimmerText(
                text = text,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                baseColor = contentColor,
                enabled = true
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = contentColor
            )
        }
    }
}

// Pre-built shimmer indicators for common status messages
@Composable
fun ShimmerThinkingIndicator(
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = "Thinking...",
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun ShimmerAnalyzingIndicator(
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = "Analyzing...",
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun ShimmerSearchingWebIndicator(
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = "Searching web...",
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
fun ShimmerGeneratingIndicator(
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = "Generating...",
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ShimmerGeneratingResponseIndicator(
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = "Generating response...",
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ShimmerProcessingIndicator(
    text: String = "Processing...",
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    ShimmerStatusIndicator(
        text = text,
        modifier = modifier,
        showLoadingIndicator = showLoadingIndicator,
        isActive = isActive,
        contentColor = MaterialTheme.colorScheme.outline
    )
}

@Composable
fun UniversalShimmerStatusIndicator(
    status: String,
    modifier: Modifier = Modifier,
    showLoadingIndicator: Boolean = true,
    isActive: Boolean = true
) {
    when (status.lowercase().trim()) {
        "thinking", "thinking..." -> ShimmerThinkingIndicator(modifier, showLoadingIndicator, isActive)
        "analyzing", "analyzing...", "analyzing photo" -> ShimmerAnalyzingIndicator(modifier, showLoadingIndicator, isActive)
        "searching web", "searching web...", "searching the web" -> ShimmerSearchingWebIndicator(modifier, showLoadingIndicator, isActive)
        "generating", "generating..." -> ShimmerGeneratingIndicator(modifier, showLoadingIndicator, isActive)
        "generating response", "generating response..." -> ShimmerGeneratingResponseIndicator(modifier, showLoadingIndicator, isActive)
        "processing", "processing..." -> ShimmerProcessingIndicator("Processing...", modifier, showLoadingIndicator, isActive)
        "processing files", "processing files..." -> ShimmerProcessingIndicator("Processing files...", modifier, showLoadingIndicator, isActive)
        "processing pdf", "processing pdf..." -> ShimmerProcessingIndicator("Processing PDF...", modifier, showLoadingIndicator, isActive)
        "processing image", "processing image..." -> ShimmerProcessingIndicator("Processing image...", modifier, showLoadingIndicator, isActive)
        "processing captured image", "processing captured image..." -> ShimmerProcessingIndicator("Processing captured image...", modifier, showLoadingIndicator, isActive)
        "processing ocr results", "processing ocr results..." -> ShimmerProcessingIndicator("Processing OCR results...", modifier, showLoadingIndicator, isActive)
        "processing text extraction", "processing text extraction..." -> ShimmerProcessingIndicator("Processing text extraction...", modifier, showLoadingIndicator, isActive)
        "analyzing search results", "analyzing search results..." -> ShimmerAnalyzingIndicator(modifier, showLoadingIndicator, isActive)
        "ai is thinking" -> ShimmerThinkingIndicator(modifier, showLoadingIndicator, isActive)
        "ai speaking", "ai speaking..." -> ShimmerProcessingIndicator("AI Speaking...", modifier, showLoadingIndicator, isActive)
        else -> ShimmerProcessingIndicator(status, modifier, showLoadingIndicator, isActive)
    }
}