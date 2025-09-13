package com.example.myapplication

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@Preview
@Composable
fun PreviewExpandingList() {
    val items = List(6) { "Item ${it + 1}" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color.LightGray)
    ) {
        ExpandingScrollableList(
            items = items,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExpandingScrollableList(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    // 确保至少有6个元素
    val finalItems = if (items.size < 6) items + List(6 - items.size) { "Item ${items.size + it + 1}" } else items

    val animatable = remember { Animatable(0f) }

    // 定义大小项目的尺寸 (左侧项目)
    val largeWidthDp = 100.dp
    val largeHeightDp = 40.dp

    // 定义小项目的尺寸 (右侧堆叠项目)
    val smallWidthDp = 60.dp
    val smallHeightDp = 36.dp

    // 项目间的间距
    val itemSpacingDp = 12.dp

    val density = LocalDensity.current
    val largeWidthPx = with(density) { largeWidthDp.toPx() }
    val itemSpacingPx = with(density) { itemSpacingDp.toPx() }
    val smallWidthPx = with(density) { smallWidthDp.toPx() }

    // 标准项目宽度 (用于滑动计算)
    val standardItemWidthPx = largeWidthPx + itemSpacingPx
    val maxIndex = (finalItems.size - 1).toFloat()

    // 颜色列表
    val colors = listOf(
        Color(0xFFE57373), // 红色
        Color(0xFFFFB74D), // 橙色
        Color(0xFF64B5F6), // 蓝色
        Color(0xFFAED581), // 绿色
        Color(0xFFBA68C8), // 紫色
        Color(0xFFBA68C8)  // 紫色
    )

    // 堆叠卡片在右侧的起始位置
    val stackBasePosition = 3 * standardItemWidthPx

    Box(
        modifier = modifier
            .fling(
                animatable = animatable,
                itemCount = finalItems.size - 1,
                itemWidth = standardItemWidthPx
            )
    ) {
        finalItems.forEachIndexed { index, item ->
            // 计算项目与当前滑动位置的距离
            val distanceFromCurrent = index - animatable.value

            // 确定项目是否应该是大尺寸 (左侧) 或小尺寸 (右侧堆叠)
            val isLargeItem = distanceFromCurrent <= 2 // 前3个是大尺寸

            // 计算过渡中的项目
            val isTransitioning = distanceFromCurrent > 2 && distanceFromCurrent < 3

            // 使用转换系数确定大小 (0=小尺寸，1=大尺寸)
            val sizeFactor = if (isTransitioning) {
                1f - (distanceFromCurrent - 2f) // 2 到 3 之间时从 1 过渡到 0
            } else if (isLargeItem) {
                1f // 大尺寸
            } else {
                0f // 小尺寸
            }

            // 计算堆叠效果的位置，改进位置过渡
            val stackPosition = if (isLargeItem) {
                // 大尺寸项目正常排列
                distanceFromCurrent * standardItemWidthPx
            } else if (isTransitioning) {
                // 过渡项目，位置从左侧向右侧堆叠位置平滑移动
                val leftPosition = 2 * standardItemWidthPx // 最右侧大尺寸项目的位置
                val rightPosition = stackBasePosition // 堆叠位置
                leftPosition + (rightPosition - leftPosition) * (distanceFromCurrent - 2f)
            } else {
                // 堆叠项目向右级联偏移
                stackBasePosition + (index - 3) * 15f // 向右偏移，形成级联效果
            }

            // 根据sizeFactor计算实际宽度和高度
            val width = smallWidthDp + (largeWidthDp - smallWidthDp) * sizeFactor
            val height = smallHeightDp + (largeHeightDp - smallHeightDp) * sizeFactor

            val itemColor = if (index < colors.size) colors[index] else colors.last()

            Card(
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .graphicsLayer(
                        translationX = stackPosition,
                        // 不添加垂直偏移，所有堆叠卡片完全重叠
                        translationY = 0f,
                        // 缩放效果
                        scaleX = 0.9f + 0.1f * sizeFactor,
                        scaleY = 0.9f + 0.1f * sizeFactor,
                        // 透明度效果，让不活跃的项目更透明
                        alpha = 0.9f + 0.3f * sizeFactor
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = itemColor
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (abs(distanceFromCurrent) < 0.5f) 8.dp else 4.dp
                )
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        color = Color.White,
                        fontSize = (12 + 2 * sizeFactor).sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@SuppressLint("ModifierFactoryUnreferencedReceiver")
private fun Modifier.fling(
    animatable: Animatable<Float, AnimationVector1D>,
    itemCount: Int,
    itemWidth: Float
) = pointerInput(Unit) {
    val decay = exponentialDecay<Float>(frictionMultiplier = 1f)
    val springSpec = FloatSpringSpec(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    )

    coroutineScope {
        while (true) {
            // Wait for the first down event
            val pointerId = awaitPointerEventScope { awaitFirstDown().id }
            animatable.stop() // Stop any ongoing animations
            val velocityTracker = VelocityTracker()

            // Handle drag events
            awaitPointerEventScope {
                horizontalDrag(pointerId) { change ->
                    val dragOffset = change.positionChange().x
                    val horizontalDragOffset = animatable.value - dragOffset / itemWidth

                    launch {
                        val value = horizontalDragOffset.coerceIn(0f, itemCount.toFloat())
                        animatable.snapTo(value) // Snap to new value
                    }

                    velocityTracker.addPosition(change.uptimeMillis, change.position) // Track velocity
                    change.consume() // Consume the event
                }
            }

            // Calculate velocity and animate with spring to nearest item
            val velocity = velocityTracker.calculateVelocity().x
            val targetValue = decay.calculateTargetValue(animatable.value, -velocity / itemWidth)
            val targetIndex = targetValue.roundToInt().coerceIn(0, itemCount)

            launch {
                animatable.animateTo(
                    targetValue = targetIndex.toFloat(),
                    initialVelocity = -velocity / itemWidth,
                    animationSpec = springSpec
                )
            }
        }
    }
}