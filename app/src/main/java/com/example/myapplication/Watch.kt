import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Preview
@Composable
fun AppleWatchApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF007AFF),
            secondary = Color(0xFF4CD964),
            tertiary = Color(0xFFFF3B30),
            background = Color.Black,
            surface = Color(0xFF1C1C1E)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            AppleWatchHomeScreen()
        }
    }
}

@Composable
fun AppleWatchHomeScreen() {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Animation for initial appearance
    val initialAppearance = remember { Animatable(0f) }
    LaunchedEffect(key1 = Unit) {
        initialAppearance.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = EaseOutQuart
            )
        )
    }

    // Define animation specs for Apple Watch-like fluid animations
    val scaleAnimSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = 0.01f
    )
    val offsetAnimSpec = spring<Offset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = Offset(0.01f, 0.01f)
    )

    // Animated values with smoother initial state
    val animatedScale = remember {
        Animatable(
            initialValue = 1f,
            visibilityThreshold = 0.01f
        )
    }
    val animatedOffset = remember {
        Animatable(
            initialValue = Offset.Zero,
            Offset.VectorConverter,
            visibilityThreshold = Offset(0.01f, 0.01f)
        )
    }

    // Add inertia effect for more fluid interactions
    val velocityTracker = remember { VelocityTracker() }
    var lastDragPosition by remember { mutableStateOf(Offset.Zero) }
    var lastDragTime by remember { mutableStateOf(0L) }

    // Apple Watch frame
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Watch frame
        Box(
            modifier = Modifier
                .aspectRatio(0.86f)  // Apple Watch aspect ratio
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(38.dp))
                .background(Color.Black)
                .shadow(12.dp, RoundedCornerShape(38.dp))
                .border(
                    width = 2.dp,
                    color = Color(0xFF333333),
                    shape = RoundedCornerShape(38.dp)
                )
                .onGloballyPositioned { coordinates ->
                    size = coordinates.size
                }
                .pointerInput(Unit) {
                    // Standard transform gesture detection
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        // Current system time for velocity calculation
                        val currentTime = System.currentTimeMillis()

                        // Apply constraints with smoother boundaries
                        val newScale = (scale * gestureZoom).coerceIn(0.8f, 2.5f)
                        val maxX = (size.width * (newScale - 1)) / 2
                        val maxY = (size.height * (newScale - 1)) / 2

                        // Calculate velocity for inertia effect
                        if (lastDragTime > 0) {
                            val timeDelta = (currentTime - lastDragTime).coerceAtLeast(1L)
                            velocityTracker.addPosition(
                                timeDelta.toFloat().toLong(),
                                pan
                            )
                        }

                        val newOffset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )

                        // Update animated values with spring physics
                        scope.launch {
                            animatedScale.animateTo(newScale, scaleAnimSpec)
                            animatedOffset.animateTo(newOffset, offsetAnimSpec)
                        }

                        // Update state values
                        scale = newScale
                        offset = newOffset
                        lastDragPosition = pan
                        lastDragTime = currentTime
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // Wait for up events after touch gestures
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { it.changedToUp() }) {
                                // Apply inertia effect when gesture ends
                                val velocity = velocityTracker.calculateVelocity()
                                val decay = splineBasedDecay<Offset>(density)

                                scope.launch {
                                    // Only apply decay if there's significant velocity
                                    if (velocity.x.absoluteValue > 50f || velocity.y.absoluteValue > 50f) {
                                        // FIX #1: Properly use Offset for calculations
                                        try {
                                            // Animate with decay for smooth finish
                                            animatedOffset.animateDecay(
                                                initialVelocity = Offset(velocity.x, velocity.y),
                                                animationSpec = decay
                                            )
                                        } catch (e: Exception) {
                                            // Fallback if decay animation fails
                                            val targetOffset = Offset(
                                                animatedOffset.value.x + (velocity.x / 10f),
                                                animatedOffset.value.y + (velocity.y / 10f)
                                            )
                                            // Apply constraints to the target
                                            val maxX = (size.width * (scale - 1)) / 2
                                            val maxY = (size.height * (scale - 1)) / 2
                                            val constrainedTarget = Offset(
                                                x = targetOffset.x.coerceIn(-maxX, maxX),
                                                y = targetOffset.y.coerceIn(-maxY, maxY)
                                            )
                                            animatedOffset.animateTo(constrainedTarget, offsetAnimSpec)
                                        }
                                    }
                                }

                                // Reset velocity tracker
                                velocityTracker.resetTracking()
                                lastDragTime = 0L
                            }
                        }
                    }
                }
        ) {
            // App grid layout
            // App grid layout
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center // This centers children *before* offset is applied
            ) {
                // Center point (still useful for reference, but not directly in offset calculation)
                // val centerX = size.width / 2f
                // val centerY = size.height / 2f

                // Draw app icons in honeycomb pattern
                val apps = generateAppleWatchApps()

                // Draw app icons in honeycomb pattern with dynamic scaling
                apps.forEach { app ->
                    // Calculate position based on honeycomb grid (in Pixels relative to center)
                    // Use idiomatic conversion from Dp to Px
                    val baseGridOffsetPx = with(density) { 70.dp.toPx() }
                    val gridOffsetX = app.gridX * baseGridOffsetPx
                    val gridOffsetY = app.gridY * baseGridOffsetPx

                    // Calculate pan offset (already in Pixels, relative to center)
                    // Note: The original panOffset calculation was slightly off too.
                    // animatedOffset is the total pan in the *scaled* coordinate system.
                    // To get the effective pan offset on the *unscaled* grid, we don't divide by scale here.
                    // Let's rethink the interaction: offset is the pan applied AT the current scale.
                    // The grid positions are fixed. The view "pans" across the grid.
                    // So the center of the view corresponds to (-offset.x / scale, -offset.y / scale) in grid coords.
                    // The position of an icon *relative to the view center* is (gridX + panX/scale, gridY + panY/scale) * baseOffset
                    // Wait, the existing panOffsetX/Y calculation might be okay conceptually for moving the "camera". Let's stick with it for now.
                    val panOffsetX = animatedOffset.value.x // Pan offset in pixels
                    val panOffsetY = animatedOffset.value.y // Pan offset in pixels


                    // Calculate distance from center for dynamic scaling (using grid coordinates)
                    val distanceFromCenter = if (app.name == "Activity") {
                        0f // Center app doesn't scale based on grid distance
                    } else {
                        // Use gridX/gridY directly for distance calculation
                        sqrt(app.gridX * app.gridX + app.gridY * app.gridY)
                    }

                    // Calculate dynamic scale based on distance and zoom
                    val dynamicScaleFactor = animatedScale.value // Alias for clarity
                    val dynamicScale = when {
                        app.name == "Activity" -> 1f * dynamicScaleFactor // Center app scales with zoom
                        dynamicScaleFactor > 1.5f -> {
                            // When zoomed in, apps scale up more uniformly relative to base size
                            (1f - (distanceFromCenter * 0.05f).coerceIn(0f, 0.3f)) * dynamicScaleFactor
                        }
                        else -> {
                            // When zoomed out, more dramatic scaling difference relative to base size
                            (1f - (distanceFromCenter * 0.15f).coerceIn(0f, 0.5f)) * dynamicScaleFactor
                        }
                    }.coerceAtLeast(0.1f) // Ensure icons don't disappear


                    // Apply subtle parallax effect (relative pixel offset)
                    // Parallax should probably depend on the *view* offset, not grid distance? Or maybe both?
                    // Let's make it depend on grid distance *from the panned center* for a more 3D feel.
                    val viewCenterXInGrid = -animatedOffset.value.x / baseGridOffsetPx / dynamicScaleFactor
                    val viewCenterYInGrid = -animatedOffset.value.y / baseGridOffsetPx / dynamicScaleFactor
                    val effectiveDistX = app.gridX - viewCenterXInGrid
                    val effectiveDistY = app.gridY - viewCenterYInGrid

                    val parallaxFactor = 0.05f / dynamicScaleFactor // Reduce parallax when zoomed in
                    val parallaxOffsetX = -animatedOffset.value.x * parallaxFactor * effectiveDistX // Invert pan for parallax
                    val parallaxOffsetY = -animatedOffset.value.y * parallaxFactor * effectiveDistY // Invert pan for parallax


                    // --- CORRECTED POSITION CALCULATION ---
                    // Calculate the total offset *of the icon's center* relative to the parent's center in PIXELS.
                    // The position is determined by the scaled grid offset plus the pan offset plus parallax.
                    val totalOffsetX_px = (gridOffsetX * dynamicScaleFactor) + panOffsetX + parallaxOffsetX
                    val totalOffsetY_px = (gridOffsetY * dynamicScaleFactor) + panOffsetY + parallaxOffsetY

                    // Convert the total pixel offset to Dp for the .offset() modifier
                    val finalOffsetX_dp = with(density) { totalOffsetX_px.toDp() }
                    val finalOffsetY_dp = with(density) { totalOffsetY_px.toDp() }


                    // Position the app icon using the calculated Dp offset relative to the centered position
                    AppIcon(
                        app = app,
                        modifier = Modifier
                            .offset(x = finalOffsetX_dp, y = finalOffsetY_dp) // Offset from center
                            .scale(dynamicScale) // Scale around the (offset) center
                    )
                }
            }
        }
    }
}


@Composable
fun AppIcon(app: WatchApp, modifier: Modifier = Modifier) {
    // Add subtle hover animation for the selected icon
    val animatedElevation = remember { Animatable(0f) }
    val animatedIconScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Additional animation for interaction
    var isPressed by remember { mutableStateOf(false) }
    val animatedPressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )

    Box(
        modifier = modifier
            .size(50.dp)
            .scale(animatedPressScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        scope.launch {
                            animatedElevation.animateTo(
                                targetValue = 8f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                            animatedIconScale.animateTo(
                                targetValue = 1.1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        tryAwaitRelease()
                        isPressed = false
                        scope.launch {
                            animatedElevation.animateTo(0f)
                            animatedIconScale.animateTo(1f)
                        }
                    }
                )
            }
            .clip(CircleShape)
            .background(app.backgroundColor)
            .shadow(
                elevation = animatedElevation.value.dp,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.scale(animatedIconScale.value),
            contentAlignment = Alignment.Center
        ) {
            if (app.name == "Activity") {
                ActivityRings(modifier = Modifier.size(40.dp))
            } else if (app.displayText != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = app.displayText,
                        color = Color.Black,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    if (app.displayDate != null) {
                        Text(
                            text = app.displayDate,
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                app.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = app.name,
                        tint = app.iconTint ?: Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Activity rings custom composable
@Composable
fun ActivityRings(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(40.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val strokeWidth = size.width * 0.15f

        // Outer ring (red)
        drawArc(
            color = Color(0xFFFF3B30),
            startAngle = -90f,
            sweepAngle = 330f, // 330 degrees = 110% completion
            useCenter = false,
            topLeft = Offset(centerX - size.width * 0.4f, centerY - size.width * 0.4f),
            size = Size(size.width * 0.8f, size.height * 0.8f),
            style = Stroke(width = strokeWidth)
        )

        // Middle ring (green)
        drawArc(
            color = Color(0xFF4CD964),
            startAngle = -90f,
            sweepAngle = 270f, // 270 degrees = 75% completion
            useCenter = false,
            topLeft = Offset(centerX - size.width * 0.3f, centerY - size.width * 0.3f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            style = Stroke(width = strokeWidth)
        )

        // Inner ring (blue)
        drawArc(
            color = Color(0xFF007AFF),
            startAngle = -90f,
            sweepAngle = 180f, // 180 degrees = 50% completion
            useCenter = false,
            topLeft = Offset(centerX - size.width * 0.2f, centerY - size.width * 0.2f),
            size = Size(size.width * 0.4f, size.height * 0.4f),
            style = Stroke(width = strokeWidth)
        )
    }
}

data class WatchApp(
    val name: String,
    val icon: ImageVector? = null,
    val backgroundColor: Color,
    val iconTint: Color? = Color.White,
    val displayText: String? = null,
    val displayDate: String? = null,
    val gridX: Float = 0f,
    val gridY: Float = 0f
)

fun generateAppleWatchApps(): List<WatchApp> {
    // Icons from the image, using available Material icons
    val icons = listOf(
        WatchApp("Phone", Icons.Default.Phone, Color(0xFF4CD964)),
        WatchApp("Mail", Icons.Default.Settings, Color(0xFF2196F3)),
        WatchApp("Messages", Icons.Default.Lock, Color(0xFF4CD964)),
        WatchApp("Breathe", Icons.Default.Place, Color(0xFF03DAC5)),
        // Center icon (activity rings)
        WatchApp("Activity", null, Color(0xFF000000), null, null, null, 0f, 0f),
        WatchApp("Workout", Icons.Default.ThumbUp, Color(0xFFCCFF00)),
        WatchApp("Settings", Icons.Default.Settings, Color(0xFFBBBBBB)),
        WatchApp("Timer", Icons.Default.Build, Color(0xFFFF9800)),
        WatchApp("Calendar", null, Color.White, Color.Black, "Mon", "10"),
        WatchApp("Stopwatch", Icons.Default.Info, Color(0xFFFF9800)),
        WatchApp("World Clock", Icons.Default.CheckCircle, Color(0xFFFF9800)),
        WatchApp("Maps", Icons.Default.PlayArrow, Color(0xFF43CD6B)),
        WatchApp("Weather", Icons.Default.Warning, Color(0xFF03A9F4)),
        WatchApp("Alarm", Icons.Default.DateRange, Color(0xFFFF9800)),
        WatchApp("Compass", Icons.Default.Person, Color(0xFFEF5350)),
        WatchApp("Digital Crown", Icons.Default.ArrowDropDown, Color(0xFF03A9F4)),
        WatchApp("App List", null, Color.White, Color.Black, null, "•••")
    )

    // Build the position map for apps
    val positionedApps = mutableListOf<WatchApp>()

    // Always add Activity at center
    positionedApps.add(icons[4])

    // Position the first ring (6 apps)
    val firstRing = listOf(0, 1, 2, 5, 7, 8)
    for (i in 0 until 6) {
        val angle = i * (2 * PI / 6)
        val position = Pair(
            (cos(angle) * 1.0f).toFloat(),
            (sin(angle) * 1.0f).toFloat()
        )
        positionedApps.add(icons[firstRing[i]].copy(gridX = position.first, gridY = position.second))
    }

    // Position the second ring (remaining apps)
    val secondRing = listOf(3, 6, 9, 10, 11, 12, 13, 14, 15, 16)
    for (i in 0 until secondRing.size) {
        val angle = i * (2 * PI / secondRing.size)
        val position = Pair(
            (cos(angle) * 2.0f).toFloat(),
            (sin(angle) * 2.0f).toFloat()
        )
        positionedApps.add(icons[secondRing[i]].copy(gridX = position.first, gridY = position.second))
    }

    return positionedApps
}

fun generateHoneycombPositions(rings: Int): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()

    // Center position is not included as it's special

    // First ring (6 positions)
    val firstRingRadius = 1.0f
    for (i in 0 until 6) {
        val angle = i * (2 * PI / 6)
        positions.add(Pair(
            (firstRingRadius * cos(angle)).toFloat(),
            (firstRingRadius * sin(angle)).toFloat()
        ))
    }

    // Additional rings
    for (ring in 2..rings) {
        val ringRadius = ring.toFloat()
        for (i in 0 until ring * 6) {
            val angle = (i * (2 * PI / (ring * 6))) + (PI / (ring * 6))
            positions.add(Pair(
                (ringRadius * cos(angle)).toFloat(),
                (ringRadius * sin(angle)).toFloat()
            ))
        }
    }

    return positions
}