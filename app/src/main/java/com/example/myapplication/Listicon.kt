package com.example.myapplication

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.HapticFeedbackConstantsCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Preview
@Composable
fun ListIconDemo() {
    val density = LocalDensity.current
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val circlePositions = remember { mutableStateListOf<Offset>().apply { repeat(20) { add(Offset.Zero) } } }
    var enlargedCircleIndex by remember { mutableStateOf(-1) }

    // Haptic feedback control
    val view = LocalView.current
    val lastFeedbackTime = remember { mutableStateOf(0L) }

    // Screen information (for detecting hot zones)
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val edgeThresholdPx = 100f  // Distance from left/right edge that triggers auto‐scroll

    // Track whether we’re in a hot zone
    var isInRightHotZone by remember { mutableStateOf(false) }
    var isInLeftHotZone by remember { mutableStateOf(false) }

    // LazyListState for the LazyRow
    val listState = rememberLazyListState()

    // Continuously auto‐scroll when in hot zones
    LaunchedEffect(Unit) {
        while (true) {
            when {
                isInRightHotZone -> {
                    // Scroll to the right
                    listState.scrollBy(6f)
                }
                isInLeftHotZone -> {
                    // Scroll to the left
                    listState.scrollBy(-6f)
                }
            }
            // Small delay to avoid blocking the main thread
            delay(16)
        }
    }

    // Helper function for the target sizes
    fun getTargetSize(index: Int, enlargedIndex: Int): Pair<Dp, Dp> {
        val isEnlarged = index == enlargedIndex
        return if (index == 0) {
            // The first item’s size
            if (isEnlarged) 174.dp to 70.dp else 134.dp to 54.dp
        } else {
            // Other items
            if (isEnlarged) 70.dp to 70.dp else 54.dp to 54.dp
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray)
        ) {
            // Fixed height container at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(bottom = 30.dp)
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clipToBounds(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    items(20) { index ->
                        val (targetWidth, targetHeight) = getTargetSize(index, enlargedCircleIndex)

                        val animWidth by animateDpAsState(
                            targetValue = targetWidth,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 160f)
                        )
                        val animHeight by animateDpAsState(
                            targetValue = targetHeight,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 160f)
                        )

                        val shape = if (index == 0) {
                            RoundedCornerShape(percent = 50)
                        } else {
                            CircleShape
                        }

                        Box(
                            modifier = Modifier
                                .size(width = animWidth, height = animHeight)
                                .background(Color.White, shape)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    val center = position + Offset(
                                        coordinates.size.width / 2f,
                                        coordinates.size.height / 2f
                                    )
                                    circlePositions[index] = center
                                }
                        ) {
                            // The first item shows text & icon
                            if (index == 0) {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Replace with your icon painter
//                                        Image(
//                                            painter = painterResource(id = R.drawable.aicyicon),
//                                            contentDescription = null,
//                                            modifier = Modifier.size(24.dp)
//                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                       Text(
                                            text = "拖拽给 Aicy",
                                            textAlign = TextAlign.Center,
                                            color = Color.Black,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                // The other items show images
//                                Image(
//                                    painter = painterResource(id = R.drawable.icon),
//                                    contentDescription = null,
//                                    modifier = Modifier.fillMaxSize()
//                                )
                            }
                        }

                        // Haptic feedback effect on enlarge
                        LaunchedEffect(enlargedCircleIndex) {
                            if (enlargedCircleIndex == index) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastFeedbackTime.value > 100) {
                                    view.performHapticFeedback(
                                        /* TYPE: */ androidx.core.view.HapticFeedbackConstantsCompat.CLOCK_TICK
                                    )
                                    lastFeedbackTime.value = currentTime
                                }
                            }
                        }
                    }
                }
            }

            // Draggable red box (with cat image)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 1f))
                    .zIndex(1f) // Ensure it's on top
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y

                                // Compute the center of the red box
                                val redBoxCenterX = offsetX + with(density) { 50.dp.toPx() }
                                val redBoxCenterY = offsetY + with(density) { 50.dp.toPx() }

                                // Check distance to left/right edges
                                isInRightHotZone = (redBoxCenterX > (screenWidthPx - edgeThresholdPx))
                                isInLeftHotZone  = (redBoxCenterX < edgeThresholdPx)

                                // Nearest circle logic
                                val redBoxCenter = Offset(redBoxCenterX, redBoxCenterY)
                                var nearestCircleIndex = -1
                                var minDistance = Float.MAX_VALUE

                                circlePositions.forEachIndexed { index, circleCenter ->
                                    val distance = (redBoxCenter - circleCenter).getDistance()
                                    if (distance < minDistance) {
                                        minDistance = distance
                                        nearestCircleIndex = index
                                    }
                                }

                                // Enlarge if within range
                                enlargedCircleIndex = if (minDistance < 350f) {
                                    nearestCircleIndex
                                } else {
                                    -1
                                }
                            },
                            onDragEnd = {
                                // When the drag ends, stop auto‐scroll
                                isInRightHotZone = false
                                isInLeftHotZone = false
                            },
                            onDragCancel = {
                                // If the drag is canceled, stop auto‐scroll
                                isInRightHotZone = false
                                isInLeftHotZone = false
                            }
                        )
                    }
            ) {
                // Replace with your actual cat resource
//                Image(
//                    painter = painterResource(id = R.drawable.cat),
//                    contentDescription = null,
//                    alpha = 0.8f
//                )
            }
        }
    }
}
