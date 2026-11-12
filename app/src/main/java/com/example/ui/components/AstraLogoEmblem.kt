package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AstraLogoEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AstraEmblemTransition")
    val pulseScale by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    val starColor = MaterialTheme.colorScheme.onSurface
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale)
            .border(1.dp, borderColor, CircleShape)
            .background(cardColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // High-precision minimalist 8-pointed geometric star
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            val path = Path()

            // 4 major cardinal points + 4 subtle intermediate facets
            val outerRadius = w / 2f
            val innerRadius = outerRadius * 0.28f

            // Construct smooth 4-pointed curved star (diamond flare)
            path.moveTo(cx, 0f)
            path.quadraticTo(cx, cy, w, cy)
            path.quadraticTo(cx, cy, cx, h)
            path.quadraticTo(cx, cy, 0f, cy)
            path.quadraticTo(cx, cy, cx, 0f)
            path.close()

            drawPath(path = path, color = starColor)

            // Center subtle core circle for futuristic look
            drawCircle(
                color = cardColor,
                radius = innerRadius * 0.5f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}
