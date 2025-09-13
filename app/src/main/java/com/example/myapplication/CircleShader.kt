package com.example.myapplication

import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt


private  val circularTimerSHADER ="""
    
 uniform float time;
 uniform float percentage;
 uniform vec2 resolution;
 uniform shader image;
 
 vec2 cartesianToPolar(vec2 uv) {
     float r = length(uv);
     float theta = atan(uv.y, uv.x);
     return vec2(r, theta);
 }
 
 vec4 main(float2 fragCoord) {
     float2 uv = fragCoord / resolution - 0.5; // Normalize coordinates
     uv.x *= resolution.x / resolution.y;
     
     float width = 0.02; // Bar width
     
     // Convert to polar coordinates
     vec2 polar = cartesianToPolar(uv);
     float r = polar.x;           // Radius
     float theta = polar.y;       // Angle
     
     float innerMask = step(0.3, r);
    
     // Bar pattern with adjusted width
     float adjustedWidth = width / max(r, 0.001); // Compensate for radial scaling
     float fence = step( 0.5 - adjustedWidth, fract(theta * 10.0));
     
     // Apply the wave mask to the bar
     float barRadius = 0.3;
     float barMask = smoothstep(barRadius+0.01, barRadius, r);
     
     // Combine bar shape and inner mask
     float combinedMask = barMask * fence * innerMask;
    
     vec3 col = vec3(1.0) * combinedMask;
     
     return vec4(col, 1.0);
 }
   """.trimIndent()
@SuppressLint("SuspiciousIndentation")
@Preview
@Composable
fun CircleShader(){


    var startValue by remember { mutableStateOf(20) }
    var percentage by remember { mutableStateOf(0.0f) }
    val shader = remember { RuntimeShader(circularTimerSHADER) }

    var time by remember { mutableStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "")
    time = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    ).value
        Column(Modifier.fillMaxSize().padding(12.dp).background(Color.Black),

            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally

            ) {


             OutlinedTextField(
                 value =  if (startValue !=0 ) startValue.toString() else "",
                 onValueChange = {
                     startValue = it.toIntOrNull() ?:0
                 },

                 label = { Text(("Start Value")) }, modifier = Modifier.width(200.dp)
             )

            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center){

                 Box(Modifier
                     .size(250.dp)
                     .onSizeChanged {
                         shader.setFloatUniform("resolution",it.width.toFloat(),it.height.toFloat())
                     }
                     .graphicsLayer {
                         shader.setFloatUniform("time", time)
                         shader.setFloatUniform("percentage", percentage)
                         renderEffect = RenderEffect.createRuntimeShaderEffect(shader,"image").asComposeRenderEffect()
                     }
                     .background(Color.Red)

                 )

                Text(modifier = Modifier.fillMaxWidth(),
                    text = "20",
                    fontSize = 50.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                    )

            }

        }

}

// AreaLightShader.kt
private val innerGlow = """
   uniform float2 size;
uniform float cornerRadius;
uniform float glowIntensity;
uniform float glowRange;
uniform float4 glowColor;

// 基础的圆角矩形距离计算
float roundedRectDistance(float2 point, float2 size, float radius) {
    float2 q = abs(point) - size + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

// 指数衰减
float exponentialGlow(float distance, float range) {
    return exp(-distance * distance / (2.0 * range * range));
}

// 高斯分布
float gaussianGlow(float distance, float range) {
    float x = distance / range;
    return exp(-(x * x));
}

// 正弦插值
float sineGlow(float distance, float range) {
    float x = clamp(distance / range + 1.0, 0.0, 1.0);
    return cos(x * 3.14159 / 2.0);
}

// 多项式软衰减
float polynomialGlow(float distance, float range) {
    float x = clamp(distance / range + 1.0, 0.0, 1.0);
    return 1.0 - x * x * (3.0 - 2.0 * x);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord - size/2;
    float dist = roundedRectDistance(uv, size/2, cornerRadius);

    // 选择以下任意一种发光效果
    float glow;

    // 1. 指数衰减 - 更柔和的光晕效果
    // glow = exponentialGlow(-dist, glowRange) * glowIntensity;

    // 2. 高斯分布 - 更自然的光晕扩散
    // glow = gaussianGlow(-dist, glowRange) * glowIntensity;

    // 3. 正弦插值 - 更平滑的过渡
    // glow = sineGlow(-dist, glowRange) * glowIntensity;

    // 4. 多项式软衰减 - 类似 smoothstep 但更可控
    glow = polynomialGlow(-dist, glowRange) * glowIntensity;

    // 5. 组合效果 - 混合多种插值方法
    // float glow1 = exponentialGlow(-dist, glowRange);
    // float glow2 = gaussianGlow(-dist, glowRange * 1.5);
    // glow = mix(glow1, glow2, 0.5) * glowIntensity;

    float alpha = dist <= 0.0 ? glow : 0.0;
    return half4(glowColor.rgb, alpha * glowColor.a);
}
"""
@Preview
@Composable
fun InnerGlowEffect() {
    val shader = remember { RuntimeShader(innerGlow) }
    var cornerRadius by remember { mutableStateOf(20f) }
    var glowIntensity by remember { mutableStateOf(0.8f) }
    var glowRange by remember { mutableStateOf(50f) }
    var red by remember { mutableStateOf(0.3f) }
    var green by remember { mutableStateOf(0.8f) }
    var blue by remember { mutableStateOf(0.3f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(500.dp)
                .aspectRatio(1f)
                .onSizeChanged { size ->
                    shader?.let {
                        it.setFloatUniform(
                            "resolution",
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                        it.setFloatUniform(
                            "size",
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                    }
                }
                .graphicsLayer {
                    shader?.let {
                        it.setFloatUniform("cornerRadius", cornerRadius)
                        it.setFloatUniform("glowIntensity", glowIntensity)
                        it.setFloatUniform("glowRange", glowRange)
                        it.setFloatUniform("glowColor", red, green, blue, 1f)

                        renderEffect = RenderEffect
                            .createRuntimeShaderEffect(it, "image")
                            .asComposeRenderEffect()
                    }
                }
                .background(Color.White)
        )
    }


}



