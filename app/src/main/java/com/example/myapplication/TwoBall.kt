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
uniform float  leftScale;      // 左球缩放
uniform float  rightScale;     // 右球缩放

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

// Direction blend: 0=global rotation, 1=lock to press direction
uniform float  lineDirMix;

// ---------- Constants ----------
const float ROT_SPEED    = 0.55;
const float SPEC_POWER   = 32.0;

const float CORE_SIGMA_PX = 65.0;
const float HALO_SIGMA_PX = 480.0;
const float CORE_GAIN     = 0.5;   // 降低亮度但保持范围
const float HALO_GAIN     = 0.52;  // 降低亮度但保持范围
const float RANGE_PX      = 520.0;

// ========== Circle SDF ==========
float sdCircle(float2 p, float r){
    return length(p) - r;
}

// SDF for two balls with individual scaling
float shapeSDF(float2 p){
    float normalizedRadius = ballRadius;
    float halfSpacing = ballSpacing * 0.5;
    
    // Left ball center with scale
    float2 leftCenter = float2(-halfSpacing, 0.0);
    float d1 = sdCircle(p - leftCenter, normalizedRadius * leftScale);
    
    // Right ball center with scale
    float2 rightCenter = float2(halfSpacing, 0.0);
    float d2 = sdCircle(p - rightCenter, normalizedRadius * rightScale);
    
    // Union: minimum distance to either ball
    return min(d1, d2);
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

    // ===== Point light (press light) =====
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
fun LiquidGlassTwoMeatball(
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    height: Dp = 160.dp,
    ballRadius: Float = 0.35f,
    ballSpacing: Float = 0.6f,

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

    content: @Composable BoxScope.() -> Unit = {}
) {
    val shader = remember { RuntimeShader(LiquidGlassMeatballAGSL) }
    val density = LocalDensity.current

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
    var clickedBall by remember { mutableStateOf<String?>(null) } // "left" or "right"

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

    val leftScale by animateFloatAsState(
        targetValue = if (clickedBall == "left") 1.1f else 1f,
        animationSpec =
            if (clickedBall == "left")
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            else
                spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "leftScale",
        visibilityThreshold = 0.000001f
    )

    val rightScale by animateFloatAsState(
        targetValue = if (clickedBall == "right") 1.1f else 1f,
        animationSpec =
            if (clickedBall == "right")
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            else
                spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "rightScale",
        visibilityThreshold = 0.000001f
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

                    // 判断点击的是哪个球
                    val w = renderSize.width.toFloat()
                    val h = renderSize.height.toFloat()
                    val centerX = w / 2f
                    val centerY = h / 2f
                    val normX = (touchPos.x - centerX) / h
                    val normY = (touchPos.y - centerY) / h

                    val halfSpacing = ballSpacing * 0.5f
                    val leftCenterX = -halfSpacing
                    val rightCenterX = halfSpacing

                    val distToLeft = kotlin.math.sqrt(
                        (normX - leftCenterX) * (normX - leftCenterX) + normY * normY
                    )
                    val distToRight = kotlin.math.sqrt(
                        (normX - rightCenterX) * (normX - rightCenterX) + normY * normY
                    )

                    clickedBall = if (distToLeft < distToRight) "left" else "right"

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
                    pressing = false
                    clickedBall = null
                }
            }
            .onSizeChanged { sz -> renderSize = sz }
            .graphicsLayer {
                shader.setFloatUniform("time", time)

                val w = renderSize.width.takeIf { it > 0 } ?: 1
                val h = renderSize.height.takeIf { it > 0 } ?: 1

                shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())
                shader.setFloatUniform("ballRadius", ballRadius.coerceIn(0.1f, 0.5f))
                shader.setFloatUniform("ballSpacing", ballSpacing.coerceIn(0.0f, 1.5f))
                shader.setFloatUniform("leftScale", leftScale)
                shader.setFloatUniform("rightScale", rightScale)

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
        content()
    }
}

// ======================= Preview =======================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun PreviewLiquidGlassTwoMeatball() {
    var radiusScale by remember { mutableStateOf(0.35f) }
    var spacing by remember { mutableStateOf(0.6f) }

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

        LiquidGlassTwoMeatball(
            width = 280.dp,
            height = 160.dp,
            ballRadius = radiusScale,
            ballSpacing = spacing,
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
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "1",
                    color = Color.White,
                    fontSize = 48.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "2",
                    color = Color.White,
                    fontSize = 48.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ball Radius: ${(radiusScale * 100).toInt()}%",
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = radiusScale,
                onValueChange = { radiusScale = it },
                valueRange = 0.15f..0.45f,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(fraction = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Ball Spacing: ${(spacing * 100).toInt()}%",
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = spacing,
                onValueChange = { spacing = it },
                valueRange = 0.3f..1.2f,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(fraction = 0.8f)
            )
        }
    }
}