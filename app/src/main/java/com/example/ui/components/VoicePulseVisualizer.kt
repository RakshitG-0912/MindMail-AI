package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.VoiceState

@Composable
fun VoicePulseVisualizer(
    voiceState: VoiceState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_2"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = this.size.minDimension / 3.2f

                // Outer animated pulse ring 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * pulseScale2 * (1f + audioLevel * 0.5f)
                    ),
                    radius = baseRadius * pulseScale2 * (1f + audioLevel * 0.5f),
                    center = center
                )

                // Middle pulse ring 1
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.4f),
                    radius = baseRadius * pulseScale1 * (1f + audioLevel * 0.3f),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Dynamic soundwave rings
                val dynamicRadius = baseRadius * (1f + (audioLevel * 0.6f))
                drawCircle(
                    color = primaryColor.copy(alpha = 0.6f),
                    radius = dynamicRadius,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        // Inner glowing core
        Box(
            modifier = Modifier
                .size(size * 0.48f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (voiceState == VoiceState.LISTENING) 0.9f else 0.7f),
                            secondaryColor
                        )
                    )
                )
        )
    }
}
