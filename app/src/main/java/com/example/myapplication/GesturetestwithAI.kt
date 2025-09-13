package com.example.myapplication

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
@Preview
@Composable
fun PictureInPictureDemo() {
    // Use BoxWithConstraints to obtain the container dimensions.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Get the current density to convert dp to px.
        val density = LocalDensity.current
        // Define the size of the mini window.
        val miniWindowSize = 150.dp
        // Convert the mini window size to pixels.
        val miniWindowPx = with(density) { miniWindowSize.toPx() }
        // Create an Animatable to track the window's offset.
        val offset = remember {
            androidx.compose.animation.core.Animatable(
                Offset(0f, 0f),
                Offset.VectorConverter
            )
        }
        // A decay spec for fling simulation (for Float values).
        val decaySpec = rememberSplineBasedDecay<Float>()

        Box(
            modifier = Modifier
                // Apply the animated offset.
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(miniWindowSize)
                .background(Color.Red)
                // Gesture handling with pointerInput.
                .pointerInput(Unit) {
                    // Continuously handle gestures.
                    while (true) {
                        // Create a new velocity tracker for this gesture.
                        val velocityTracker = VelocityTracker()
                        // Cancel any ongoing animation when a new gesture starts.
                        offset.stop()

                        coroutineScope {  // 添加协程作用域
                            // Process a complete gesture sequence.
                            awaitPointerEventScope {
                                // Wait for the first touch down event.
                                val down = awaitFirstDown()
                                // Handle horizontal & vertical drags.
                                drag(down.id) { change ->
                                    // Update the position immediately.
                                    launch {
                                        offset.snapTo(offset.value + change.positionChange())
                                    }
                                    // Track the pointer's positions to calculate velocity.
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    change.consumeAllChanges()
                                }
                            }

                            // Calculate the velocity at the end of the drag.
                            val velocity = velocityTracker.calculateVelocity()
                            // Use decay simulation to predict where the fling would naturally end.
                            val predictedTargetX = decaySpec.calculateTargetValue(offset.value.x, velocity.x)
                            val predictedTargetY = decaySpec.calculateTargetValue(offset.value.y, velocity.y)
                            val predictedTarget = Offset(predictedTargetX, predictedTargetY)

                            // Get the container dimensions (in pixels).
                            val containerWidth = constraints.maxWidth.toFloat()
                            val containerHeight = constraints.maxHeight.toFloat()

                            // Determine the target corner.
                            // If the predicted X is in the left half, snap to left, else right.
                            // If the predicted Y is in the top half, snap to top, else bottom.
                            val targetCorner = Offset(
                                x = if (predictedTarget.x < containerWidth / 2f) 0f else containerWidth - miniWindowPx,
                                y = if (predictedTarget.y < containerHeight / 2f) 0f else containerHeight - miniWindowPx
                            )

                            // Animate to the chosen corner using a spring for a natural bounce.
                            launch {
                                offset.animateTo(
                                    targetValue = targetCorner,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    }
                }
        ){
            Text("111")
        }
    }
}
