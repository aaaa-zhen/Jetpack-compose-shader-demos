import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RenderEffect

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun GlowDrawingScreen() {
    val lines = remember { mutableStateListOf<Line>() }
    var intensity by remember { mutableStateOf(2.5f) } // 增加默认发光强度
    var lineWidth by remember { mutableStateOf(8f) } // 可调整线宽
    var glowColor by remember { mutableStateOf(Color(1.0f, 1.0f, 1.0f, 1.0f)) } // 发光颜色，默认为蓝色

    // 使用SDF方法的新Shader
    val shader = remember { RuntimeShader(sdfGlowShader) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(true) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        lines.add(
                            Line(
                                start = change.position - dragAmount,
                                end = change.position,
                                color = Color.White, // 线条颜色
                                strokeWidth = lineWidth.dp
                            )
                        )
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shader.setFloatUniform("resolution", size.width, size.height)
                        shader.setFloatUniform("intensity", intensity)
                        shader.setFloatUniform("lineWidth", lineWidth)
                        shader.setFloatUniform("glowColor", glowColor.red, glowColor.green, glowColor.blue)
                        renderEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(shader, "image")
                            .asComposeRenderEffect()
                    }
            ) {
                val stroke = Stroke(width = lineWidth.dp.toPx(), cap = StrokeCap.Round)

                lines.forEach { line ->
                    drawLine(
                        color = line.color,
                        start = line.start,
                        end = line.end,
                        strokeWidth = stroke.width,
                        cap = stroke.cap
                    )
                }
            }
        }

        // 控制面板
        Column(
            modifier = Modifier
                .background(Color(0xFF222222))
                .padding(16.dp)
        ) {
            Text(
                text = "Glow Intensity: ${"%.2f".format(intensity)}",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                valueRange = 0f..10f, // 更大的范围
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Text(
                text = "Line Width: ${"%.1f".format(lineWidth)} dp",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = lineWidth,
                onValueChange = { lineWidth = it },
                valueRange = 1f..20f,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Button(
                onClick = { lines.clear() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Clear Canvas")
            }
        }
    }
}

data class Line(
    val start: Offset,
    val end: Offset,
    val color: Color = Color.White,
    val strokeWidth: Dp = 8.dp
)

// 完全硬编码采样点的发光shader
private val sdfGlowShader = """
    uniform shader image;
    uniform float2 resolution;
    uniform float intensity;
    uniform float lineWidth;
    uniform float3 glowColor;

    half4 main(float2 coord) {
        // 获取原始颜色
        half4 srcColor = image.eval(coord);
        
        // 如果当前像素已经有内容，直接返回
        if (srcColor.a > 0.1) {
            return srcColor;
        }
        
        // 初始化最小距离为一个大值
        float minDist = 100000.0;
        const float MAX_RADIUS = 30.0;
        
        // 硬编码16个方向的采样
        // 0度方向
        float angle = 0.0;
        float cosA = 1.0;  // cos(0) = 1
        float sinA = 0.0;  // sin(0) = 0
        
        // 距离1
        half4 sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 45度方向
        angle = 0.7853; // 45度对应的弧度
        cosA = 0.7071;  // cos(45度)
        sinA = 0.7071;  // sin(45度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 90度方向
        angle = 1.5707; // 90度对应的弧度
        cosA = 0.0;     // cos(90度)
        sinA = 1.0;     // sin(90度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 135度方向
        angle = 2.3561; // 135度对应的弧度
        cosA = -0.7071; // cos(135度)
        sinA = 0.7071;  // sin(135度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 180度方向
        angle = 3.1415; // 180度对应的弧度
        cosA = -1.0;    // cos(180度)
        sinA = 0.0;     // sin(180度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 225度方向
        angle = 3.9269; // 225度对应的弧度
        cosA = -0.7071; // cos(225度)
        sinA = -0.7071; // sin(225度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 270度方向
        angle = 4.7123; // 270度对应的弧度
        cosA = 0.0;     // cos(270度)
        sinA = -1.0;    // sin(270度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 315度方向
        angle = 5.4977; // 315度对应的弧度
        cosA = 0.7071;  // cos(315度)
        sinA = -0.7071; // sin(315度)
        
        // 距离1
        sampleColor = image.eval(coord + float2(cosA, sinA) * 1.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 1.0);
        
        // 距离3
        sampleColor = image.eval(coord + float2(cosA, sinA) * 3.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 3.0);
        
        // 距离7
        sampleColor = image.eval(coord + float2(cosA, sinA) * 7.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 7.0);
        
        // 距离15
        sampleColor = image.eval(coord + float2(cosA, sinA) * 15.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 15.0);
        
        // 距离30
        sampleColor = image.eval(coord + float2(cosA, sinA) * 30.0);
        if (sampleColor.a > 0.1) minDist = min(minDist, 30.0);
        
        // 如果找到了附近有内容的像素，创建发光效果
        if (minDist < MAX_RADIUS) {
            // 使用指数衰减创建柔和的发光
            float glowStrength = exp(-minDist * 0.1) * intensity;
            
            // 使用指定的发光颜色
            half3 glow = half3(glowColor.r, glowColor.g, glowColor.b) * glowStrength;
            
            return half4(glow, glowStrength * 0.8); // 控制透明度
        }
        
        return half4(0.0, 0.0, 0.0, 0.0); // 远处没有发光
    }
""".trimIndent()