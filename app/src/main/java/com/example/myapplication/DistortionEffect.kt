package com.example.myapplication



import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

private val RippleShader = """
uniform shader iChannel0;
uniform float2 iResolution;
uniform float iTime;

// 常量定义
const float EffectDuration = 0.7;
const float EffectFadeInTimeFactor = 0.5;
const float EffectFadeOutTimeFactor = 0.7; // 新增: 控制结束时的平滑过渡
const float EffectWidth = 1.8;
const float EffectMaxTexelOffset = 240.0; // 增大: 从80增加到140，让波纹强度更远

half4 main(float2 fragCoord) {
    // 基本参数
    float time = iTime;
    float2 screenCoords = fragCoord.xy;
    float2 screenSize = iResolution.xy;
    
    // 计算以底部中心为原点的偏移量
    float2 centerPoint = float2(screenSize.x / 2.0, screenSize.y); // 底部中心
    float2 offsetFromCenter = (screenCoords - centerPoint) / min(screenSize.x / 2.0, screenSize.y);
    
    // 计算方向和距离
    float2 offsetDirection = normalize(-offsetFromCenter);
    float offsetDistance = length(offsetFromCenter);
    
    // 计算波纹进度 - 使用mod函数
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
    
    // 修改: 改进强度计算，让波纹结束时更柔和
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
                modifier = Modifier.fillMaxSize(),
            ) {
                // 这里放入你想要应用波纹效果的内容
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(id = R.drawable.desktop),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RippleEffectDemo(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shader = remember { RuntimeShader(RippleShader) }
    val animationTime = remember { Animatable(0f) }
    val isAnimating = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 处理点击事件触发波纹
    fun triggerRipple() {
        if (!isAnimating.value) {
            isAnimating.value = true
            coroutineScope.launch {
                animationTime.snapTo(0f)
                animationTime.animateTo(
                    targetValue = 0.5f, // 设置为EffectDuration的值，正好播放一次
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 500, // 500ms = 0.5秒
                        easing = androidx.compose.animation.core.LinearEasing
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
                    // 考虑设备密度
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
//                .clickable { triggerRipple() }
                .pointerInput(Unit){
                   detectTapGestures(
                       onTap = {
                           triggerRipple()
                       }
                   )
                }
        ) {
            content()
        }
    }
}