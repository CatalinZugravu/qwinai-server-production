package com.cyberflux.qwinai.components

import android.os.Bundle
import com.cyberflux.qwinai.utils.BaseThemedActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ShimmerTestActivity : BaseThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShimmerTestScreen()
            }
        }
    }
}

@Composable
fun ShimmerTestScreen() {
    var isActive by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Diagonal Shimmer Effect Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Button(
            onClick = { isActive = !isActive }
        ) {
            Text(if (isActive) "Stop Shimmer" else "Start Shimmer")
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Realistic Shimmer Text:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmoothShimmerText(
                text = "Thinking...",
                enabled = isActive
            )
            SmoothShimmerText(
                text = "Analyzing...",
                enabled = isActive
            )
            SmoothShimmerText(
                text = "Searching the web...",
                enabled = isActive
            )
            SmoothShimmerText(
                text = "Generating response...",
                enabled = isActive
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Canvas-based Shimmer:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RealisticShimmerText(
                text = "Thinking...",
                enabled = isActive
            )
            RealisticShimmerText(
                text = "Analyzing...",
                enabled = isActive
            )
            RealisticShimmerText(
                text = "Searching the web...",
                enabled = isActive
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Status Indicators with Loading:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerThinkingIndicator(isActive = isActive)
            ShimmerAnalyzingIndicator(isActive = isActive)
            ShimmerSearchingWebIndicator(isActive = isActive)
            ShimmerGeneratingResponseIndicator(isActive = isActive)
            ShimmerProcessingIndicator("Processing files...", isActive = isActive)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Seamless Continuous Shimmer:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeamlessShimmerText(
                text = "Thinking...",
                enabled = isActive
            )
            SeamlessShimmerText(
                text = "Analyzing...",
                enabled = isActive
            )
            SeamlessShimmerText(
                text = "Searching the web...",
                enabled = isActive
            )
            SeamlessShimmerText(
                text = "Generating response...",
                enabled = isActive
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Dual-Layer Continuous:",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContinuousShimmerText(
                text = "Thinking...",
                enabled = isActive
            )
            ContinuousShimmerText(
                text = "Analyzing...",
                enabled = isActive
            )
        }
    }
}