package com.cyberflux.qwinai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerDemoScreen() {
    val statusMessages = listOf(
        "Thinking",
        "Analyzing",
        "Searching the web",
        "Generating",
        "Generating response",
        "Processing",
        "Processing files",
        "Processing PDF",
        "Processing image",
        "Processing captured image",
        "Processing OCR results",
        "Processing text extraction",
        "Analyzing search results",
        "AI is thinking",
        "AI Speaking"
    )
    
    var activeDemo by remember { mutableStateOf(true) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = "ChatGPT-Style Shimmer Demo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Animation Active",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = activeDemo,
                            onCheckedChange = { activeDemo = it }
                        )
                    }
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
                        text = "Individual Shimmer Components",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    ThinkingShimmerIndicator(isActive = activeDemo)
                    AnalyzingShimmerIndicator(isActive = activeDemo)
                    SearchingWebShimmerIndicator(isActive = activeDemo)
                    GeneratingShimmerIndicator(isActive = activeDemo)
                    GeneratingResponseShimmerIndicator(isActive = activeDemo)
                    ProcessingShimmerIndicator("Processing files", isActive = activeDemo)
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
                        text = "Universal Shimmer Indicator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "Auto-detects status and applies appropriate shimmer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        items(statusMessages) { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status: \"$status\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    UniversalShimmerIndicator(
                        status = status,
                        isActive = activeDemo
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
                        text = "Dark Background Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.8f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChatGPTShimmerIndicator(
                                text = "Thinking",
                                isActive = activeDemo,
                                containerColor = Color.Gray.copy(alpha = 0.3f),
                                contentColor = Color.White,
                                shimmerColor = Color.White.copy(alpha = 0.8f)
                            )
                            
                            ChatGPTShimmerIndicator(
                                text = "Generating response",
                                isActive = activeDemo,
                                containerColor = Color.Gray.copy(alpha = 0.3f),
                                contentColor = Color.White,
                                shimmerColor = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
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
                        text = "Plain Text Shimmer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    ChatGPTShimmerText(
                        text = "This is a shimmer text example",
                        enabled = activeDemo,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shimmerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    
                    ChatGPTShimmerText(
                        text = "Analyzing your request...",
                        enabled = activeDemo,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shimmerColor = Color.White.copy(alpha = 0.9f),
                        baseColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerIntegrationExample() {
    var currentStatus by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }
    
    val statusOptions = listOf(
        "Thinking",
        "Analyzing",
        "Searching the web",
        "Generating",
        "Generating response",
        "Processing files"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Integration Example",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = "Select a status to see the shimmer effect:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(statusOptions) { status ->
                        Button(
                            onClick = {
                                currentStatus = status
                                isActive = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start: $status")
                        }
                    }
                    
                    item {
                        Button(
                            onClick = {
                                isActive = false
                                currentStatus = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop All")
                        }
                    }
                }
            }
        }
        
        if (isActive && currentStatus.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UniversalShimmerIndicator(
                        status = currentStatus,
                        isActive = isActive
                    )
                }
            }
        }
    }
}