package com.example.fraudapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorLow  = Color(0xFF4CAF50)
private val ColorMed  = Color(0xFFFF9800)
private val ColorHigh = Color(0xFFF44336)
private val ColorCrit = Color(0xFF9C0000)

private fun scoreColor(score: Double): Color {
    val s = score.coerceIn(0.0, 100.0)
    return when {
        s <= 30 -> lerp(ColorLow,  ColorMed,  (s / 30).toFloat())
        s <= 60 -> lerp(ColorMed,  ColorHigh, ((s - 30) / 30).toFloat())
        s <= 80 -> lerp(ColorHigh, ColorCrit, ((s - 60) / 20).toFloat())
        else    -> ColorCrit
    }
}

fun riskLabel(score: Double) = when {
    score < 30 -> "Low Risk"
    score < 60 -> "Medium Risk"
    score < 80 -> "High Risk"
    else       -> "Critical"
}

fun normaliseScore(raw: Double): Double =
    if (raw in 0.0..1.0) raw * 100.0 else raw.coerceIn(0.0, 100.0)

@Composable
fun FraudScoreGauge(rawScore: Double, modifier: Modifier = Modifier) {
    val score = normaliseScore(rawScore)

    val animated by animateFloatAsState(
        targetValue   = score.toFloat(),
        animationSpec = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
        label         = "gaugeScore"
    )
    val color = scoreColor(animated.toDouble())

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 18.dp.toPx()
                val padding  = strokePx / 2f + 2.dp.toPx()

                val cx = size.width  / 2f
                val cy = size.height
                val radius = minOf(cx - padding, cy - padding)

                val arcTopLeft = Offset(cx - radius, cy - radius)
                val arcSize    = Size(radius * 2f, radius * 2f)

                drawArc(
                    color      = Color(0xFFDDDDDD),
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter  = false,
                    topLeft    = arcTopLeft, size = arcSize,
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                val sweep = (animated / 100f) * 180f
                if (sweep > 0.5f) {
                    drawArc(
                        color      = color,
                        startAngle = 180f, sweepAngle = sweep,
                        useCenter  = false,
                        topLeft    = arcTopLeft, size = arcSize,
                        style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text       = animated.toInt().toString(),
                    fontSize   = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = color
                )
                Text(
                    text  = riskLabel(score),
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
        }
    }
}
