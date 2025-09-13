import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Using Material 3 Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


// AGSL Shader Code - Updated for Slider Controls & Added Dummy Uniform
val FlashLightSliderAGSL = """
    // --- Configuration (Constants) ---
    const float2 LIGHT_ORIGIN        = float2(0.0, -0.6); // Position where light starts (normalized, center 0,0)
    const float  BEAM_BRIGHTNESS     = 2.0;           // Overall brightness
    const float  BEAM_SPREAD         = 3.0;           // How wide the beam spreads horizontally
    const float  BEAM_CORE_SHARPNESS = 3.4;           // Sharpness of the central core

    // --- Uniforms ---
    uniform float2 resolution;       // Viewport resolution (width, height) in pixels
    uniform float uFalloffControl; // Slider value [0, 1] controlling falloff (beam length)
    uniform float uFringeControl;  // Slider value [0, 1] controlling fringe amount
    uniform shader content;        // Dummy uniform required by RenderEffect API, even if unused

    // --- HSV/RGB Conversion Functions (Optional, kept from original) ---
    float3 rgb2hsv(float3 c) {
        float4 K = float4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        float4 p = mix(float4(c.bg, K.wz), float4(c.gb, K.xy), step(c.b, c.g));
        float4 q = mix(float4(p.xyw, c.r), float4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10; // Epsilon
        return float3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
     }
    float3 hsv2rgb(float3 c) {
        float4 K = float4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        float3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
     }

    // --- Beam Intensity Calculation ---
    // p: current point relative to light origin in normalized coords [-aspect,aspect] x [-1,1]
    // currentLengthFalloff: calculated falloff value
    float getBeamIntensity(float2 p, float currentLengthFalloff) {
        // Cut off beam below origin
        if (p.y <= -0.1) return 0.0;

        // Calculate beam width based on vertical distance
        float beamWidth = mix(0.06, 0.9, smoothstep(0.0, 1.5, p.y));

        // Horizontal distance from center, normalized by width and spread
        float horizontalDist = abs(p.x) / (beamWidth * BEAM_SPREAD + 1e-5); // Add epsilon for stability

        // Intensity falloff horizontally (center core)
        float horizontalFalloff = exp(-pow(horizontalDist, 1.8) * BEAM_CORE_SHARPNESS);

        // Intensity falloff vertically (beam length)
        float verticalFalloff = exp(-p.y * currentLengthFalloff) * smoothstep(-0.05, 0.1, p.y); // Fade in smoothly near origin

        // Combine falloffs and scale by overall brightness
        return horizontalFalloff * verticalFalloff * BEAM_BRIGHTNESS;
    }

    // --- Main Shader ---
    // Input: fragCoord (pixel coordinates, origin top-left)
    // Output: Color for the pixel (medium precision)
    half4 main(float2 fragCoord) {
        // 1. Convert pixel coordinates to normalized coordinates (origin center, -aspect..aspect horizontally, -1..1 vertically)
        float2 uv = (fragCoord * 2.0 - resolution) / resolution.y;

        // 2. Calculate dynamic parameters based on slider uniforms
        // Map slider [0,1] to falloff range [20.0 (short beam), 2.5 (long beam)]
        float dynamicFalloff = mix(20.0, 2.5, uFalloffControl);
        // Map slider [0,1] to fringe offset range [0.0 (none), 0.03 (max relative offset)]
        float dynamicFringeAmount = mix(0.0, 0.03, uFringeControl); // Max offset as fraction of screen height

        // 3. Calculate position relative to the light source origin
        float2 p = uv - LIGHT_ORIGIN;

        // 4. Calculate central intensity
        float intensityCenter = getBeamIntensity(p, dynamicFalloff);

        // 5. Calculate chromatic aberration (fringe) offsets
        // Convert relative fringe amount to a pixel offset, then back to normalized UV offset
        float pixel_offset_x = dynamicFringeAmount * resolution.y; // Offset in pixels
        float norm_offset_x = (pixel_offset_x * 2.0) / resolution.y; // Equivalent offset in normalized UV space

        // Determine offset direction (left/right from beam center)
        float2 offsetDir = float2(sign(p.x), 0.0);
        if (abs(p.x) < 0.001) offsetDir = float2(1.0, 0.0); // Avoid zero offset at center line
        float2 offset_uv = offsetDir * norm_offset_x; // The offset vector in UV space

        // 6. Calculate intensities at offset positions for R and B channels
        float intensityR_side = getBeamIntensity(p + offset_uv, dynamicFalloff); // Offset one way for Red
        float intensityB_side = getBeamIntensity(p - offset_uv, dynamicFalloff); // Offset other way for Blue

        // 7. Assemble final color using intensities for R, G, B
        float3 finalColor = float3(intensityR_side, intensityCenter, intensityB_side);

        // 8. Clamp color to valid range [0, 1] and set alpha
        finalColor = clamp(finalColor, 0.0, 1.0);

        // Return the final color (medium precision)
        return half4(finalColor, 1.0);

        // Note: The 'content' uniform is declared but never used (content.eval() is never called).
    }
""".trimIndent()

@Preview
@RequiresApi(Build.VERSION_CODES.TIRAMISU) // RuntimeShader requires Android 13+
@Composable
fun FlashLightSliderEffect() {
    // Create the RuntimeShader instance once and remember it
    val shader = remember { RuntimeShader(FlashLightSliderAGSL) }

    // State to hold the size of the Composable where the shader is applied
    val viewSize = remember { mutableStateOf(IntSize.Zero) }

    // State variables for the sliders, controlling the shader uniforms
    val falloffSliderValue = remember { mutableStateOf(0.5f) } // 0.0 (short beam) to 1.0 (long beam)
    val fringeSliderValue = remember { mutableStateOf(0.5f) }  // 0.0 (no fringe) to 1.0 (max fringe)

    // Use LaunchedEffect to update shader uniforms when inputs change.
    // This is generally more efficient than setting them directly in graphicsLayer's lambda.
    LaunchedEffect(viewSize.value, falloffSliderValue.value, fringeSliderValue.value) {
        // Set resolution uniform if view size is known
        if (viewSize.value != IntSize.Zero) {
            shader.setFloatUniform(
                "resolution", // Name must match AGSL uniform
                viewSize.value.width.toFloat(),
                viewSize.value.height.toFloat()
            )
        }
        // Set slider-controlled uniforms
        shader.setFloatUniform("uFalloffControl", falloffSliderValue.value)
        shader.setFloatUniform("uFringeControl", fringeSliderValue.value)
    }

    // Main layout Column
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray), // Overall background
        horizontalAlignment = Alignment.CenterHorizontally // Center sliders horizontally
    ) {
        // Box to display the shader effect
        Box(
            modifier = Modifier
                .weight(1f) // Occupy remaining space above the controls
                .fillMaxWidth()
                .background(Color.Black) // The background onto which the shader draws
                .onSizeChanged { size ->
                    // Update view size state when the Box's size changes
                    viewSize.value = size
                    // Also set initial resolution here. LaunchedEffect handles subsequent updates.
                    shader.setFloatUniform(
                        "resolution",
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
                .graphicsLayer {
                    // Apply the RenderEffect using the shader
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(
                            shader,
                            "content" // Name MUST match the dummy 'uniform shader' in AGSL
                        )
                        .asComposeRenderEffect() // Convert to Compose-compatible RenderEffect
                    // Ensure the layer is drawn. Sometimes needed if content is fully transparent or size is zero initially.
                    // alpha = 0.99f // Uncomment if the effect is unexpectedly invisible
                }
        ) // End of Box for shader effect

        // Column for the control sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray.copy(alpha = 0.8f)) // Slightly different background for controls
                .padding(horizontal = 16.dp, vertical = 12.dp) // Padding around sliders
        ) {
            // Slider for Beam Length (Falloff)
            Text(
                text = "Beam Length: ${(falloffSliderValue.value * 100).roundToInt()}%",
                color = Color.White
            )
            Slider(
                value = falloffSliderValue.value,
                onValueChange = { falloffSliderValue.value = it }, // Update state on change
                valueRange = 0f..1f // Slider range 0 to 1
            )
            Spacer(modifier = Modifier.height(16.dp)) // Space between sliders

            // Slider for Fringe Amount
            Text(
                text = "Fringe Amount: ${(fringeSliderValue.value * 100).roundToInt()}%",
                color = Color.White
            )
            Slider(
                value = fringeSliderValue.value,
                onValueChange = { fringeSliderValue.value = it }, // Update state on change
                valueRange = 0f..1f // Slider range 0 to 1
            )
        } // End of Column for controls
    } // End of main layout Column
}