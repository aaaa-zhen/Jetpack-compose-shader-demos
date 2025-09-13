import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.annotation.RequiresApi
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import com.example.myapplication.R
import org.intellij.lang.annotations.Language

// 多彩混合效果着色器
@Language("AGSL")
private val MultiColorShader = """
uniform shader composable;

// ========== 原有参数 ==========
uniform float3 colorSet1;     // 第一个颜色 (顶部)
uniform float3 colorSet2;     // 第二个颜色
uniform float3 colorSet3;     // 第三个颜色 (中部)
uniform float3 colorSet4;     // 第四个颜色
uniform float3 colorSet5;     // 第五个颜色 (底部)
uniform float alphaValue;     // 混合强度（透明度）
uniform float displacement;   // 色差位移量

// ========== 圆形蒙版参数 ==========
uniform float2 circleCenter;  // 圆心坐标 (px)
uniform float circleRadius;   // 圆半径
uniform float feather;        // 边缘模糊范围（羽化宽度）
uniform float2 circleOffset;  // 蒙版位置偏移量 (px)

// ========== 模糊和发光参数 ==========
uniform float blurRadius;     // 模糊半径
uniform float blurIntensity;  // 模糊强度
uniform float glowIntensity;  // 发光强度
uniform float3 glowColor;     // 发光颜色

// ========== 渐变平滑参数 ==========
uniform float gradientSmoothing;  // 渐变平滑强度 (0.0-1.0)
uniform float noiseAmount;        // 噪声强度 (0.0-1.0)

// 伪随机数生成函数
float random(float2 st) {
    return fract(sin(dot(st.xy, float2(12.9898, 78.233))) * 43758.5453123);
}

// 2D噪声函数
float noise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);

    // 四角采样点
    float a = random(i);
    float b = random(i + float2(1.0, 0.0));
    float c = random(i + float2(0.0, 1.0));
    float d = random(i + float2(1.0, 1.0));

    // 平滑插值
    float2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

// 平滑步进函数，用于颜色停止点之间的平滑过渡
float smoothStep(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// 叠加混合函数 - 能在黑色和白色背景上都有良好效果
float3 overlay(float3 base, float3 blend) {
    float3 result;
    result.r = (base.r < 0.5) ? (2.0 * base.r * blend.r) : (1.0 - 2.0 * (1.0 - base.r) * (1.0 - blend.r));
    result.g = (base.g < 0.5) ? (2.0 * base.g * blend.g) : (1.0 - 2.0 * (1.0 - base.g) * (1.0 - blend.g));
    result.b = (base.b < 0.5) ? (2.0 * base.b * blend.b) : (1.0 - 2.0 * (1.0 - base.b) * (1.0 - blend.b));
    return result;
}

// 高斯模糊采样函数 - 扩展为13点采样
half4 gaussianBlur(float2 center, float radius) {
    half4 blurredColor = half4(0.0);

    // 中心点 - 权重 0.2
    blurredColor += composable.eval(center) * 0.2;

    // 八方向采样
    // 主方向 - 权重 0.1 每个
    blurredColor += composable.eval(center + float2(0.0, -radius)) * 0.1;
    blurredColor += composable.eval(center + float2(radius, 0.0)) * 0.1;
    blurredColor += composable.eval(center + float2(0.0, radius)) * 0.1;
    blurredColor += composable.eval(center + float2(-radius, 0.0)) * 0.1;

    // 对角方向 - 权重 0.05 每个
    blurredColor += composable.eval(center + float2(-radius, -radius)) * 0.05;
    blurredColor += composable.eval(center + float2(radius, -radius)) * 0.05;
    blurredColor += composable.eval(center + float2(radius, radius)) * 0.05;
    blurredColor += composable.eval(center + float2(-radius, radius)) * 0.05;

    // 扩展采样 - 第二圈，权重 0.025 每个
    blurredColor += composable.eval(center + float2(0.0, -radius * 1.5)) * 0.025;
    blurredColor += composable.eval(center + float2(radius * 1.5, 0.0)) * 0.025;
    blurredColor += composable.eval(center + float2(0.0, radius * 1.5)) * 0.025;
    blurredColor += composable.eval(center + float2(-radius * 1.5, 0.0)) * 0.025;

    return blurredColor;
}

// 获取多色线性渐变
float3 getLinearGradient(float y, float height) {
    // 归一化Y坐标 (0.0在顶部，1.0在底部)
    float yNorm = y / height;

    // 添加细微噪声以打破可能的条纹
    float noiseVal = noise(float2(0.5, yNorm) * 15.0) * noiseAmount * 0.02;
    yNorm = clamp(yNorm + noiseVal, 0.0, 1.0);

    // 根据图示设定颜色停止点
    // 颜色位置 (颜色1：0%, 颜色2：23%, 颜色3：42%, 颜色4：66%, 颜色5：100%)
    float stop1 = 0.0;    // 深蓝色 (7163FD)
    float stop2 = 0.23;   // 蓝色 (2D26F8)
    float stop3 = 0.42;   // 紫色 (7C2BFA)
    float stop4 = 0.66;   // 粉色 (FC44B5)
    float stop5 = 1.0;    // 黄色 (FFBF10)

    // 应用平滑度因子
    float smoothFactor = gradientSmoothing * 0.05;

    // 确定在哪个颜色区间，并混合颜色
    float3 color;

    if (yNorm < stop2) {
        // 区间1-2（深蓝到蓝）
        float t = smoothStep(stop1, stop2, yNorm);
        color = mix(colorSet1, colorSet2, t);
    } else if (yNorm < stop3) {
        // 区间2-3（蓝到紫）
        float t = smoothStep(stop2, stop3, yNorm);
        color = mix(colorSet2, colorSet3, t);
    } else if (yNorm < stop4) {
        // 区间3-4（紫到粉）
        float t = smoothStep(stop3, stop4, yNorm);
        color = mix(colorSet3, colorSet4, t);
    } else {
        // 区间4-5（粉到黄）
        float t = smoothStep(stop4, stop5, yNorm);
        color = mix(colorSet4, colorSet5, t);
    }

    return color;
}

half4 main(float2 fragCoord) {
    // ------------- 原图颜色 -------------
    half4 originalColor = composable.eval(fragCoord);

    // ------------- 特效颜色 -------------
    // 1) Chromatic Aberration（色差） - 上下错位
    half3 chromaColor;
    chromaColor.r = composable.eval(float2(fragCoord.x, fragCoord.y - displacement)).r;
    chromaColor.g = composable.eval(fragCoord).g;
    chromaColor.b = composable.eval(float2(fragCoord.x, fragCoord.y + displacement)).b;

    // 2) 五色线性渐变
    float3 blendedColor = getLinearGradient(fragCoord.y, 1920.0); // 假设高度为1920像素，根据实际情况调整

    // 3) 使用叠加混合代替加法混合 - 适用于黑白背景
    float3 overlayColor = overlay(chromaColor, blendedColor);
    float3 effectColor = mix(chromaColor, overlayColor, alphaValue);

    // 确保混合后的颜色不会超过1.0
    effectColor = min(effectColor, 1.0);

    // ------------- 圆形蒙版计算 -------------
    // 应用蒙版偏移量
    float2 adjustedFragCoord = fragCoord - circleOffset;

    // 与圆心的距离
    float dist = distance(adjustedFragCoord, circleCenter);

    // 使用 smoothstep 做平滑过渡
    float mask = smoothstep(circleRadius - feather, circleRadius, dist);

    // ------------- 双重模糊 -------------
    // 对特效颜色进行模糊处理 - 使用双阶段模糊以获得更平滑的效果
    half4 blurredEffectColor = gaussianBlur(fragCoord, blurRadius);
    // 二次模糊，将已模糊的结果进一步模糊
    half4 doubleBlurredEffectColor = gaussianBlur(fragCoord, blurRadius * 2.0);

    // 结合两种模糊结果
    float3 finalEffectColor = mix(
        effectColor, 
        mix(blurredEffectColor.rgb, doubleBlurredEffectColor.rgb, 0.5), 
        blurIntensity
    );

    // ------------- 添加发光效果（适度亮度）-------------
    // 创建发光效果 - 使用模糊结果作为发光基础，控制亮度
    float3 glowEffect = doubleBlurredEffectColor.rgb * glowColor * 0.55;

    // 应用发光强度并添加到效果上（适度亮度）
    float adjustedGlowIntensity = glowIntensity * 0.75;
    finalEffectColor = finalEffectColor + glowEffect * adjustedGlowIntensity;

    // 确保颜色值不超过1.0
    finalEffectColor = min(finalEffectColor, 1.0);

    // ------------- 根据 mask 进行混合 -------------
    // mask=0 → 特效颜色；mask=1 → 原图颜色
    half3 finalColor = mix(finalEffectColor, originalColor.rgb, mask);

    return half4(finalColor, 1.0);
}
"""

// 波纹特效着色器
private val RippleShader = """
uniform shader iChannel0;
uniform float2 iResolution;
uniform float iTime;

// 常量定义
const float EffectDuration = 0.7;
const float EffectFadeInTimeFactor = 0.5;
const float EffectFadeOutTimeFactor = 0.9; // 控制结束时的平滑过渡
const float EffectWidth = 2.0;
const float EffectMaxTexelOffset = 220.0;

// Jelly效果参数
const float JellyStrength = 3.;        // 果冻变形强度
const float JellyOscillations = 0.8;    // 振荡次数
const float JellyDamping = 0.2;         // 阻尼系数
const float JellyScale = 2.;           // 效果范围缩放

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

    // Jelly效果: 添加随时间衰减的振荡
    float normalizedDistance = offsetDistance / JellyScale;
    float oscillation = sin((normalizedDistance - progress) * JellyOscillations * 6.28) * 
                        exp(-normalizedDistance * JellyDamping - progress * 3.0);

    // 应用Jelly效果
    float2 jellyOffset = offsetDirection * oscillation * JellyStrength * fadeStrength * (1.0 - progress);

    // 合并Ripple和Jelly效果
    float2 texelOffset = (distortion * offsetDirection * EffectMaxTexelOffset) + (jellyOffset * EffectMaxTexelOffset);

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
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview
@Composable
fun CombinedEffectsDemo() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // 主内容区域
            CombinedEffects(
                modifier = Modifier.fillMaxSize(),
                imageRes = R.drawable.test // 修改为你的图片资源
            )


        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CombinedEffects(
    modifier: Modifier = Modifier,
    imageRes: Int
) {
    BoxWithConstraints(modifier = modifier) {
        val layoutWidth = constraints.maxWidth.toFloat()
        val layoutHeight = constraints.maxHeight.toFloat()
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current

        // ===== 多彩混合效果状态 =====
        val multiColorShader = remember { RuntimeShader(MultiColorShader) }
        
        // 圆心初始位置
        val bottomCenter = Offset(
            x = layoutWidth / 2f,
            y = layoutHeight
        )

        // 颜色参数 - 根据图片中的颜色设置
        val colorSet1 = Color(0x71, 0x63, 0xFD, 0xFF) // 深蓝色 #7F63FD (顶部)
        val colorSet2 = Color(0x2D, 0x26, 0xF8, 0xFF) // 蓝色 #2D26F8
        val colorSet3 = Color(0x7C, 0x2B, 0xFA, 0xFF) // 紫色 #7C2BFA
        val colorSet4 = Color(0xFC, 0x44, 0xB5, 0xFF) // 粉色 #FC44B5
        val colorSet5 = Color(0xFF, 0xBF, 0x10, 0xFF) // 黄色 #FFBF10 (底部)

        val AnimationCurve: Easing = CubicBezierEasing(0.67f, 0.0f, 0.33f, 1.0f)
        val AnimationCurveAlpha: Easing = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)

        // 多彩混合效果动画状态
        var alphaValue by remember { mutableStateOf(1f) }
        val animatedAlphaValue by animateFloatAsState(
            targetValue = alphaValue,
            animationSpec = tween(1000, easing = AnimationCurveAlpha, delayMillis = 600)
        )

        var displacementValueDefault by remember { mutableStateOf(10f) }
        val animatedDisplacementValue by animateFloatAsState(
            targetValue = displacementValueDefault,
            animationSpec = tween(600, easing = AnimationCurveAlpha, delayMillis = 600)
        )

        var circleRadius by remember { mutableStateOf(0f) }
        val animatedCircleRadius by animateFloatAsState(
            targetValue = circleRadius,
            animationSpec = tween(817, easing = AnimationCurve)
        )

        var feather by remember { mutableStateOf(500f) }
        val animatedFeather by animateFloatAsState(
            targetValue = feather,
            animationSpec = tween(817, easing = AnimationCurve)
        )

        var circleOffset by remember { mutableStateOf(Offset(0f, 0f)) }
        val animatedCircleOffset by animateOffsetAsState(
            targetValue = circleOffset,
            animationSpec = tween(817, easing = AnimationCurve)
        )

        var glowIntensity by remember { mutableStateOf(3.5f) }
        val animatedGlowIntensity by animateFloatAsState(
            targetValue = glowIntensity,
            animationSpec = tween(800, easing = FastOutSlowInEasing, delayMillis = 200)
        )

        // 渐变平滑和噪声参数
        val gradientSmoothing = 0.3f // 渐变平滑度 (0.0-1.0)
        val noiseAmount = 0.1f       // 噪声强度 (0.0-1.0)
        
        // 模糊和发光参数
        val blurRadius = 2.0f        // 模糊半径
        val blurIntensity = 0.2f     // 模糊强度 (0.0-1.0)
        val glowColor = Color(0.851f, 0.243f, 0.780f) // 发光颜色

        // ===== 波纹效果状态 =====
        val rippleShader = remember { RuntimeShader(RippleShader) }
        val animationTime = remember { Animatable(0f) }
        val isRippleAnimating = remember { mutableStateOf(false) }
        
        // 保存当前尺寸
        var currentSize by remember { mutableStateOf(IntSize(0, 0)) }

        // 触发多彩混合效果动画
        fun triggerColorBlendEffect() {
            coroutineScope.launch {
                circleRadius = 3500f
                alphaValue = 0f
                glowIntensity = 0f
                displacementValueDefault = 0f
            }
        }

        // 触发波纹效果动画
        fun triggerRippleEffect() {
            if (!isRippleAnimating.value) {
                isRippleAnimating.value = true
                coroutineScope.launch {
                    animationTime.snapTo(0f)
                    animationTime.animateTo(
                        targetValue = 0.7f, // EffectDuration的值
                        animationSpec = tween(
                            durationMillis = 700, // 700ms = 0.7秒
                            easing = LinearEasing
                        )
                    )
                    isRippleAnimating.value = false
                }
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                currentSize = size
                with(density) {
                    rippleShader.setFloatUniform(
                        "iResolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        // 同时触发两个效果
                        triggerColorBlendEffect()
                        triggerRippleEffect()
                    }
                )
            }
        ) {
            // 先应用多彩混合效果，再应用波纹效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 波纹效果shader设置
                        rippleShader.setFloatUniform("iTime", animationTime.value)

                        // 多彩混合shader设置
                        multiColorShader.setFloatUniform("colorSet1", colorSet1.red, colorSet1.green, colorSet1.blue)
                        multiColorShader.setFloatUniform("colorSet2", colorSet2.red, colorSet2.green, colorSet2.blue)
                        multiColorShader.setFloatUniform("colorSet3", colorSet3.red, colorSet3.green, colorSet3.blue)
                        multiColorShader.setFloatUniform("colorSet4", colorSet4.red, colorSet4.green, colorSet4.blue)
                        multiColorShader.setFloatUniform("colorSet5", colorSet5.red, colorSet5.green, colorSet5.blue)

                        // 动画参数
                        multiColorShader.setFloatUniform("alphaValue", animatedAlphaValue)
                        multiColorShader.setFloatUniform("displacement", animatedDisplacementValue)

                        // 蒙版参数
                        multiColorShader.setFloatUniform("circleCenter", bottomCenter.x, bottomCenter.y)
                        multiColorShader.setFloatUniform("circleRadius", animatedCircleRadius)
                        multiColorShader.setFloatUniform("feather", animatedFeather)
                        multiColorShader.setFloatUniform("circleOffset", animatedCircleOffset.x, animatedCircleOffset.y)

                        // 渐变平滑参数
                        multiColorShader.setFloatUniform("gradientSmoothing", gradientSmoothing)
                        multiColorShader.setFloatUniform("noiseAmount", noiseAmount)

                        // 模糊和发光参数
                        multiColorShader.setFloatUniform("blurRadius", blurRadius)
                        multiColorShader.setFloatUniform("blurIntensity", blurIntensity)
                        multiColorShader.setFloatUniform("glowIntensity", animatedGlowIntensity)
                        multiColorShader.setFloatUniform("glowColor", glowColor.red, glowColor.green, glowColor.blue)

                        // 首先应用多彩混合效果，然后应用波纹效果
                        val multiColorEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(multiColorShader, "composable")
                        
                        val rippleEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(rippleShader, "iChannel0")
                            
                        // 链接两个效果
                        val chainedEffect = android.graphics.RenderEffect.createChainEffect(
                            rippleEffect,
                            multiColorEffect
                        )

                        renderEffect = chainedEffect.asComposeRenderEffect()
                    }
            ) {
                // 基础图片内容
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
