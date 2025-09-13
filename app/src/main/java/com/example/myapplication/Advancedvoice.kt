package com.example.myapplication

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


//val runtimeShaderDemo ="""
//
//// All rights reserved. Copyright © 2024 Lumiey.
//uniform shader image;
//uniform float2 resolution;
//uniform float time;
//uniform float speakingState; // 0.0 for idle, 1.0 for speaking
//
//const float PI = 3.1419265358;
//
//// Noise function
//float2 hash(float2 p) {
//    p = float2(dot(p,float2(127.1,311.7)), dot(p,float2(269.5,183.3)));
//    return -1.0 + 2.0 * fract(sin(p)*43758.5453123);
//}
//
//float noise(float2 p) {
//    float2 i = floor(p);
//    float2 f = fract(p);
//    float2 u = f*f*(3.0-2.0*f);
//    return mix(mix(dot(hash(i + float2(0.0,0.0)), f - float2(0.0,0.0)),
//                   dot(hash(i + float2(1.0,0.0)), f - float2(1.0,0.0)), u.x),
//               mix(dot(hash(i + float2(0.0,1.0)), f - float2(0.0,1.0)),
//                   dot(hash(i + float2(1.0,1.0)), f - float2(1.0,1.0)), u.x), u.y);
//}
//
//// Rotation matrix
//float2x2 rot(float a) {
//    float s = sin(a);
//    float c = cos(a);
//    return float2x2(c, -s, s, c);
//}
//
//float2 get_uv(float2 uv) {
//    float t = time * mix(1.0, 2.5, speakingState); // Dynamic speed based on state
//
//    // Dynamic rotation based on noise
//    float degree = noise(float2(t * 0.1, uv.x * uv.y));
//    float rotationAngle = (degree - 0.25) * 720.0 + 180.0;
//    float2x2 rotation = rot(rotationAngle * PI / 180.0);
//    uv = float2(
//        rotation[0][0] * uv.x + rotation[0][1] * uv.y,
//        rotation[1][0] * uv.x + rotation[1][1] * uv.y
//    );
//
//    // Wave distortion with state-based parameters
//    float idleFreq = 1.0;
//    float speakingFreq = 2.0;
//    float idleAmp = 0.02;
//    float speakingAmp = 0.01;
//
//    float frequency = mix(idleFreq, speakingFreq, speakingState);
//    float amplitude = mix(idleAmp, speakingAmp, speakingState);
//
//    // Apply wave distortion
//    uv.x += sin(uv.y * frequency + t) * amplitude;
//    uv.y += sin(uv.x * frequency * 0.5 + t) * (amplitude * 0.5);
//
//    // Additional non-linear distortion
//    float a = 1.0 * uv.y - sin(uv.x * 2.0 + uv.y - t) * 0.5;
//    a = smoothstep(cos(a) * 0.3, sin(a) * 0.3 + 1.0, cos(a - 2.0 * uv.y) - sin(a - 1.5 * uv.x));
//    uv = cos(a) * uv + sin(a) * float2(-uv.y, uv.x);
//
//    return uv;
//}
//
//const float3 purple = float3(0.68, 0.1, 0.9);
//const float3 blue = float3(0.200,0.031,0.447);
//const float3 orange = float3(1, 0.68, 0.4);
//const float3 red = float3(0.98, 0.38, 0.35);
//
//float4 get_col(float2 uv) {
//    uv = get_uv(uv) * 0.5 + 0.5;
//    float3 col = mix(purple, orange, uv.x);
//    col = mix(col, blue, uv.y);
//    col *= col + 0.5 * sqrt(col);
//    return float4(col, dot(col, float3(0.3, 0.6, 0.1)));
//}
//
//half4 main(float2 fragCoord) {
//    float2 uv = (fragCoord * 2.0 - resolution.xy) / (resolution.x + resolution.y) * 2.0;
//    float4 col = get_col(uv);
//    return half4(col.rgb, 1.0);
//}
//
// """.trimIndent()
//
//
//
//@Preview
//@Composable
//fun ShaderEffect() {
//    val shader = remember { RuntimeShader(runtimeShaderDemo) }
//    var time by remember { mutableStateOf(0f) }
//    var isSpeaking by remember { mutableStateOf(false) }
//
//    // 添加状态动画
//    val speakingState by animateFloatAsState(
//        targetValue = if (isSpeaking) 1f else 0f,
//        animationSpec = tween(
//            durationMillis = 500,
//            easing = FastOutSlowInEasing
//        ),
//        label = "speakingState"
//    )
//
//    LaunchedEffect(Unit) {
//        while (true) {
//            withFrameNanos { frameTime ->
//                time = (frameTime / 1_000_000_000f)
//            }
//        }
//    }
//
//    Box(
//        Modifier
//            .fillMaxSize()
//            .background(Color.Black),
//        contentAlignment = Alignment.Center
//    ) {
//        Box(
//            Modifier
//                .fillMaxSize()
//                .scale(1.2f)
//                .clipToBounds()
//                .onSizeChanged { size ->
//                    shader.setFloatUniform(
//                        "resolution",
//                        size.width.toFloat(),
//                        size.height.toFloat()
//                    )
//                }
//                .graphicsLayer {
//                    renderEffect = RenderEffect
//                        .createRuntimeShaderEffect(
//                            shader.apply {
//                                setFloatUniform("time", time)
//                                setFloatUniform("speakingState", speakingState)  // 使用动画状态值
//                            },
//                            "image"
//                        )
//                        .asComposeRenderEffect()
//                }
//                .background(Color.White)
//        )
//
//        Button(
//            onClick = { isSpeaking = !isSpeaking },
//            colors = ButtonDefaults.buttonColors(
//                containerColor = if (isSpeaking) Color(0xFF6200EE) else Color(0xFF03DAC5)
//            ),
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 32.dp)
//        ) {
//            Text(
//                if (isSpeaking) "Stop Speaking" else "Start Speaking",
//                color = Color.White
//            )
//        }
//    }
//}


val runtimeShaderDemo ="""

// All rights reserved. Copyright © 2024 Lumiey.
uniform shader image;
uniform float2 resolution;
uniform float time;

const float PI = 3.1419265358;

// Noise function
float2 hash(float2 p) {
    p = float2(dot(p,float2(127.1,311.7)), dot(p,float2(269.5,183.3)));
    return -1.0 + 2.0 * fract(sin(p)*43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);

    float2 u = f*f*(3.0-2.0*f);

    return mix(mix(dot(hash(i + float2(0.0,0.0)), f - float2(0.0,0.0)),
                   dot(hash(i + float2(1.0,0.0)), f - float2(1.0,0.0)), u.x),
               mix(dot(hash(i + float2(0.0,1.0)), f - float2(0.0,1.0)),
                   dot(hash(i + float2(1.0,1.0)), f - float2(1.0,1.0)), u.x), u.y);
}

// Rotation matrix
float2x2 rot(float a) {
    float s = sin(a);
    float c = cos(a);
    return float2x2(c, -s, s, c);
}

float2 get_uv(float2 uv) {
    float t = time * 1.5;

    // 1. Rotation based on noise
    float noiseVal = noise(float2(t * 0.2, length(uv)));
    float rotationAngle = (noiseVal * 2.0 - 1.0) * PI;
    float2x2 rotation = rot(rotationAngle);
    uv = float2(
        rotation[0][0] * uv.x + rotation[0][1] * uv.y,
        rotation[1][0] * uv.x + rotation[1][1] * uv.y
    );

    // 2. Wave distortion
    float frequency = 1.0;
    float amplitude = 0.01;
    float speed = t * 1.0;

    uv.x += sin(uv.y * frequency + speed) * amplitude;
    uv.y += sin(uv.x * frequency * 1.5 + speed) * (amplitude * 0.5);

    float a = 1.0 * uv.y - sin(uv.x * 2.0 + uv.y - t) * 0.5;
    a = smoothstep(cos(a) * 0.3, sin(a) * 0.3 + 1.0, cos(a - 2.0 * uv.y) - sin(a - 1.5 * uv.x));
    uv = cos(a) * uv + sin(a) * float2(-uv.y, uv.x);

    return uv;
}

const float3 purple = float3(0.68, 0.1, 0.9);
const float3 blue = float3(0.200,0.031,0.447);
const float3 orange = float3(1, 0.68, 0.4);
const float3 red = float3(0.98, 0.38, 0.35);

float4 get_col(float2 uv) {
    uv = get_uv(uv) * 0.5 + 0.5;
    float3 col = mix(purple, orange, uv.x);
    col = mix(col, blue, uv.y);
    col *= col + 0.5 * sqrt(col);
    return float4(col, dot(col, float3(0.3, 0.6, 0.1)));
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord * 2.0 - resolution.xy) / (resolution.x + resolution.y) * 2.0;
    float4 col = get_col(uv);
    return half4(col.rgb, 1.0);
}

 """.trimIndent()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview
@Composable
fun ShaderEffect() {
    val shader = remember { RuntimeShader(runtimeShaderDemo) }
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime ->
                time = (frameTime / 1_000_000_000f)
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(1.2f)
                .clipToBounds()
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
