package com.erikblasko.tvstreamapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erikblasko.tvstreamapp.data.model.StreamStatus
import com.erikblasko.tvstreamapp.ui.theme.StreamChecking
import com.erikblasko.tvstreamapp.ui.theme.StreamOffline
import com.erikblasko.tvstreamapp.ui.theme.StreamOnline
import com.erikblasko.tvstreamapp.ui.theme.StreamUnknown

/**
 * Online = green dot, offline = red dot, checking = spinning orange arc,
 * unknown = small grey dot.
 */
@Composable
fun StatusIndicator(status: StreamStatus, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    when (status) {
        StreamStatus.ONLINE -> Canvas(modifier.size(size)) {
            drawCircle(color = StreamOnline)
        }
        StreamStatus.OFFLINE -> Canvas(modifier.size(size)) {
            drawCircle(color = StreamOffline)
        }
        StreamStatus.CHECKING -> {
            val transition = rememberInfiniteTransition(label = "checking")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "checking_angle"
            )
            Canvas(modifier.size(size)) {
                val stroke = this.size.minDimension * 0.24f
                rotate(angle) {
                    drawArc(
                        color = StreamChecking,
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(this.size.width - stroke, this.size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        StreamStatus.UNKNOWN -> Canvas(modifier.size(size)) {
            drawCircle(color = StreamUnknown, radius = this.size.minDimension * 0.3f)
        }
    }
}
