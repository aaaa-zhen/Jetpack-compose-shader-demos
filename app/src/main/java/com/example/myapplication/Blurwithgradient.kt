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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val GradientBlur = """
    uniform shader image;
    uniform float2 resolution;
    uniform float time;
    
    float4 main(float2 fragCoord) {
        float2 uv = fragCoord/resolution.xy;
        float aspect = resolution.x/resolution.y;
        float2 adjustedUV = float2(uv.x * aspect - aspect*0.5, uv.y - 0.5);
        
        float2 rectSize = float2(0.15, 0.1);
        float2 d = abs(adjustedUV) - rectSize;
        float dist = length(max(d, float2(0.0, 0.0))) + min(max(d.x, d.y), 0.0);
        float blur = 0.01; // Increased blur value
        
        float rotationAngle = time;
        float2 gradientUV = float2(
            adjustedUV.x * cos(rotationAngle) - adjustedUV.y * sin(rotationAngle),
            adjustedUV.x * sin(rotationAngle) + adjustedUV.y * cos(rotationAngle)
        );
        
        float angle = atan(gradientUV.y/gradientUV.x);
        float percentage = (angle + 3.14159) / (2.0 * 3.14159);
        float segment = percentage * 5.0;
        
        float3 color = float3(0.0);
        float blend = smoothstep(0.0, 0.5, fract(segment)); // Smooth color transition
        
        if(segment < 1.0) {
            color = mix(float3(1,0,0), float3(0,1,0), float3(blend));
        } else if(segment < 2.0) {
            color = mix(float3(0,1,0), float3(0,0,1), float3(blend));
        } else if(segment < 3.0) {
            color = mix(float3(0,0,1), float3(1,1,0), float3(blend));
        } else if(segment < 4.0) {
            color = mix(float3(1,1,0), float3(1,0,1), float3(blend));
        } else {
            color = mix(float3(1,0,1), float3(1,0,0), float3(blend));
        }
        
        float rect = smoothstep(blur, 0.0, dist);
        color *= rect;
        
        return float4(color, 0);
    }
"""
@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GradientBlurEffect() {
    val shader = remember { RuntimeShader(GradientBlur) }
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while(true) {
            time += 0.01f
            delay(16)
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(500.dp)
//                .blur(50.dp)
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

//        Box(Modifier.size(140.dp,120.dp).background(Color.White))
    }
}