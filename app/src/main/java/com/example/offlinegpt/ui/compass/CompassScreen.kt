package com.example.offlinegpt.ui.compass

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.offlinegpt.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    onBack: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
    viewModel: CompassViewModel = hiltViewModel()
) {
    val azimuth by viewModel.azimuth.collectAsState()
    val sunAzimuth by viewModel.sunAzimuth.collectAsState()
    val aiLocationInfo by chatViewModel.aiLocationInfo.collectAsState()
    var normalizedAzimuth2 = remember { mutableStateOf(0f) }
    val directionShort = remember { mutableStateOf("N") }
    val animatedAzimuth by animateFloatAsState(targetValue = azimuth, label = "Compass Rotation")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compass", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { chatViewModel.askWhereAmI(  "" + normalizedAzimuth2.value + " " + directionShort.value + ". tell me if i can see the SUN now. Where is SUN now from the view of mine?") }) {
                        Text("Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                aiLocationInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 150.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = info,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val normalizedAzimuth = (azimuth + 360) % 360
                normalizedAzimuth2.value = normalizedAzimuth
                directionShort.value = when {
                    normalizedAzimuth >= 337.5 || normalizedAzimuth < 22.5 -> "N"
                    normalizedAzimuth >= 22.5 && normalizedAzimuth < 67.5 -> "NE"
                    normalizedAzimuth >= 67.5 && normalizedAzimuth < 112.5 -> "E"
                    normalizedAzimuth >= 112.5 && normalizedAzimuth < 157.5 -> "SE"
                    normalizedAzimuth >= 157.5 && normalizedAzimuth < 202.5 -> "S"
                    normalizedAzimuth >= 202.5 && normalizedAzimuth < 247.5 -> "SW"
                    normalizedAzimuth >= 247.5 && normalizedAzimuth < 292.5 -> "W"
                    normalizedAzimuth >= 292.5 && normalizedAzimuth < 337.5 -> "NW"
                    else -> "N"
                }

                Canvas(modifier = Modifier.size(320.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val outerRadius = size.width / 2
                    val innerCircleRadius = outerRadius * 0.45f
                    val dialRadius = outerRadius * 0.95f

                    // 1. Draw rotating dial
                    rotate(degrees = animatedAzimuth, pivot = center) {
                        // Outer dark rim
                        drawCircle(
                            color = Color(0xFF1A1A1A),
                            radius = dialRadius,
                            center = center
                        )
                        
                        // Degree markings
                        val degreeTextPaint = Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 10.sp.toPx()
                            textAlign = Paint.Align.CENTER
                        }

                        for (i in 0 until 360 step 2) {
                            val angle = Math.toRadians(i.toDouble() - 90)
                            val startLen = if (i % 10 == 0) 15.dp.toPx() else 8.dp.toPx()
                            val start = Offset(
                                (center.x + (dialRadius - startLen) * Math.cos(angle)).toFloat(),
                                (center.y + (dialRadius - startLen) * Math.sin(angle)).toFloat()
                            )
                            val end = Offset(
                                (center.x + dialRadius * Math.cos(angle)).toFloat(),
                                (center.y + dialRadius * Math.sin(angle)).toFloat()
                            )
                            drawLine(Color.White.copy(alpha = 0.6f), start, end, strokeWidth = 1.dp.toPx())

                            if (i % 10 == 0) {
                                val textPos = Offset(
                                    (center.x + (dialRadius - 25.dp.toPx()) * Math.cos(angle)).toFloat(),
                                    (center.y + (dialRadius - 25.dp.toPx()) * Math.sin(angle)).toFloat()
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    i.toString(),
                                    textPos.x,
                                    textPos.y + 4.dp.toPx(),
                                    degreeTextPaint
                                )
                            }
                        }

                        // Cardinal Points (N, S, E, W, NE, NW, SE, SW)
                        val cardinalPaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24.sp.toPx()
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        val northPaint = Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 24.sp.toPx()
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        val interCardinalPaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 16.sp.toPx()
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }

                        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                        points.forEachIndexed { index, label ->
                            val angle = Math.toRadians(index * 45.0 - 90)
                            val dist = dialRadius - 55.dp.toPx()
                            val x = (center.x + dist * Math.cos(angle)).toFloat()
                            val y = (center.y + dist * Math.sin(angle)).toFloat()
                            
                            val paint = when(label) {
                                "N" -> northPaint
                                "NE", "NW", "SE", "SW" -> interCardinalPaint
                                else -> cardinalPaint
                            }
                            
                            drawContext.canvas.nativeCanvas.drawText(label, x, y + 8.dp.toPx(), paint)
                        }

                        // Primary points (Large)
                        for (i in 0 until 4) {
                            val angle = Math.toRadians(i * 90.0 - 90)
                            val tip = Offset(
                                (center.x + (dialRadius - 80.dp.toPx()) * Math.cos(angle)).toFloat(),
                                (center.y + (dialRadius - 80.dp.toPx()) * Math.sin(angle)).toFloat()
                            )
                            val base1 = Offset(
                                (center.x + innerCircleRadius * Math.cos(angle - 0.2)).toFloat(),
                                (center.y + innerCircleRadius * Math.sin(angle - 0.2)).toFloat()
                            )
                            val base2 = Offset(
                                (center.x + innerCircleRadius * Math.cos(angle + 0.2)).toFloat(),
                                (center.y + innerCircleRadius * Math.sin(angle + 0.2)).toFloat()
                            )
                            
                            drawPath(
                                Path().apply {
                                    moveTo(center.x, center.y)
                                    lineTo(base1.x, base1.y)
                                    lineTo(tip.x, tip.y)
                                    close()
                                },
                                color = Color.LightGray.copy(alpha = 0.3f)
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(center.x, center.y)
                                    lineTo(base2.x, base2.y)
                                    lineTo(tip.x, tip.y)
                                    close()
                                },
                                color = Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        
                        // Sun position
                        rotate(degrees = sunAzimuth, pivot = center) {
                            drawCircle(
                                color = Color(0xFFFFD700),
                                radius = 8.dp.toPx(),
                                center = Offset(center.x, center.y - dialRadius - 15.dp.toPx())
                            )
                        }
                    }

                    // 2. Fixed Elements (Top Indicator & Center Display)
                    
                    // Top white lubber line
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x, center.y - dialRadius - 10.dp.toPx()),
                        end = Offset(center.x, center.y - innerCircleRadius),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Center Circle (Dark background with border)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2A2A2A), Color(0xFF121212)),
                            center = center,
                            radius = innerCircleRadius
                        ),
                        radius = innerCircleRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = innerCircleRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Current heading text in center
                    val bigTextPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 56.sp.toPx()
                        typeface = Typeface.DEFAULT
                        textAlign = Paint.Align.CENTER
                    }
                    val subTextPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 32.sp.toPx()
                        typeface = Typeface.DEFAULT
                        textAlign = Paint.Align.CENTER
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        normalizedAzimuth.toInt().toString(),
                        center.x,
                        center.y + 10.dp.toPx(),
                        bigTextPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        directionShort.value,
                        center.x,
                        center.y + 45.dp.toPx(),
                        subTextPaint
                    )
                }
            }
        }
    }
}
