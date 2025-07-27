package com.cyberflux.qwinai.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShimmerUsageExample() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Shimmer Text Examples",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Basic Shimmer Indicators",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    ThinkingIndicator()
                    AnalyzingIndicator()
                    SearchingWebIndicator()
                    ProcessingIndicator("Processing files...")
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advanced Shimmer Effects",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    PulsingShimmerIndicator(
                        status = "Thinking",
                        primaryColor = MaterialTheme.colorScheme.primary,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    
                    WaveShimmerIndicator(
                        status = "Analyzing",
                        waveColor = MaterialTheme.colorScheme.tertiary
                    )
                    
                    AdvancedShimmerText(
                        text = "Searching the web",
                        shimmerColors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Status Collection (Auto-detect)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    StatusIndicatorCollection(
                        currentStatus = "thinking"
                    )
                    
                    StatusIndicatorCollection(
                        currentStatus = "analyzing"
                    )
                    
                    StatusIndicatorCollection(
                        currentStatus = "searching the web"
                    )
                }
            }
        }
    }
}

@Composable
fun IntegrationExample() {
    var currentStatus by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                isProcessing = true
                currentStatus = "thinking"
            }
        ) {
            Text("Start Thinking")
        }
        
        Button(
            onClick = {
                isProcessing = true
                currentStatus = "analyzing"
            }
        ) {
            Text("Start Analyzing")
        }
        
        Button(
            onClick = {
                isProcessing = true
                currentStatus = "searching the web"
            }
        ) {
            Text("Start Web Search")
        }
        
        Button(
            onClick = {
                isProcessing = false
                currentStatus = ""
            }
        ) {
            Text("Stop Processing")
        }
        
        if (isProcessing && currentStatus.isNotEmpty()) {
            StatusIndicatorCollection(
                currentStatus = currentStatus,
                isActive = isProcessing
            )
        }
    }
}

object ShimmerIntegrationHelper {
    
    @Composable
    fun GetStatusIndicator(
        status: String,
        isActive: Boolean = true,
        modifier: Modifier = Modifier
    ) {
        when (status.lowercase()) {
            "thinking..." -> ThinkingIndicator(modifier, isActive)
            "analyzing..." -> AnalyzingIndicator(modifier, isActive)
            "searching web..." -> SearchingWebIndicator(modifier, isActive)
            "generating response..." -> ProcessingIndicator("Generating response", modifier, isActive)
            "processing files..." -> ProcessingIndicator("Processing files", modifier, isActive)
            "processing pdf..." -> ProcessingIndicator("Processing PDF", modifier, isActive)
            "processing image..." -> ProcessingIndicator("Processing image", modifier, isActive)
            "processing..." -> ProcessingIndicator("Processing", modifier, isActive)
            "analyzing photo" -> AnalyzingIndicator(modifier, isActive)
            "ai is thinking" -> ThinkingIndicator(modifier, isActive)
            "processing captured image..." -> ProcessingIndicator("Processing captured image", modifier, isActive)
            "analyzing search results..." -> AnalyzingIndicator(modifier, isActive)
            "processing ocr results..." -> ProcessingIndicator("Processing OCR results", modifier, isActive)
            "processing text extraction..." -> ProcessingIndicator("Processing text extraction", modifier, isActive)
            else -> ProcessingIndicator(status, modifier, isActive)
        }
    }
    
    fun getShimmerColorForStatus(status: String, colorScheme: ColorScheme): Color {
        return when (status.lowercase()) {
            "thinking..." -> colorScheme.primary
            "analyzing..." -> colorScheme.secondary
            "searching web..." -> colorScheme.tertiary
            "generating response..." -> colorScheme.primary
            "processing files..." -> colorScheme.outline
            "processing pdf..." -> colorScheme.error
            "processing image..." -> colorScheme.secondary
            else -> colorScheme.primary
        }
    }
}