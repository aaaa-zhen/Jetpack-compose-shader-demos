// 关键点：把效果挂在“卡片容器”上，不是内部内容
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val deformShader = """
    uniform shader image;
    uniform float2 resolution;
    uniform float percentage;
    uniform float2 touch;
    
    vec4 GetImageTexture(vec2 p, vec2 pivot, vec2 r) {
        p.x /= r.x / r.y;
        p += pivot;
        p *= r;
        return image.eval(p);
    }
    
    half4 main(float2 fragCoord) {
        float ratio = resolution.x / resolution.y;
        float2 uv = fragCoord / resolution - 0.5;
        uv.x *= ratio;
        
        vec2 nMouse = touch / resolution;
        nMouse.x *= ratio;
        
        // 计算距离中心的距离，用于减少边缘扭曲
        float distFromCenter = length(uv);
        float edgeFalloff = smoothstep(0.3, 0.6, distFromCenter);
        
        vec2 scale = vec2(min(length(nMouse.x), 0.18), min(length(nMouse.y), 0.22));
        float influence = dot(normalize(nMouse), uv);
        influence = smoothstep(0., 0.9, influence);
        
        // 减少边缘区域的扭曲强度
        scale *= influence * (1.0 - edgeFalloff * 0.5);
        scale *= percentage;
        
        uv *= vec2(1.0) - scale;
        
        vec4 img = GetImageTexture(uv, vec2(0.5), resolution);
        return half4(img);
    }
""".trimIndent()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview
@Composable
fun DeformDemo() {
    val shader by remember { mutableStateOf(RuntimeShader(deformShader)) }
    var targetPercentage by remember { mutableFloatStateOf(0f) }
    val percentage = animateFloatAsState(
        targetPercentage,
        animationSpec = spring(0.9f, 180f),
        label = "percentage"
    )
    val pressed = remember { mutableStateOf(false) }
    var fingerPosition by remember { mutableStateOf(Offset.Zero) }
    var fingerStartPosition by remember { mutableStateOf(Offset.Zero) }

    // Resistance effect - 阻力系数
    val resistanceThreshold = 150f
    val maxResistance = 500f

    val actions = listOf(
        "Focus", "Heading 1", "List", "Task List", "Add Wikilink", "Configure Menu"
    )

    // iOS风格选择器状态
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val itemHeight = 56.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // 监听滚动变化，计算当前选中项
    LaunchedEffect(scrollOffset) {
        val newIndex = (scrollOffset / itemHeightPx + 0.5f).roundToInt()
        selectedIndex = newIndex.coerceIn(0, actions.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .onSizeChanged { size ->
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            pressed.value = change.pressed
                            if (change.pressed) {
                                if (change.previousPressed.not()) {
                                    fingerStartPosition = change.position
                                }
                                targetPercentage = 1f

                                // 计算原始偏移
                                val rawOffset = change.position - fingerStartPosition
                                val distance = sqrt(rawOffset.x * rawOffset.x + rawOffset.y * rawOffset.y)

                                // 应用阻力效果
                                fingerPosition = if (distance > resistanceThreshold) {
                                    val resistance = min((distance - resistanceThreshold) / maxResistance, 1f)
                                    val dampingFactor = 1f - resistance * 0.4f
                                    Offset(
                                        rawOffset.x * dampingFactor,
                                        rawOffset.y * dampingFactor
                                    )
                                } else {
                                    rawOffset
                                }
                            } else {
                                targetPercentage = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .graphicsLayer {
                    if (pressed.value) {
                        shader.setFloatUniform("percentage", 1f)
                    } else {
                        shader.setFloatUniform("percentage", percentage.value)
                    }
                    shader.setFloatUniform("touch", fingerPosition.x, fingerPosition.y)
                    this.renderEffect = android.graphics.RenderEffect
                        .createRuntimeShaderEffect(shader, "image")
                        .asComposeRenderEffect()
                }
        ) {
            Card(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 20.dp,
                    pressedElevation = 12.dp
                )
            ) {
                // iOS风格选择器
                Box(
                    modifier = Modifier
                        .height(itemHeight * 5)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // 选择指示器
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .offset(y = itemHeight * 2)
                            .background(
                                Color(0xFF007AFF).copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            )
                    )

                    // 滚动列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val targetOffset = selectedIndex * itemHeightPx
                                        scrollOffset = targetOffset
                                    }
                                ) { _, dragAmount ->
                                    scrollOffset = (scrollOffset - dragAmount.y)
                                        .coerceIn(0f, (actions.size - 1) * itemHeightPx)
                                }
                            },
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(vertical = itemHeight * 2),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(actions) { index, action ->
                            val offsetFromSelected = abs(index - selectedIndex)
                            val alpha = when (offsetFromSelected) {
                                0 -> 1f
                                1 -> 0.5f
                                2 -> 0.25f
                                else -> 0.1f
                            }
                            val scale = when (offsetFromSelected) {
                                0 -> 1f
                                1 -> 0.9f
                                else -> 0.8f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clickable {
                                        selectedIndex = index
                                        scrollOffset = index * itemHeightPx
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = action,
                                        color = if (index == selectedIndex) Color(0xFF1A1A1A) else Color(0xFF666666),
                                        fontSize = if (index == selectedIndex) 17.sp else 16.sp,
                                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}