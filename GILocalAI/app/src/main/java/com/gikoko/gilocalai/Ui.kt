package com.gikoko.gilocalai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object GiColors {
    val bg = Color(0xFF04100A)
    val card = Color(0xFF0A1F14)
    val card2 = Color(0xFF0F2D1C)
    val line = Color(0xFF14502D)
    val neon = Color(0xFF2BFF88)
    val neonDim = Color(0xFF0FBF5E)
    val deep = Color(0xFF0B8A45)
    val gold = Color(0xFFD6AE55)
    val text = Color(0xFFE6FFEF)
    val muted = Color(0xFF7FA58D)
    val danger = Color(0xFFFF5C6C)
}

@Composable
fun GiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GiColors.neon,
            onPrimary = Color(0xFF02150A),
            secondary = GiColors.gold,
            background = GiColors.bg,
            onBackground = GiColors.text,
            surface = GiColors.card,
            onSurface = GiColors.text,
            surfaceVariant = GiColors.card2,
            onSurfaceVariant = GiColors.muted,
            outline = GiColors.line,
            error = GiColors.danger
        ),
        content = content
    )
}

/** Animated extruded 3D "GI" lettering (layered text + Y-axis rotation). */
@Composable
fun Gi3dText(modifier: Modifier = Modifier, fontSize: TextUnit = 96.sp, depth: Int = 14) {
    val tr = rememberInfiniteTransition(label = "gi3d")
    val angle by tr.animateFloat(
        initialValue = -24f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "angle"
    )
    val glow by tr.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glow"
    )
    val density = LocalDensity.current.density
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = angle
            cameraDistance = 16f * density
        },
        contentAlignment = Alignment.Center
    ) {
        // glow
        Text(
            "GI",
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            style = TextStyle(shadow = Shadow(GiColors.neon, Offset.Zero, 60f)),
            color = GiColors.neon.copy(alpha = 0.18f),
            modifier = Modifier.alpha(glow)
        )
        // extrusion layers
        for (i in depth downTo 1) {
            val t = i / depth.toFloat()
            Text(
                "GI",
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = lerp(Color(0xFF0B8A45), Color(0xFF021A0D), t),
                modifier = Modifier.offset(x = (i * 1.1f).dp, y = (i * 1.1f).dp)
            )
        }
        // front face
        Text(
            "GI",
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color(0xFFC8FFE0), Color(0xFF2BFF88), Color(0xFF0FBF5E)))
            )
        )
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2600)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF0B3D1E), GiColors.bg))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Gi3dText(fontSize = 110.sp)
            Text(
                "LOCAL AI",
                color = GiColors.gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(top = 36.dp)
            )
            Text(
                "Offline · Private · Always learning",
                color = GiColors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GiColors.card, RoundedCornerShape(16.dp))
            .border(1.dp, GiColors.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = GiColors.neon, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        content()
    }
}

@Composable
fun Gap(h: Int = 8) {
    Box(Modifier.height(h.dp))
}
