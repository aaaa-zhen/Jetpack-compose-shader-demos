package com.example.myapplication

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Preview
@Composable
fun TextSpringWithVelocity() {
    val animatedY = remember { Animatable(0f) }

    // Spring animation spec
    val springSpec = spring<Float>(
        dampingRatio = 0.5f,  // You can adjust the damping ratio for bounce
        stiffness = 200f     // You can adjust stiffness for speed of movement
    )

    // Apply the spring animation with initial velocity
    LaunchedEffect(Unit) {
        delay(2000)
        animatedY.animateTo(
            targetValue = 0f,  // The target vertical position (adjust as needed)
            initialVelocity = 2000f,  // This is the initial velocity to make the text move quickly
            animationSpec = springSpec
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "This is Spring Text With Velocity",
            modifier = Modifier
                .offset(y = animatedY.value.dp)  // Apply the animated vertical offset
                .padding(16.dp)  // Optional padding
        )
    }
}
