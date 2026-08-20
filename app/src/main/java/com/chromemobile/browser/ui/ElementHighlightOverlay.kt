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
        // Avoid drawing if the element covers >90% of the screen (e.g. full-screen body or main container)
        val w = highlightedRect.width.toFloat() * density
        val h = highlightedRect.height.toFloat() * density
        if (w >= size.width * 0.92f && h >= size.height * 0.85f) {
            return@Canvas
        }

        val left = highlightedRect.left.toFloat() * density
        val top = highlightedRect.top.toFloat() * density
        val width = w.coerceAtMost(size.width - left)
        val height = h.coerceAtMost(size.height - top)

        if (width <= 0 || height <= 0) return@Canvas

        // Draw subtle glowing background highlight
        drawRoundRect(
            color = Color(0x307C3AED).copy(alpha = alpha * 0.25f),
            topLeft = Offset(left - 3f, top - 3f),
            size = Size(width + 6f, height + 6f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Draw animated border stroke
        drawRoundRect(
            color = Color(0xFF7C3AED).copy(alpha = alpha),
            topLeft = Offset(left - 2f, top - 2f),
            size = Size(width + 4f, height + 4f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 3f)
        )
    }
}
