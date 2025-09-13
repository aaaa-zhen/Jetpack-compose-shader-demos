package com.example.myapplication

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsProperties.Text
import androidx.compose.ui.text.input.KeyboardType.Companion.Text
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.lang.reflect.Modifier
val runtimeShader = """
    uniform shader image;
    uniform float2 resolution;
    uniform float lightIntensity;
    uniform float lightRange;
    
    float area_light_antideriv(float2 uv, float i, float h, float t) {
        float lxh = sqrt(uv.x * uv.x + h * h);
        return -i * uv.x * atan((t-uv.y)/lxh) / lxh;
    }
    
    float area_light(float2 uv, float i, float h_bottom, float h_top, float t_start, float t_end) {
        float v =
            + area_light_antideriv(uv, i, h_top, t_end)
            + area_light_antideriv(uv, i, h_bottom, t_start)
            - area_light_antideriv(uv, i, h_bottom, t_end)
            - area_light_antideriv(uv, i, h_top, t_start);
        return max(0.0, v);
    }
    
    float lin_to_srgb(float val) {
        if (val < 0.0031308)
            return val * 12.92;
        return 1.055 * pow(val, 1.0/2.4) - 0.055;
    }
    
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord - 0.5*resolution.xy) / resolution.y;
        uv = uv * 2.0;
        
        float h_top = 0.1;
        float v = area_light(uv, lightIntensity, 0.0, h_top, -lightRange, lightRange);
        v = v + area_light(uv, -0.5, 0.0, h_top, -lightRange, lightRange);
        v = lin_to_srgb(v);
        
        // 修改返回值为橙色光
        float r = v;
        float g = v * 0.6;  // 降低绿色分量
        float b = v * 0.2;  // 显著降低蓝色分量来获得暖色调
        
        return half4(r, g, b, 1.0);
    }
""".trimIndent()

@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ShaderGlow() {
    val shader = remember { RuntimeShader(runtimeShader) }

    // 状态管理
    var lightIntensity by remember { mutableStateOf(0.05f) }
    var lightRange by remember { mutableStateOf(0.5f) }

    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(16.dp)
    ) {
        // 主要的光效果区域
        Box(
            modifier = androidx.compose.ui.Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset(x=200.dp)
                .onSizeChanged { size ->
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
                .graphicsLayer {
                    shader.setFloatUniform("lightIntensity", lightIntensity)
                    shader.setFloatUniform("lightRange", lightRange)
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(shader, "image")
                        .asComposeRenderEffect()
                }
                .background(Color.White)
        )

        // 控制区域
        Column(
            modifier = androidx.compose.ui.Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Text(
                "光强度: ${String.format("%.2f", lightIntensity)}",
                color = Color.White,
                modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = lightIntensity,
                onValueChange = { lightIntensity = it },
                valueRange = 0f..0.2f,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )

            Text(
                "光范围: ${String.format("%.2f", lightRange)}",
                color = Color.White,
                modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp)
            )
            Slider(
                value = lightRange,
                onValueChange = { lightRange = it },
                valueRange = 0.1f..1f,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )
        }
    }
}