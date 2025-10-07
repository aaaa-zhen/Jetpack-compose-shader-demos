package com.example.myapplication

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

/**
 * 波纹效果着色器常量
 */
private object RippleConstants {
    const val EFFECT_DURATION = 0.7f
    const val FADE_IN_TIME_FACTOR = 0.5f
    const val FADE_OUT_TIME_FACTOR = 0.7f
    const val EFFECT_WIDTH = 1.8f
    const val MAX_TEXEL_OFFSET = 240.0f
    const val ANIMATION_DURATION_MS = 500
}

/**
 * 波纹效果着色器
 * 从底部中心向外扩散的波纹扭曲效果
 */
private val rippleShader = """
    uniform shader iChannel0;
    uniform float2 iResolution;
    uniform float iTime;

    // 常量定义
    const float EffectDuration = ${RippleConstants.EFFECT_DURATION};
    const float EffectFadeInTimeFactor = ${RippleConstants.FADE_IN_TIME_FACTOR};
    const float EffectFadeOutTimeFactor = ${RippleConstants.FADE_OUT_TIME_FACTOR};
    const float EffectWidth = ${RippleConstants.EFFECT_WIDTH};
    const float EffectMaxTexelOffset = ${RippleConstants.MAX_TEXEL_OFFSET};

    half4 main(float2 fragCoord) {
        // 基本参数
        float time = iTime;
        float2 screenCoords = fragCoord.xy;
        float2 screenSize = iResolution.xy;
        
        // 计算以底部中心为原点的偏移量
        float2 centerPoint = float2(screenSize.x / 2.0, screenSize.y);
        float2 offsetFromCenter = (screenCoords - centerPoint) / min(screenSize.x / 2.0, screenSize.y);
        
        // 计算方向和距离
        float2 offsetDirection = normalize(-offsetFromCenter);
        float offsetDistance = length(offsetFromCenter);
        
        // 计算波纹进度
        float progress = mod(time, EffectDuration) / EffectDuration;
        
        // 计算波纹区域
        float halfWidth = EffectWidth / 2.0;
        float lowerEdge = progress - halfWidth;
        float upperEdge = progress + halfWidth;
        
        // 计算平滑边缘
        float lowerStep = smoothstep(lowerEdge, progress, offsetDistance);
        float upperStep = smoothstep(progress, upperEdge, offsetDistance);
        
        // 计算波纹强度
        float lower = 1.0 - lowerStep;
        float upper = upperStep;
        float band = 1.0 - (upper + lower);
        
        // 改进强度计算，让波纹结束时更柔和
        float strength = 1.0 - smoothstep(0.0, 1.0, progress / EffectFadeOutTimeFactor);
        float fadeStrength = smoothstep(0.0, EffectFadeInTimeFactor, progress);
        float distortion = band * strength * fadeStrength;
        
        // 计算纹理偏移
        float2 texelOffset = distortion * offsetDirection * EffectMaxTexelOffset;
        
        // 计算采样坐标
        float2 coords = fragCoord.xy / screenSize;
        float2 texelSize = 1.0 / screenSize;
        float2 offsetCoords = coords + texelSize * texelOffset;
        
        // 限制坐标范围
        float2 halfTexelSize = texelSize / 2.0;
        float2 minCoord = halfTexelSize;
        float2 maxCoord = 1.0 - halfTexelSize;
        float2 clampedCoords = clamp(offsetCoords, minCoord, maxCoord);
        
        // 采样并返回颜色
        float3 color = iChannel0.eval(clampedCoords * iResolution).rgb;
        return half4(color, 1.0);
    }
""".trimIndent()

/**
 * 波纹效果演示预览
 */
@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RippleDemo() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            RippleEffectDemo(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.desktop),
                        contentDescription = "背景图片",
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

/**
 * 波纹效果组件
 * 
 * @param modifier 修饰符
 * @param content 要应用波纹效果的内容
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RippleEffectDemo(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shader = remember { RuntimeShader(rippleShader) }
    val animationTime = remember { Animatable(0f) }
    val isAnimating = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    /**
     * 触发波纹效果
     */
    fun triggerRipple() {
        if (!isAnimating.value) {
            isAnimating.value = true
            coroutineScope.launch {
                animationTime.snapTo(0f)
                animationTime.animateTo(
                    targetValue = RippleConstants.EFFECT_DURATION,
                    animationSpec = tween(
                        durationMillis = RippleConstants.ANIMATION_DURATION_MS,
                        easing = LinearEasing
                    )
                )
                isAnimating.value = false
            }
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    with(density) {
                        shader.setFloatUniform(
                            "iResolution",
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                    }
                }
                .graphicsLayer {
                    shader.setFloatUniform("iTime", animationTime.value)
                    renderEffect = android.graphics.RenderEffect
                        .createRuntimeShaderEffect(shader, "iChannel0")
                        .asComposeRenderEffect()
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { triggerRipple() }
                    )
                }
        ) {
            content()
        }
    }
}