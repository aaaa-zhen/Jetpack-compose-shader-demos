package com.example.myapplication

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.VelocityTracker
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

@Preview
@Composable
fun JellyShape(){
    var isExpanded by remember { mutableStateOf(false) }

    // 使用 Animatable 来获取动画进度
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(isExpanded) {
        animationProgress.animateTo(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio =1f,
                stiffness = 150f
            )
        )
    }

    // 根据动画进度计算目标尺寸
    val progress = animationProgress.value
    val isMoving = progress > 0f && progress < 1f

    // 计算目标尺寸
    val targetWidth = if (isMoving) {
        120f + sin(progress * PI.toFloat()) * 60f // 移动中：120→180→120
    } else {
        120f // 静止时：120
    }

    val targetHeight = if (isMoving) {
        80f + sin(progress * PI.toFloat()) * 20f // 移动中：80→70→80
    } else {
        80f // 静止时：80
    }

    // 使用 Spring 动画让 width 和 height 平滑过渡到目标值
    val width by animateDpAsState(
        targetValue = targetWidth.dp,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 100f
        ),
        label = "width"
    )

    val height by animateDpAsState(
        targetValue = targetHeight.dp,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 100f
        ),
        label = "height"
    )

    val offsetX = (progress * 150f).dp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        Box(
            Modifier
                .width(width)
                .height(height)
                .offset(x = offsetX)
                .background(Color.Red.copy(1f), shape = RoundedCornerShape(40.dp))
                .clickable { isExpanded = !isExpanded }
        ){

        }


    }
}



// -------------------- 可调参数 --------------------
private const val CONTAINER_OFFSET_FACTOR = 0.10f
private const val RESISTANCE_K = 1.2f
private const val MAX_OVERDRAG_DP = 250f
private const val TRANSLATION_NUDGE_DP = 10f
private const val MAX_STRETCH = 0.10f
private const val SQUASH = 1.05f
private const val DEADZONE_DP = 2f

// -------------------- 组件 --------------------
@Composable
fun KnotButtonOmniDirection(
    size: Dp,
    onClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val radius = sizePx / 2f
    val view = LocalView.current

    var layerW by remember { mutableStateOf(0f) }
    var layerH by remember { mutableStateOf(0f) }

    val overVec = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var dragUpdateJob by remember { mutableStateOf<Job?>(null) }

    var pressStart by remember { mutableStateOf(Offset.Zero) }
    var hasStartedDrag by remember { mutableStateOf(false) }

    // 🎯 按压缩放状态
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.1f else 1f,
        animationSpec = if (isPressed)
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        else
            spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioHighBouncy,
            ),
        label = "pressScale"
    )

    val maxOverdrag = with(density) { MAX_OVERDRAG_DP.dp.toPx() }
    val translationNudge = with(density) { TRANSLATION_NUDGE_DP.dp.toPx() }
    val deadzone = with(density) { DEADZONE_DP.dp.toPx() }

    fun rubberBand(exceed: Float, limit: Float, k: Float): Float {
        val x = exceed.coerceAtLeast(0f)
        val t = x / (x + k * limit)
        return t * limit
    }

    fun computeOverdragWithResistance(start: Offset, current: Offset): Offset {
        val d = current - start
        val dist = hypot(d.x, d.y)

        if (dist <= deadzone) return Offset.Zero

        val exceed = dist - radius
        if (exceed <= 0f) return Offset.Zero

        val ux = d.x / max(dist, 1e-3f)
        val uy = d.y / max(dist, 1e-3f)
        val adjusted = rubberBand(exceed, maxOverdrag, RESISTANCE_K)
        return Offset(ux * adjusted, uy * adjusted)
    }

    fun computePivot(overVec: Offset): Pair<Float, Float> {
        val mag = hypot(overVec.x, overVec.y)
        if (mag < 1e-3f) return 0.5f to 0.5f

        val nx = overVec.x / mag
        val ny = overVec.y / mag

        val pivotX = (0.5f - nx * 0.5f).coerceIn(0f, 1f)
        val pivotY = (0.5f - ny * 0.5f).coerceIn(0f, 1f)

        return pivotX to pivotY
    }

    val pointerMod = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown()
                pressStart = down.position
                hasStartedDrag = false

                // 🎯 按下时触发缩放
                isPressed = true
                view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)

                var pointerId = down.id

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue

                    // 🎯 只有在按压放大后才允许拖动拉伸
                    val raw = if (pressScale >= 1.05f) {
                        computeOverdragWithResistance(pressStart, change.position)
                    } else {
                        Offset.Zero
                    }

                    if (!hasStartedDrag && (raw.x != 0f || raw.y != 0f)) {
                        hasStartedDrag = true
                    }

                    dragUpdateJob?.cancel()
                    dragUpdateJob = scope.launch {
                        overVec.snapTo(raw)
                    }

                    if (!change.pressed) break
                    else change.consume()
                }

                // 🎯 松手:取消按压 + 回弹拉伸
                isPressed = false
                dragUpdateJob?.cancel()
                scope.launch {
                    if (hasStartedDrag) {
                        view.performHapticFeedback(HapticFeedbackConstantsCompat.LONG_PRESS)
                    }
                    overVec.animateTo(
                        Offset.Zero,
                        spring(
                            dampingRatio = 1f,
                            stiffness = 200f,
                            visibilityThreshold = Offset(.000001f, .000001f)
                        )
                    )
                }
            }
        }
    }

    // —— 视觉派生 ——
    val od = overVec.value
    val odMag = hypot(od.x, od.y)
    val overFrac = (odMag / maxOverdrag).coerceIn(0f, 1f)
    val eased = sqrt(overFrac)

    val stretch = eased * MAX_STRETCH

    val absX = abs(od.x)
    val absY = abs(od.y)
    val total = absX + absY + 1e-3f
    val xWeight = absX / total
    val yWeight = absY / total

    val scaleX = (1f + stretch * xWeight - stretch * yWeight * SQUASH).coerceAtLeast(0.6f)
    val scaleY = (1f + stretch * yWeight - stretch * xWeight * SQUASH).coerceAtLeast(0.6f)

    val (pivotX, pivotY) = computePivot(od)

    val compTx = (0.5f - pivotX) * layerW * (scaleX - 1f)
    val compTy = (0.5f - pivotY) * layerH * (scaleY - 1f)

    val nx = if (odMag > 0f) (od.x / odMag) * eased * translationNudge else 0f
    val ny = if (odMag > 0f) (od.y / odMag) * eased * translationNudge else 0f

    val extraPadPx = CONTAINER_OFFSET_FACTOR * maxOverdrag + translationNudge +
            0.5f * sizePx * MAX_STRETCH * SQUASH
    val extraPad = with(density) { extraPadPx.toDp() }

    // 外层容器
    Box(
        modifier = Modifier
            .size(size + extraPad * 2)
            .graphicsLayer {
                translationX = od.x * CONTAINER_OFFSET_FACTOR
                translationY = od.y * CONTAINER_OFFSET_FACTOR
                clip = false
            },
        contentAlignment = Alignment.Center
    ) {
        // 内层按钮
        Box(
            modifier = Modifier
                .size(size)
                .onGloballyPositioned {
                    layerW = it.size.width.toFloat()
                    layerH = it.size.height.toFloat()
                }
                .then(pointerMod)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                    // 🎯 叠加按压缩放 + 拉伸形变
                    this.scaleX = scaleX * pressScale
                    this.scaleY = scaleY * pressScale
                    translationX = compTx + nx
                    translationY = compTy + ny
                    clip = true
                    shape = CircleShape
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = radius
                drawCircle(color = Color(0xFF2F6FEE))
                drawCircle(
                    color = Color(0x14000000),
                    style = Stroke(width = r * 0.06f)
                )

                val crossR = r * 0.22f
                val stroke = r * 0.12f
                drawLine(
                    color = Color.White,
                    start = center + Offset(-crossR, -crossR),
                    end = center + Offset(crossR, crossR),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = center + Offset(crossR, -crossR),
                    end = center + Offset(-crossR, crossR),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OmniDirectionDemo() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)),
        contentAlignment = Alignment.Center
    ) {
        KnotButtonOmniDirection(size = 96.dp)
    }
}