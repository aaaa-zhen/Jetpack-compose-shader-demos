package com.example.myapplication

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay


// 你的原版本，只做最小修改
val MinimalFixDynamicIsland = """
    uniform float2 resolution;
    uniform shader image;
    uniform float circleX;  // 由 Compose 动画控制，移除 time
    
    half4 main(vec2 fragCoord) {
        float2 uv = fragCoord/resolution.xy;
        float aspect = resolution.x/resolution.y;
        
        float2 adjustedUV = float2(uv.x * aspect - aspect*0.5, uv.y - 0.5);
        
        // 圆的位置由外部控制（原来是 sin(time * 0.8) * 0.3）
        float2 circlePos = float2(circleX, 0.0);
        
        // 圆角矩形（完全保持你的原参数）
        float2 rectCenter = float2(0.0, 0.0);
        float2 rectSize = float2(0.12, 0.04);
        float cornerRadius = 0.04;
        
        // 圆角矩形的距离场（完全保持原来的）
        float2 d = abs(adjustedUV - rectCenter) - rectSize + cornerRadius;
        float rect_distance = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - cornerRadius;
        
        // 圆的距离场（完全保持原来的）
        float circle_distance = length(adjustedUV - circlePos) - 0.03;
        
        // 使用平滑最小值进行融合（完全保持原来的）
        float k = 0.05;
        float h = clamp(0.5 + 0.5 * (circle_distance - rect_distance) / k, -0.1, 1.0);
        float combined_distance = mix(circle_distance, rect_distance, h) - k * h * (1. - h);
        
        // 唯一修改：改为你喜欢的 smoothstep 范围
   
        float final = 1. - step(0.0, combined_distance);  // 最锐利
        
        return half4(final, final, final, 1);
    }
""".trimIndent()

@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MinimalFixDynamicIslandEffect() {
    val shader = remember { RuntimeShader(MinimalFixDynamicIsland) }

    // 使用 Compose Spring 动画，控制在较小范围内移动
    var isMoving by remember { mutableStateOf(false) }

    val circleX by animateFloatAsState(
        targetValue = if (isMoving) 0.25f else -0.25f,  // 减小移动范围
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 250f
        ),
        label = "circleMovement"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            isMoving = !isMoving
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
                .graphicsLayer {
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(
                            shader.apply {
                                setFloatUniform("circleX", circleX)
                            },
                            "image"
                        )
                        .asComposeRenderEffect()
                }
                .background(Color.Black)
                .clickable {
                    isMoving = !isMoving
                }
        )

        Text(
            text = "点击切换动画方向",
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

val MetaballShader = """
    uniform float2 resolution;
    uniform shader image;
    uniform float time;
    
    half4 main(vec2 fragCoord) {
        float2 uv = fragCoord/resolution.xy;
        float aspect = resolution.x/resolution.y;
        
        float2 adjustedUV = float2(uv.x * aspect - aspect*0.5, uv.y - 0.5);
        
       
        float movement = 0.15 * sin(time);
        
      
        float d1 = length(adjustedUV - float2(movement, 0.0));
        float d2 = length(adjustedUV + float2(movement, 0.0));
        
     
        float metaball = 1.0/(d1*20.0) + 1.0/(d2*20.0);
        metaball = metaball * 0.5;  
        
        float final = smoothstep(0.5, 0.5, metaball);
        
        return half4(final, final, final, 1);
    }
""".trimIndent()

@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun Metaball() {
    val shader = remember { RuntimeShader(MetaballShader) }
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while(true) {
            withFrameNanos { frameTime ->
                time = (frameTime / 1_000_000_000f)  // 转换纳秒到秒
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
                .graphicsLayer {
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(
                            shader.apply {
                                setFloatUniform("time", time)
                            },
                            "image"
                        )
                        .asComposeRenderEffect()
                }
                .background(Color.White)
        )
    }
}