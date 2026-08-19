package com.chromemobile.browser.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.chromemobile.browser.agent.ElementRect

@Composable
fun ElementHighlightOverlay(
    highlightedRect: ElementRect?,
    density: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (highlightedRect == null || highlightedRect.width <= 0 || highlightedRect.height <= 0) {
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val left = highlightedRect.left.toFloat() * density
        val top = highlightedRect.top.toFloat() * density
        val width = highlightedRect.width.toFloat() * density
        val height = highlightedRect.height.toFloat() * density

        // Draw glowing background highlight
        drawRoundRect(
            color = Color(0x407C3AED).copy(alpha = alpha * 0.3f),
            topLeft = Offset(left - 4f, top - 4f),
            size = Size(width + 8f, height + 8f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Draw animated border stroke
        drawRoundRect(
            color = Color(0xFF7C3AED).copy(alpha = alpha),
            topLeft = Offset(left - 2f, top - 2f),
            size = Size(width + 4f, height + 4f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 3.5f)
        )
    }
}
