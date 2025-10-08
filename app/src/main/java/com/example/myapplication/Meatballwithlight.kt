package com.example.myapplication
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
// ======================= AGSL (Two Meatballs Version) =======================
// ======================= AGSL (Two Meatballs with Metaball Effect) =======================
// ======================= AGSL (Two Meatballs with Dynamic Radius) =======================
private const val LiquidGlassMeatballAGSL = """
uniform shader image;
uniform float2 resolution;
uniform float  time;

// Fill & outline
uniform float3 bgColor;
uniform float  bgAlpha;
uniform float  edgeWidthPx;
uniform float  baseEdgePx;
uniform float  baseEdgeStrength;
uniform float  ballRadius;
uniform float  ballSpacing;

// 左右球独立的半径（用于按压放大）
uniform float  leftBallRadius;
uniform float  rightBallRadius;

// Metaball effect
uniform float  metaballStrength;

// Edge clipping
uniform float  clipToShape;
uniform float  edgePadPx;

// Rotating highlight
uniform float  twoRimPower;

// Line appearance
uniform float  lineGain;
uniform float  lineSigmaPx;

// Press light
uniform float2 pressPos;
uniform float  pressAlpha;

// Direction blend
uniform float  lineDirMix;

// ---------- Constants ----------
const float ROT_SPEED    = 0.35;
const float SPEC_POWER   = 32.0;

const float CORE_SIGMA_PX = 65.0;
const float HALO_SIGMA_PX = 480.0;
const float CORE_GAIN     = 0.6;
const float HALO_GAIN     = 0.3;
const float RANGE_PX      = 520.0;

// ========== Metaball SDF (粘性效果) ==========
float metaballField(float2 p, float2 center, float radius){
    float dist = length(p - center);
    return radius / (dist + 0.001);
}

float shapeSDF(float2 p){
    float halfSpacing = ballSpacing * 0.5;
    float2 leftCenter = float2(-halfSpacing, 0.0);
    float2 rightCenter = float2(halfSpacing, 0.0);
    
    // 使用独立的半径（按压时会变大）
    float field1 = metaballField(p, leftCenter, leftBallRadius);
    float field2 = metaballField(p, rightCenter, rightBallRadius);
    
    float totalField = field1 + field2;
    
    float threshold = metaballStrength;
    return threshold - totalField;
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

// ========== Main ==========
half4 main(float2 fragCoord){
    float2 uv = (fragCoord - 0.5*resolution) / resolution.y;

    float d   = shapeSDF(uv);
    float2 n2 = sdfNormal(uv);

    float px = 1.0 / resolution.y;
    float aa = 1.5 * px;
    float dShift = d - edgePadPx * px;
    float insideSoft = 1.0 - smoothstep(0.0, aa, dShift);
    float insideHard = step(dShift, 0.0);

    half4 base = image.eval(fragCoord);
    float3 rgb = mix(base.rgb, bgColor, bgAlpha * insideSoft);

    float baseBand = 1.0 - smoothstep(0.0, baseEdgePx*px, abs(d));
    rgb += float3(0.32) * baseBand * baseEdgeStrength * insideSoft;

    // ===== Rotating/Press direction highlight =====
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

    float3 add = float3(0.0);
    add += float3(1.0) * (lineTerm * band * edgeMask * max(lineGain, 1.0)) * insideSoft;

    // ===== Point light (按压发光) =====
    float rPx = length(uv - lpUV)/px;
    float core = CORE_GAIN * gRad(rPx, CORE_SIGMA_PX);
    float halo = HALO_GAIN * gRad(rPx, HALO_SIGMA_PX);
    float range = 1.0 - smoothstep(0.0, RANGE_PX, rPx);
    float diffuse = (core + halo) * range;

    float clipMask = mix(1.0, insideHard, clamp(clipToShape, 0.0, 1.0));
    add += float3(diffuse) * pressAlpha * clipMask;

    float3 col = rgb + add;

    float outA = max(base.a, bgAlpha * insideSoft);
    return half4(clamp(col, 0.0, 1.0), outA);
}
"""

// ======================= Composable Wrapper =======================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiquidGlassMeatball(
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    height: Dp = 160.dp,
    ballRadius: Float = 0.28f,        // 初始半径（较小，有间距）
    ballSpacing: Float = 0.75f,       // 球心间距（较大）
    pressScaleMultiplier: Float = 1.5f, // 按压时半径放大倍数
    metaballStrength: Float = 2.8f,

    bgColor: Color = Color(0xFF2F6FEE),
    bgAlpha: Float = 1f,

    edgeWidthPx: Float = 3f,
    baseEdgePx: Float = 3f,
    baseEdgeStrength: Float = 0.55f,

    clipToShape: Boolean = true,
    edgePadPx: Float = -1f,

    twoRimPower: Float = 1.0f,

    lineGain: Float = 1.6f,
    lineSigmaPx: Float = 2.2f,

    onLeftClick: () -> Unit = {},
    onRightClick: () -> Unit = {},

    content: @Composable BoxScope.(isLeft: Boolean) -> Unit = { _ -> }
) {
    val shader = remember { RuntimeShader(LiquidGlassMeatballAGSL) }

    var renderSize by remember { mutableStateOf(IntSize.Zero) }

    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            time = awaitFrame() * 1e-9f
        }
    }

    var pressing by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    var lastPos by remember { mutableStateOf(Offset.Zero) }

    var pressedBall by remember { mutableIntStateOf(-1) }

    val pressAlpha by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = if (pressing)
            tween(540, easing = FastOutSlowInEasing)
        else
            tween(560, easing = FastOutSlowInEasing),
        label = "pressAlpha"
    )

    val lineDirMix by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = if (pressing)
            tween(durationMillis = 100, easing = FastOutSlowInEasing)
        else
            tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lineDirMix"
    )

    // 左球半径动画（按压时变大）
    val leftBallRadius by animateFloatAsState(
        targetValue = if (pressedBall == 0) ballRadius * pressScaleMultiplier else ballRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "leftBallRadius"
    )

    // 右球半径动画
    val rightBallRadius by animateFloatAsState(
        targetValue = if (pressedBall == 1) ballRadius * pressScaleMultiplier else ballRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rightBallRadius"
    )

    val posForShader = if (pressing) touchPos else lastPos

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    pressing = true
                    touchPos = d.position
                    lastPos = touchPos

                    pressedBall = if (d.position.x < size.width / 2f) 0 else 1

                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed) {
                                touchPos = c.position
                                lastPos = touchPos
                            }
                        }
                        if (ev.changes.all { it.changedToUpIgnoreConsumed() }) break
                    }

                    if (pressedBall == 0) onLeftClick() else onRightClick()

                    pressing = false
                    pressedBall = -1
                }
            }
            .onSizeChanged { sz -> renderSize = sz }
            .graphicsLayer {
                shader.setFloatUniform("time", time)

                val w = renderSize.width.takeIf { it > 0 } ?: 1
                val h = renderSize.height.takeIf { it > 0 } ?: 1

                shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())
                shader.setFloatUniform("ballRadius", ballRadius)
                shader.setFloatUniform("ballSpacing", ballSpacing)

                // 传入动态变化的左右球半径
                shader.setFloatUniform("leftBallRadius", leftBallRadius)
                shader.setFloatUniform("rightBallRadius", rightBallRadius)
                shader.setFloatUniform("metaballStrength", metaballStrength)

                shader.setFloatUniform("bgColor", bgColor.red, bgColor.green, bgColor.blue)
                shader.setFloatUniform("bgAlpha", bgAlpha)
                shader.setFloatUniform("edgeWidthPx", edgeWidthPx)
                shader.setFloatUniform("baseEdgePx", baseEdgePx)
                shader.setFloatUniform("baseEdgeStrength", baseEdgeStrength)

                shader.setFloatUniform("clipToShape", if (clipToShape) 1f else 0f)
                shader.setFloatUniform("edgePadPx", edgePadPx)

                shader.setFloatUniform("twoRimPower", twoRimPower)
                shader.setFloatUniform("lineGain", lineGain)
                shader.setFloatUniform("lineSigmaPx", lineSigmaPx)

                shader.setFloatUniform("lineDirMix", lineDirMix)
                shader.setFloatUniform("pressPos", posForShader.x, posForShader.y)
                shader.setFloatUniform("pressAlpha", pressAlpha)

                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "image")
                    .asComposeRenderEffect()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                content(true)
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                content(false)
            }
        }
    }
}

// ======================= Preview =======================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun PreviewLiquidGlassMeatball() {
    var radiusScale by remember { mutableStateOf(0.28f) }
    var spacing by remember { mutableStateOf(0.75f) }
    var pressScale by remember { mutableStateOf(1.5f) }
    var metaStrength by remember { mutableStateOf(2.8f) }
    var clickCount by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.fillMaxSize().blur(100.dp),
            painter = painterResource(id = com.example.myapplication.R.drawable.desktop),
            contentDescription = "Background",
            contentScale = ContentScale.Crop
        )

        LiquidGlassMeatball(
            width = 340.dp,
            height = 160.dp,
            ballRadius = radiusScale,
            ballSpacing = spacing,
            pressScaleMultiplier = pressScale,
            metaballStrength = metaStrength,
            bgColor = Color(0xFFFFFFFF),
            bgAlpha = 0.25f,
            edgeWidthPx = 2.0f,
            baseEdgePx = 5.0f,
            baseEdgeStrength = 0.3f,
            clipToShape = true,
            edgePadPx = -1.0f,
            twoRimPower = 2.8f,
            lineGain = 1.6f,
            lineSigmaPx = 2.2f,
            onLeftClick = { clickCount++ },
            onRightClick = { clickCount++ }
        ) { isLeft ->
            Text(
                if (isLeft) "6" else "—",
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "Clicks: $clickCount\nTap left or right ball!",
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ball Radius: ${(radiusScale * 100).toInt()}%",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = radiusScale,
                onValueChange = { radiusScale = it },
                valueRange = 0.2f..0.4f,
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(0.8f)
            )

            Text(
                "Ball Spacing: ${(spacing * 100).toInt()}%",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = spacing,
                onValueChange = { spacing = it },
                valueRange = 0.5f..1.2f,
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(0.8f)
            )

            Text(
                "Press Scale: ${String.format("%.1f", pressScale)}x",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = pressScale,
                onValueChange = { pressScale = it },
                valueRange = 1.2f..2.0f,
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(0.8f)
            )

            Text(
                "Metaball: ${String.format("%.1f", metaStrength)}",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = metaStrength,
                onValueChange = { metaStrength = it },
                valueRange = 2.0f..4.0f,
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(0.8f)
            )
        }
    }
}