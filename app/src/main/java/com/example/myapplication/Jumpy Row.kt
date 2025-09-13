package com.example.myapplication

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt


@Composable
fun JumpyRow(
    modifier: Modifier = Modifier,
    waveWidth: Dp = 200.dp,
    waveHeight: Dp = 35.dp,
    animationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(2000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "Wave Progress"
    )

    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        // Convert wave dimensions to pixels
        val waveWidthPx = waveWidth.roundToPx()
        val waveHeightPx = waveHeight.roundToPx()

        // Measure items
        val placeables = measurables.map { it.measure(constraints) }

        // Calculate row dimensions
        val rowWidth = placeables.sumOf { it.width.toInt() }
        val maxHeight = placeables.maxOf { it.height }
        val rowHeight = maxHeight + waveHeightPx

        // Define layout
        layout(width = rowWidth, height = rowHeight) {
            var xPosition = 0

            // Calculate wave effect bounds
            val totalDistance = rowWidth + waveWidthPx
            val waveStart = -waveWidthPx + (totalDistance * waveProgress)
            val waveEnd = waveStart + waveWidthPx

            placeables.forEach { placeable ->
                val itemCenterX = xPosition + placeable.width / 2f
                val baseYPosition = rowHeight - placeable.height

                val yPosition = if (itemCenterX in waveStart..waveEnd) {
                    val normalizedX = normalizeX(
                        x = itemCenterX,
                        originalMin = waveStart,
                        originalMax = waveEnd,
                        targetMin = -2f,
                        targetMax = 2f
                    )
                    val waveEffect = waveCurve(normalizedX)
                    (baseYPosition - waveHeightPx * waveEffect).toInt()
                } else {
                    baseYPosition
                }

                placeable.place(x = xPosition, y = yPosition)
                xPosition += placeable.width
            }
        }
    }
}

private fun normalizeX(
    x: Float,
    originalMin: Float,
    originalMax: Float,
    targetMin: Float,
    targetMax: Float
): Float {
    return targetMin + ((x - originalMin) / (originalMax - originalMin)) * (targetMax - targetMin)
}

private fun waveCurve(x: Float): Float {
    return exp(-(x * x)) // 使用高斯曲线创建平滑波浪效果
}

// 使用示例
@Preview
@Composable
fun DemoJumpy() {

    val infiniteTransition = rememberInfiniteTransition()
    // 定义 alpha 值的动画，从 0.4f 到 0.6f，并以 RepeatMode.Reverse 方式反向播放
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing, delayMillis = 1300),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        JumpyRow(
            waveWidth = 160.dp,
            waveHeight = 20.dp,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing)
            )
        ) {


            // second state
            "Thinking...".forEach { char ->
                Text(
                    text = char.toString(),
                    modifier = Modifier.alpha(alpha),
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp
                )
            }

        }
    }
}








