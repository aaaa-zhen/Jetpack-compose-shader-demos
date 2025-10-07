package com.example.myapplication

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.VelocityTracker
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
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
private const val CONTAINER_OFFSET_FACTOR = 0.10f   // 外层容器视差比例（0.05~0.12）
private const val RESISTANCE_K = 0.9f               // 橡皮筋阻尼强度（越大越“紧”）
private const val MAX_OVERDRAG_DP = 250f            // 有效过拉上限（像素映射自 dp）
private const val TRANSLATION_NUDGE_DP = 10f        // 内层轻微随动
private const val MAX_STRETCH = 0.30f               // 主轴最大拉伸比例
private const val SQUASH = 1.25f                    // 正交方向压缩倍数
private const val DEADZONE_DP = 2f                  // 起始判定死区
private const val SWITCH_HYSTERESIS_DP = 12f        // 换向迟滞阈值（越大越不易误切）

private enum class Axis { NONE, H, V }

// -------------------- 组件 --------------------
@Composable
fun KnotButtonWithResistance(
    size: Dp,
    onClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val radius = sizePx / 2f
    val view = LocalView.current

    // 形变需要的 layer 尺寸
    var layerW by remember { mutableStateOf(0f) }
    var layerH by remember { mutableStateOf(0f) }

    // 带阻尼的“有效过拉向量”
    val overVec = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var dragUpdateJob by remember { mutableStateOf<Job?>(null) }

    // 手势状态
    var pressStart by remember { mutableStateOf(Offset.Zero) }
    var pivotX by remember { mutableStateOf(0.5f) }
    var pivotY by remember { mutableStateOf(0.5f) }
    var axis by remember { mutableStateOf(Axis.NONE) }
    var hasEnteredOverdrag by remember { mutableStateOf(false) }

    // 参数映射
    val maxOverdrag = with(density) { MAX_OVERDRAG_DP.dp.toPx() }
    val translationNudge = with(density) { TRANSLATION_NUDGE_DP.dp.toPx() }
    val deadzone = with(density) { DEADZONE_DP.dp.toPx() }
    val switchHysteresis = with(density) { SWITCH_HYSTERESIS_DP.dp.toPx() }

    // iOS-like 橡皮筋映射：x -> t*limit，t = x/(x+k*limit)
    fun rubberBand(exceed: Float, limit: Float, k: Float): Float {
        val x = exceed.coerceAtLeast(0f)
        val t = x / (x + k * limit)
        return t * limit
    }

    fun computeOverdragWithResistance(start: Offset, current: Offset): Offset {
        val d = current - start
        val dist = hypot(d.x, d.y)
        val exceed = dist - radius
        if (exceed <= 0f) return Offset.Zero
        val ux = d.x / max(dist, 1e-3f)
        val uy = d.y / max(dist, 1e-3f)
        val adjusted = rubberBand(exceed, maxOverdrag, RESISTANCE_K)
        return Offset(ux * adjusted, uy * adjusted)
    }

    // 低阶手势：支持中途换向；受限域内不直接调用挂起动画 API
    val pointerMod = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown()
                pressStart = down.position
                axis = Axis.NONE
                hasEnteredOverdrag = false
                view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)

                var pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue

                    val d = change.position - pressStart
                    val ax = abs(d.x)
                    val ay = abs(d.y)

                    // 判定/切换主轴（带死区+迟滞）
                    val desired = when {
                        ax <= deadzone && ay <= deadzone -> Axis.NONE
                        ax >= ay -> Axis.H
                        else -> Axis.V
                    }
                    when (axis) {
                        Axis.NONE -> {
                            if (desired != Axis.NONE) {
                                axis = desired
                                if (axis == Axis.H) {
                                    pivotY = 0.5f
                                    pivotX = if (d.x < 0f) 1f else 0f
                                } else {
                                    pivotX = 0.5f
                                    pivotY = if (d.y < 0f) 1f else 0f
                                }
                            }
                        }
                        Axis.H -> {
                            if (ay - ax > switchHysteresis) {
                                axis = Axis.V
                                pivotX = 0.5f
                                pivotY = if (d.y < 0f) 1f else 0f
                                view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)
                            } else {
                                pivotY = 0.5f
                                pivotX = if (d.x < 0f) 1f else 0f
                            }
                        }
                        Axis.V -> {
                            if (ax - ay > switchHysteresis) {
                                axis = Axis.H
                                pivotY = 0.5f
                                pivotX = if (d.x < 0f) 1f else 0f
                                view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)
                            } else {
                                pivotX = 0.5f
                                pivotY = if (d.y < 0f) 1f else 0f
                            }
                        }
                    }

                    // 过拉 + 阻尼 → 交给非受限域的 Job
                    val raw = computeOverdragWithResistance(pressStart, change.position)
                    if (!hasEnteredOverdrag && (raw.x != 0f || raw.y != 0f)) {
                        hasEnteredOverdrag = true
                    }
                    dragUpdateJob?.cancel()
                    dragUpdateJob = scope.launch {
                        overVec.snapTo(raw) // ✅ 非受限域中调用
                    }

                    // 结束判定
                    if (!change.pressed) break else change.consume()
                }

                // 收尾：回弹动画也放进非受限域
                dragUpdateJob?.cancel()
                scope.launch {
                    view.performHapticFeedback(HapticFeedbackConstantsCompat.LONG_PRESS)
                    overVec.animateTo(Offset.Zero, spring(dampingRatio = 1f, stiffness = 200f))
                }
            }
        }
    }

    // —— 视觉派生量 —— //
    val od = overVec.value
    val odMag = hypot(od.x, od.y)
    val overFrac = (odMag / maxOverdrag).coerceIn(0f, 1f)
    val eased = sqrt(overFrac)
    val s = eased * MAX_STRETCH

    val horizontal = (axis == Axis.H)
    val scaleX = (if (horizontal) 1f + s else 1f - s * SQUASH).coerceAtLeast(0.6f)
    val scaleY = (if (horizontal) 1f - s * SQUASH else 1f + s).coerceAtLeast(0.6f)

    // 钉边补偿
    val compTx = (0.5f - pivotX) * layerW * (scaleX - 1f)
    val compTy = (0.5f - pivotY) * layerH * (scaleY - 1f)

    // 轻微随动（沿过拉方向）
    val nx = if (odMag > 0f) (od.x / odMag) * eased * translationNudge else 0f
    val ny = if (odMag > 0f) (od.y / odMag) * eased * translationNudge else 0f

    // 外层容器放大，避免裁切
    val extraPadPx =
        CONTAINER_OFFSET_FACTOR * maxOverdrag +
                translationNudge +
                0.5f * sizePx * MAX_STRETCH * SQUASH
    val extraPad = with(density) { extraPadPx.toDp() }

    // ---------------- 外层：容器视差跟随（不裁剪） ----------------
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
        // --------------- 内层：按钮本体（裁剪+形变+补偿+随动） ---------------
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
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    translationX = compTx + nx
                    translationY = compTy + ny
                    clip = true
                    shape = CircleShape
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 纯蓝底 + 白色 X
            Canvas(Modifier.fillMaxSize()) {
                val r = radius
                drawCircle(color = Color(0xFF2F6FEE))
                drawCircle(color = Color(0x14000000), style = Stroke(width = r * 0.06f))
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

// -------------------- Preview --------------------
@Preview(showBackground = true)
@Composable
fun JellyDemo() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)),
        contentAlignment = Alignment.Center
    ) {
        KnotButtonWithResistance(size = 96.dp)
    }
}