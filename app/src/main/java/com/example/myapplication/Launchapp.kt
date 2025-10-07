package com.example.myapplication
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize


import androidx.compose.ui.tooling.preview.Preview

private val skewTransitionShader = """
uniform shader image;
uniform float2 resolution;          // 画布像素
uniform float  progress;            // 0..1
uniform float4 fromRectPx;          // x,y,w,h（像素）
uniform float  cornerPx;            // 圆角像素
uniform float  intensity;           // 顶部张开快慢 0.6~1.6

float sdRoundedRect(float2 p, float2 b, float r){
    float2 q = abs(p) - b + r;
    float outside = length(max(q, 0.0)) - r;
    float inside  = min(max(q.x, q.y), 0.0);
    return outside + inside;
}

half4 main(float2 frag){
    float2 canvas = resolution;

    // 1) 顶部加速张开：按像素 y 做“局部指数”插值
    float y01 = frag.y / canvas.y;
    float pf  = pow(progress, 1.0 / mix(1.0, intensity, y01));

    // 2) 起点→终点（全屏）
    float4 endRect = float4(0.0, 0.0, canvas.x, canvas.y);
    float4 rect = mix(fromRectPx, endRect, clamp(pf,0.0,1.0));

    // 3) Arc 轨迹：中心朝屏幕中心正弦偏移（0→1→0）
    float2 startC = float2(fromRectPx.x + fromRectPx.z*0.5, fromRectPx.y + fromRectPx.w*0.5);
    float2 screenC= canvas * 0.5;
    float2 arcOff = (screenC - startC) * float2(0.20, 0.40) * sin(progress*3.14159265);
    rect.xy += arcOff;

    // 4) 局部坐标 & skew 鼓角（靠屏幕中心一侧）
    float2 halfExt = rect.zw * 0.5;
    float2 center  = rect.xy + halfExt;
    float2 local   = frag - center;

    float skewDir = sign(screenC.x - startC.x); // 向内鼓
    float vInflu  = clamp((local.y + halfExt.y) / rect.w, 0.0, 1.0); // 0顶 1底
    float hInflu  = clamp((local.x + halfExt.x) / rect.z, 0.0, 1.0);
    if (skewDir < 0.0) hInflu = 1.0 - hInflu;

    float skewAmt = 35.0; // px
    float skew    = skewAmt * (1.0 - vInflu) * hInflu * 1.38 * skewDir * sin(progress*3.14159265);

    float2 samplePos = float2(local.x - skew, local.y) + halfExt;
    float2 uv01 = clamp(samplePos / rect.zw, float2(0.0), float2(1.0));
    half4 src = image.eval(uv01 * canvas);

    // 5) 圆角 mask（边缘 1px 软化）
    float d = sdRoundedRect(local, halfExt, cornerPx);
    float m = smoothstep(1.0, -1.0, d);
    return half4(src.rgb * m, src.a * m);
}
""".trimIndent()

@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun SkewTransitionDemo() {
    val shader by remember { mutableStateOf(RuntimeShader(skewTransitionShader)) }
    var res: IntSize by remember { mutableStateOf(IntSize(1,1)) }

    // 起点矩形（你要放大的那个卡片）
    var fromRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    val progress = remember { Animatable(0f) }
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { s ->
                res = s
                shader.setFloatUniform("resolution", s.width.toFloat(), s.height.toFloat())
            }
            .graphicsLayer {
                // 每帧推 uniforms（也可放到 withFrameNanos）
                shader.setFloatUniform("progress", progress.value)
                shader.setFloatUniform(
                    "fromRectPx",
                    fromRect.left, fromRect.top, fromRect.width, fromRect.height
                )
                shader.setFloatUniform("cornerPx", 24f)   // 你的圆角
                shader.setFloatUniform("intensity", 1.2f) // 顶部更快展开
                renderEffect = android.graphics.RenderEffect
                    .createRuntimeShaderEffect(shader, "image")
                    .asComposeRenderEffect()
            }
    ) {
        // 起点卡片
        Card(
            modifier = Modifier
                .size(220.dp, 160.dp)
                .align(Alignment.CenterStart)
                .offset(x = 28.dp,y=300.dp)
                .onGloballyPositioned { lp ->
                    val r = lp.boundsInWindow()
                    fromRect = androidx.compose.ui.geometry.Rect(
                        r.left, r.top, r.right, r.bottom
                    )
                }
                .drawBehind { /* 你的卡片内容 */ },
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.95f))
        ) {}

        // 点击开/关
        LaunchedEffect(open) {
            progress.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .padding(24.dp)
                .align(Alignment.BottomEnd)
                .clickable { open = !open }
        ) {
            Text(
                text = if (open) "关闭" else "打开",
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}