
import android.R
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.android.awaitFrame
import kotlin.math.min
import kotlin.math.roundToInt

// —— 建议的默认：更通用（正方形≈30%圆角），仍可在 Preview 用滑杆调 —— //
private const val DEFAULT_CORNER_RADIUS_NORM = 0.33f

// ======================= AGSL (完整修正版) =======================
private const val LiquidGlassPointRefPressAGSL = """
uniform shader image;
uniform float2 resolution;
uniform float  time;

// 填充 & 轮廓
uniform float3 bgColor;
uniform float  bgAlpha;
uniform float  edgeWidthPx;
uniform float  baseEdgePx;
uniform float  baseEdgeStrength;
uniform float  cornerRadius;   // 以“高度”为基准的归一半径

// 贴边/裁剪
uniform float  clipToShape;    // 1=仅形状内, 0=不过滤
uniform float  edgePadPx;      // 可为负，-1 更贴边

// 旋转高光线
uniform float  twoRimPower;    // 2.5~4.0

// 线条外观
uniform float  lineGain;       // 1.2~2.0
uniform float  lineSigmaPx;    // 1.4~2.2 常用

// 按下光
uniform float2 pressPos;       // 像素坐标
uniform float  pressAlpha;     // 0..1

// 方向混合：0=全局旋转，1=锁定按下方向
uniform float  lineDirMix;

// ---------- 常量 ----------
const float ROT_SPEED    = 0.35;
const float SPEC_POWER   = 32.0;

const float CORE_SIGMA_PX = 65.0;
const float HALO_SIGMA_PX = 480.0;
const float CORE_GAIN     = 1.2;
const float HALO_GAIN     = 0.55;
const float RANGE_PX      = 520.0;


// ========== SDF（固定外框，不随圆角变大） ==========
float sdRoundedRect(float2 p, float2 b, float r){
    // 关键修正：用 (b - r) 预扣半尺寸，r 只改变角的圆滑，不改变外框尺寸
    float2 q = abs(p) - (b - r);
    return length(max(q, 0.0)) - r;
}

float shapeSDF(float2 p){
    // 坐标以“高度”归一；用纵横比构建半宽高，避免非正方形拉伸
    float aspect = resolution.x / resolution.y;
    float2 HALF_SIZE_DYN = float2(0.5 * aspect, 0.5);
    float rClamp = clamp(cornerRadius, 0.0, min(HALF_SIZE_DYN.x, HALF_SIZE_DYN.y));
    return sdRoundedRect(p, HALF_SIZE_DYN, rClamp);
}

float2 sdfNormal(float2 p){
    float e = 2.0 / resolution.y;
    float dx = shapeSDF(p + float2(e,0)) - shapeSDF(p - float2(e,0));
    float dy = shapeSDF(p + float2(0,e)) - shapeSDF(p - float2(0,e));
    return normalize(float2(dx, dy));
}

float gaussianLine(float d, float sigmaPx){
    float px = 1.0 / resolution.y;
    float x = d / (sigmaPx*px + 1e-6);
    return exp(-x*x);
}

float gRad(float rPx, float sigmaPx){
    float s = max(sigmaPx, 1e-6);
    float t = rPx / s;
    return exp(-t*t);
}

// ========== 主程序 ==========
half4 main(float2 fragCoord){
    // uv：以“高度”归一，中心为(0,0)
    float2 uv = (fragCoord - 0.5*resolution) / resolution.y;

    float d   = shapeSDF(uv);
    float2 n2 = sdfNormal(uv);

    // 统一的 AA 与贴边（像素单位）
    float px = 1.0 / resolution.y;
    float aa = 1.5 * px;
    float dShift = d - edgePadPx * px;
    float insideSoft = 1.0 - smoothstep(0.0, aa, dShift);
    float insideHard = step(dShift, 0.0);

    // 背景 + 内部填充（保持 UI 颜色原样，不做全局 tonemap）
    half4 base = image.eval(fragCoord);
    float3 rgb = mix(base.rgb, bgColor, bgAlpha * insideSoft);

    // 常亮轮廓（soft mask，避免硬锯齿）
    float baseBand = 1.0 - smoothstep(0.0, baseEdgePx*px, abs(d));
    rgb += float3(0.32) * baseBand * baseEdgeStrength * insideSoft;

    // ===== 旋转/按压方向高光 =====
    float rotAng = ROT_SPEED * time;
    float2 Lrot   = float2(cos(rotAng), sin(rotAng));

    float2 lpUV   = (pressPos - 0.5*resolution) / resolution.y;
    float2 Lpress = length(lpUV) > 1e-5 ? normalize(lpUV) : Lrot;

    float mix01 = clamp(lineDirMix, 0.0, 1.0);
    float2 Ld   = normalize(mix(Lrot, Lpress, mix01));

    float ndl   = dot(n2, Ld);
    float spec1 = pow(max(ndl, 0.0), SPEC_POWER);
    float rim2  = pow(clamp(1.0 - abs(ndl), 0.0, 1.0), twoRimPower);
    float lineTerm = mix(rim2, spec1, mix01);

    float sigma = max(lineSigmaPx, 1.2);
    float band  = gaussianLine(d, sigma);
    float edgeMask = 1.0 - smoothstep(edgeWidthPx*px, (edgeWidthPx+2.0)*px, abs(d));

    // —— 光效累加（只对“光”做处理；保持底色忠实）——
    float3 add = float3(0.0);
    add += float3(1.0) * (lineTerm * band * edgeMask * max(lineGain, 1.0)) * insideSoft;

    // ===== 点光（按压光）=====
    float rPx = length(uv - lpUV)/px;
    float core = CORE_GAIN * gRad(rPx, CORE_SIGMA_PX);
    float halo = HALO_GAIN * gRad(rPx, HALO_SIGMA_PX);
    float range = 1.0 - smoothstep(0.0, RANGE_PX, rPx);   // 修正后的半径函数
    float diffuse = (core + halo) * range;

    float clipMask = mix(1.0, insideHard, clamp(clipToShape, 0.0, 1.0));
    add += float3(diffuse) * pressAlpha * clipMask;

    // 最终合成（如需更柔和，可改为：rgb + add/(1.0 + add)）
    float3 col = rgb + add;

    float outA = max(base.a, bgAlpha * insideSoft);
    return half4(clamp(col, 0.0, 1.0), outA);
}

"""

// ======================= Composable 封装 =======================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiquidGlassPointRefPressBox(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    cornerRadius: Dp? = null,

    // 胶囊内部颜色与透明度（保持忠实 sRGB 呈现）
    bgColor: Color = Color(0xFF2F6FEE),
    bgAlpha: Float = 1f,

    // 边缘参数
    edgeWidthPx: Float = 3f,
    baseEdgePx: Float = 3f,
    baseEdgeStrength: Float = 0.55f,

    // 贴边/裁剪
    clipToShape: Boolean = true,
    edgePadPx: Float = -1f,

    // 高光参数
    twoRimPower: Float = 1.0f,

    // 线亮度 & 线宽（像素）
    lineGain: Float = 1.6f,
    lineSigmaPx: Float = 2.2f,

    content: @Composable BoxScope.() -> Unit = {}
) {
    val shader = remember { RuntimeShader(LiquidGlassPointRefPressAGSL) }
    val density = LocalDensity.current

    val cornerRadiusPx = cornerRadius?.let { with(density) { it.toPx() } }

    var renderSize by remember { mutableStateOf(IntSize.Zero) }

    // 时钟
    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            time = awaitFrame() * 1e-9f
        }
    }

    // 触控 & 光效
    var pressing by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    var lastPos by remember { mutableStateOf(Offset.Zero) }

    // 点光透明度：按下淡入 / 抬手淡出
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = if (pressing)
            tween(540, easing = FastOutSlowInEasing)
        else
            tween(560, easing = FastOutSlowInEasing),
        label = "pressAlpha"
    )

    // 线方向混合（0=旋转, 1=按压），抬手时 400ms 回归
    val lineDirMix by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = if (pressing)
            tween(durationMillis = 100, easing = FastOutSlowInEasing)
        else
            tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lineDirMix"
    )

    // 缩放动效（按下放大，抬手回弹）
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.1f else 1f,
        animationSpec =
        if (isPressed)
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        else
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pressScale",
        visibilityThreshold = 0.000001f
    )

    val posForShader = if (pressing) touchPos else lastPos

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    pressing = true
                    isPressed = true
                    touchPos = d.position
                    lastPos = touchPos

                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed) {
                                touchPos = c.position
                                lastPos = touchPos
                                // 如果需要阻止父级手势，取消注释：
                                // c.consume()
                            }
                        }
                        if (ev.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }
                    pressing = false
                    isPressed = false
                }
            }
            .onSizeChanged { sz -> renderSize = sz }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale

                shader.setFloatUniform("time", time)

                val width = renderSize.width.takeIf { it > 0 } ?: 1
                val height = renderSize.height.takeIf { it > 0 } ?: 1

                shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())

                // —— cornerRadius 归一化（以高度为基准），并限制最大半径 —— //
                val maxRadiusPx = 0.5f * min(width, height)
                val appliedCornerPx = when {
                    cornerRadiusPx != null && cornerRadiusPx.isFinite() ->
                        cornerRadiusPx.coerceAtMost(maxRadiusPx)
                    else ->
                        DEFAULT_CORNER_RADIUS_NORM * maxRadiusPx
                }
                val normalizedCorner = appliedCornerPx / height
                shader.setFloatUniform("cornerRadius", normalizedCorner)

                // 填充 & 轮廓
                shader.setFloatUniform("bgColor", bgColor.red, bgColor.green, bgColor.blue)
                shader.setFloatUniform("bgAlpha", bgAlpha)
                shader.setFloatUniform("edgeWidthPx", edgeWidthPx)
                shader.setFloatUniform("baseEdgePx", baseEdgePx)
                shader.setFloatUniform("baseEdgeStrength", baseEdgeStrength)

                // 贴边/裁剪
                shader.setFloatUniform("clipToShape", if (clipToShape) 1f else 0f)
                shader.setFloatUniform("edgePadPx", edgePadPx)

                // 高光参数
                shader.setFloatUniform("twoRimPower", twoRimPower)
                shader.setFloatUniform("lineGain", lineGain)
                shader.setFloatUniform("lineSigmaPx", lineSigmaPx)

                // 方向混合 & 按压位置
                shader.setFloatUniform("lineDirMix", lineDirMix)
                shader.setFloatUniform("pressPos", posForShader.x, posForShader.y)
                shader.setFloatUniform("pressAlpha", pressAlpha)

                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "image")
                    .asComposeRenderEffect()
            }
    ) {
        content()
    }
}

// ======================= 预览：按钮居中 + 可调圆角 =======================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun PreviewLiquidGlassPointRefPress() {
    var cornerRadiusDp by remember { mutableStateOf(24f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center  // —— 居中 —— //
    ) {
        // 背景图只是用来对比效果，可移除
        Image(
            modifier = Modifier.fillMaxSize().blur(100.dp),
            painter = painterResource(id = com.example.myapplication.R.drawable.desktop),
            contentDescription = "背景图片",
            contentScale = ContentScale.Crop
        )



        LiquidGlassPointRefPressBox(
            size = 160.dp,
            cornerRadius = cornerRadiusDp.dp,
            bgColor = Color(0xFFFFFFFF),
            bgAlpha = 0.2f,
            edgeWidthPx = 2.0f,
            baseEdgePx = 5.0f,
            baseEdgeStrength = 0.25f,
            clipToShape = true,
            edgePadPx = -1.0f,
            twoRimPower = 2.8f,
            lineGain = 1.6f,
            lineSigmaPx = 2.2f
        ) {
            // 内容可选，这里放个透明文本占位
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // ✅ 底层玻璃效果（带 bgAlpha）
                LiquidGlassPointRefPressBox(
                    modifier = Modifier.matchParentSize(),
                    bgAlpha = 0.2f,
                    bgColor = Color.White.copy(alpha = 1f), // 内部仍然可控
                    content = {}
                )

                // ✅ 上层文字，不受 shader 影响
                Text(
                    "1",
                    color = Color.White,
                    fontSize = 52.sp
                )
            }

        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Slider(
                value = cornerRadiusDp,
                onValueChange = { cornerRadiusDp = it },
                valueRange = 0f..160f,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize(fraction = 0.8f)
            )
        }
    }
}
