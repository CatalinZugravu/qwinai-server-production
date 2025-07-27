package com.cyberflux.qwinai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatGPTShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    shimmerColor: Color = Color.White.copy(alpha = 0.6f),
    baseColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    backgroundColor: Color = Color.Transparent,
    animationDuration: Int = 1500,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "chatgpt_shimmer")
    
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )
    
    val shimmerBrush = remember(shimmerPosition, shimmerColor, componentSize) {
        val widthPx = componentSize.width.toFloat()
        val shimmerWidth = widthPx * 0.3f
        val shimmerStart = widthPx * shimmerPosition - shimmerWidth
        val shimmerEnd = widthPx * shimmerPosition + shimmerWidth
        
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor.copy(alpha = 0.3f),
                shimmerColor.copy(alpha = 0.8f),
                shimmerColor.copy(alpha = 0.3f),
                Color.Transparent
            ),
            start = Offset(shimmerStart, 0f),
            end = Offset(shimmerEnd, 0f)
        )
    }
    
    Box(
        modifier = modifier
            .background(backgroundColor)
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
            }
    ) {
        // Base text
        Text(
            text = text,
            style = textStyle,
            color = baseColor
        )
        
        // Shimmer overlay
        if (enabled && componentSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = shimmerBrush,
                        alpha = 1f
                    )
            )
        }
    }
}

@Composable
fun ChatGPTShimmerIndicator(
    text: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shimmerColor: Color = Color.White.copy(alpha = 0.7f),
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
) {
    val density = LocalDensity.current
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "chatgpt_indicator_shimmer")
    
    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )
    
    val shimmerBrush = remember(shimmerPosition, shimmerColor, componentSize) {
        val widthPx = componentSize.width.toFloat()
        val shimmerWidth = widthPx * 0.4f
        val shimmerStart = widthPx * shimmerPosition - shimmerWidth * 0.5f
        val shimmerEnd = widthPx * shimmerPosition + shimmerWidth * 0.5f
        
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor.copy(alpha = 0.2f),
                shimmerColor.copy(alpha = 0.6f),
                shimmerColor.copy(alpha = 0.2f),
                Color.Transparent
            ),
            start = Offset(shimmerStart, 0f),
            end = Offset(shimmerEnd, 0f)
        )
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .padding(padding)
            .onGloballyPositioned { coordinates ->
                componentSize = coordinates.size
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = contentColor
            )
            
            if (isActive) {
                Spacer(modifier = Modifier.width(8.dp))
                ChatGPTDots()
            }
        }
        
        // Shimmer overlay
        if (isActive && componentSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = shimmerBrush,
                        alpha = 0.8f
                    )
            )
        }
    }
}

@Composable
private fun ChatGPTDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "chatgpt_dots")
    
    val dotAlphas = (0..2).map { index ->
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
            label = "dot_alpha_$index"
        )
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotAlphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        color = Color.White.copy(alpha = alpha.value),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

// Pre-built indicators for common status messages
@Composable
fun ThinkingShimmerIndicator(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = "Thinking",
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.primary,
        shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )
}

@Composable
fun AnalyzingShimmerIndicator(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = "Analyzing",
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.secondary,
        shimmerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    )
}

@Composable
fun SearchingWebShimmerIndicator(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = "Searching the web",
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.tertiary,
        shimmerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
    )
}

@Composable
fun GeneratingShimmerIndicator(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = "Generating",
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shimmerColor = Color.White.copy(alpha = 0.5f)
    )
}

@Composable
fun GeneratingResponseShimmerIndicator(
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = "Generating response",
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shimmerColor = Color.White.copy(alpha = 0.5f)
    )
}

@Composable
fun ProcessingShimmerIndicator(
    text: String = "Processing",
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    ChatGPTShimmerIndicator(
        text = text,
        modifier = modifier,
        isActive = isActive,
        containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.outline,
        shimmerColor = Color.White.copy(alpha = 0.4f)
    )
}

// Universal shimmer indicator that auto-detects the status
@Composable
fun UniversalShimmerIndicator(
    status: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    when (status.lowercase().trim()) {
        "thinking", "thinking..." -> ThinkingShimmerIndicator(modifier, isActive)
        "analyzing", "analyzing...", "analyzing photo" -> AnalyzingShimmerIndicator(modifier, isActive)
        "searching web", "searching web...", "searching the web" -> SearchingWebShimmerIndicator(modifier, isActive)
        "generating", "generating..." -> GeneratingShimmerIndicator(modifier, isActive)
        "generating response", "generating response..." -> GeneratingResponseShimmerIndicator(modifier, isActive)
        "processing", "processing..." -> ProcessingShimmerIndicator("Processing", modifier, isActive)
        "processing files", "processing files..." -> ProcessingShimmerIndicator("Processing files", modifier, isActive)
        "processing pdf", "processing pdf..." -> ProcessingShimmerIndicator("Processing PDF", modifier, isActive)
        "processing image", "processing image..." -> ProcessingShimmerIndicator("Processing image", modifier, isActive)
        "processing captured image", "processing captured image..." -> ProcessingShimmerIndicator("Processing captured image", modifier, isActive)
        "processing ocr results", "processing ocr results..." -> ProcessingShimmerIndicator("Processing OCR results", modifier, isActive)
        "processing text extraction", "processing text extraction..." -> ProcessingShimmerIndicator("Processing text extraction", modifier, isActive)
        "analyzing search results", "analyzing search results..." -> AnalyzingShimmerIndicator(modifier, isActive)
        "ai is thinking" -> ThinkingShimmerIndicator(modifier, isActive)
        "ai speaking", "ai speaking..." -> ProcessingShimmerIndicator("AI Speaking", modifier, isActive)
        else -> ProcessingShimmerIndicator(status, modifier, isActive)
    }
}