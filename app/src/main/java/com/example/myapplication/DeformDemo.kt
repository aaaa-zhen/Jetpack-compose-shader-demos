package com.example.myapplication

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
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
        
        // 减少边缘区域的扭曲强度 (减少衰减)
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

    // Resistance effect - 阻力系数 (减小阻力)
    val resistanceThreshold = 150f
    val maxResistance = 500f

    val actions = listOf(
        "Focus",
        "Heading 1",
        "List",
        "Task List",
        "Add Wikilink",
        "Configure Menu"
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a1a1a),
                        Color(0xFF000000)
                    )
                )
            ),
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

                                // 应用阻力效果 (减小阻力强度)
                                fingerPosition = if (distance > resistanceThreshold) {
                                    val resistance = min((distance - resistanceThreshold) / maxResistance, 1f)
                                    val dampingFactor = 1f - resistance * 0.4f // 40%阻力 (从70%减少)
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
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 16.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    // iOS风格选择器
                    Box(
                        modifier = Modifier
                            .height(itemHeight * 5) // 显示5个项目
                            .fillMaxWidth()
                    ) {
                        // 背景选择指示器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .offset(y = itemHeight * 2) // 居中位置
                                .background(
                                    Color(0xFF007AFF).copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                        )

                        // 滚动列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            // 滚动结束后，自动对齐到最近的项目
                                            val targetOffset = selectedIndex * itemHeightPx
                                            scrollOffset = targetOffset
                                        }
                                    ) { _, dragAmount ->
                                        scrollOffset = (scrollOffset - dragAmount.y)
                                            .coerceIn(0f, (actions.size - 1) * itemHeightPx)
                                    }
                                },
                            state = rememberLazyListState(),
                            contentPadding = PaddingValues(vertical = itemHeight * 2), // 上下各留2个项目的空间
                            userScrollEnabled = false // 禁用默认滚动，使用自定义拖拽
                        ) {
                            itemsIndexed(actions) { index, action ->
                                val offsetFromSelected = abs(index - selectedIndex)
                                val alpha = when (offsetFromSelected) {
                                    0 -> 1f
                                    1 -> 0.6f
                                    2 -> 0.3f
                                    else -> 0.1f
                                }

                                val scale = when (offsetFromSelected) {
                                    0 -> 1f
                                    1 -> 0.8f
                                    else -> 0.6f
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
                                            // 处理选择
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = action,
                                        color = if (index == selectedIndex)
                                            Color(0xFF007AFF) else Color(0xFF333333),
                                        fontSize = if (index == selectedIndex) 18.sp else 16.sp,
                                        fontWeight = if (index == selectedIndex)
                                            FontWeight.Bold else FontWeight.Medium
                                    )

                                    if (index == selectedIndex) {
                                        Text(
                                            text = "›",
                                            color = Color(0xFF007AFF).copy(alpha = 0.5f),
                                            fontSize = 18.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentWidth(Alignment.End)
                                                .padding(end = 20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}