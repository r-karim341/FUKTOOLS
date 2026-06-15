package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.FlashlightViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FlashlightScreen(
    viewModel: FlashlightViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Observe state flows from viewModel
    val isPhysicalFlashSupported by viewModel.isPhysicalFlashSupported.collectAsState()
    val isPhysicalBrightnessSupported by viewModel.isPhysicalBrightnessSupported.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val flashlightBrightness by viewModel.flashlightBrightness.collectAsState()
    
    val isStrobeActive by viewModel.isStrobeActive.collectAsState()
    val strobeFrequency by viewModel.strobeFrequency.collectAsState()
    val strobeTick by viewModel.strobeIndicatorTick.collectAsState()

    val isSosActive by viewModel.isSosActive.collectAsState()
    val sosMessage by viewModel.sosMessage.collectAsState()

    val isScreenLightActive by viewModel.isScreenLightActive.collectAsState()
    val screenLightColor by viewModel.screenLightColor.collectAsState()
    val screenBrightness by viewModel.screenBrightness.collectAsState()

    val hardwareErrorMessage by viewModel.hardwareErrorMessage.collectAsState()

    // Screen selection tab (0 = Physical Led Flashlight, 1 = Soft Screen Lantern)
    var selectedTab by remember { mutableStateOf(0) }

    // Dynamic Permission check & state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Vibrator Setup for subtle tactile haptic responses
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    // Interactive vibration feedback triggers
    // type: 0 = standard toggle (e.g. settings tick), 1 = subtle slider increment, 2 = master hardware heavy switch
    val triggerVibration: (Int) -> Unit = remember(vibrator) {
        { type ->
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val predefinedEffect = when (type) {
                            1 -> android.os.VibrationEffect.EFFECT_TICK
                            2 -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                            else -> android.os.VibrationEffect.EFFECT_CLICK
                        }
                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(predefinedEffect))
                    } else {
                        val durationMs = when (type) {
                            1 -> 12L
                            2 -> 45L
                            else -> 25L
                        }
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(durationMs)
                    }
                }
            } catch (e: Exception) {
                // Fail-safe protection on emulator & unsupported configs
            }
        }
    }

    // Trigger window brightness changes when screen lantern is active
    DisposableEffect(screenBrightness, isScreenLightActive, selectedTab) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null && isScreenLightActive && selectedTab == 1) {
            val layoutParams = window.attributes
            val originalBrightness = layoutParams.screenBrightness
            
            // Override screen brightness to target user-selected slider level
            layoutParams.screenBrightness = screenBrightness.coerceAtLeast(0.02f)
            window.attributes = layoutParams
            
            onDispose {
                // Restore to system auto brightness when screen lantern is closed or disposed
                layoutParams.screenBrightness = originalBrightness
                window.attributes = layoutParams
            }
        } else {
            onDispose {}
        }
    }

    // Custom modern Dark Cyber theme aligning with the "Vibrant Palette"
    val darkCharcoal = Color(0xFF0F1115)
    val gridLineColor = Color(0xFF2D3139)
    val neonAmber = Color(0xFFFBBF24) // Theme vibrant amber-400
    val neonBlue = Color(0xFF38BDF8)  // Theme secondary high-contrast sky-400
    val surfaceMetal = Color(0xFF1C1F26) // Card container bg
    val textSoftGray = Color(0xFF909196) // Text secondary/helper
    val textLight = Color(0xFFE2E2E6)    // Text primary highlight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkCharcoal)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Futuristic cyber grid overlay backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 40.dp.toPx()
            val stepY = 40.dp.toPx()
            for (x in 0..size.width.toInt() step stepX.toInt()) {
                drawLine(
                    color = gridLineColor.copy(alpha = 0.12f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step stepY.toInt()) {
                drawLine(
                    color = gridLineColor.copy(alpha = 0.12f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        // --- FULLSCREEN LANTERN LIGHT PAGE OVERLAY ---
        AnimatedVisibility(
            visible = (isScreenLightActive && selectedTab == 1),
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenLightColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Single tap on screen anywhere to close soft screen lantern cleanly
                        triggerVibration(2) // beefy click
                        viewModel.toggleScreenLight()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "SCREEN LIGHT ACTIVE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isLightColor(screenLightColor)) Color.Black else Color.White,
                            letterSpacing = 2.sp
                        ),
                        modifier = Modifier.testTag("lantern_indicator")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap anywhere to switch off",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = (if (isLightColor(screenLightColor)) Color.Black else Color.White).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }

        // --- MAIN DASHBOARD INTERFACE ---
        // Hidden when Screen Lantern is active and fullscreen, so users get max uninterrupted screen glow area!
        if (!(isScreenLightActive && selectedTab == 1)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Brand Title & Subtitle with Settings Button matching Vibrant Palette HTML
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Flashlight",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.testTag("app_brand_title")
                        )
                        Text(
                            text = "SYSTEM TOOL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSoftGray,
                            letterSpacing = 2.sp
                        )
                    }
                    // Decorative/Tactile Hamburger Menu Button from the Vibrant design HTML
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(surfaceMetal)
                            .border(1.dp, gridLineColor, RoundedCornerShape(16.dp))
                            .clickable { /* decorative active state UI feedback */ }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(textLight))
                            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(textLight))
                            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(textLight))
                        }
                    }
                }

                // Tab Selectors styled in Vibrant style (0 = Physical Dual LED, 1 = Screen soft Glow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(surfaceMetal)
                        .border(1.dp, gridLineColor, RoundedCornerShape(26.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selectedTab == 0) neonAmber else Color.Transparent)
                            .clickable {
                                if (selectedTab != 0) {
                                    selectedTab = 0
                                    triggerVibration(0)
                                }
                            }
                            .testTag("tab_physical_led"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LED POWER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = if (selectedTab == 0) Color(0xFF1E1B0B) else textLight
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selectedTab == 1) neonBlue else Color.Transparent)
                            .clickable {
                                if (selectedTab != 1) {
                                    selectedTab = 1
                                    triggerVibration(0)
                                }
                            }
                            .testTag("tab_screen_glow"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LANTERN MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = if (selectedTab == 1) Color(0xFF0F172A) else textLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Error Warning System Messages (graceful fallbacks)
                hardwareErrorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1119)),
                        border = BorderStroke(1.dp, Color.Red),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        onClick = { viewModel.clearHardwareError() }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Hardware Alert",
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HARDWARE UTILITY ADVISORY",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                                Text(
                                    text = message,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Check and prompt permissions beautifully if using Physical mode and permission is missing
                if (selectedTab == 0 && !hasCameraPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information Required",
                                tint = neonBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "CAMERA PERMISSION REQUIRED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Some phone manufacturers lock the LED flash module behind camera capabilities. Grant permission below to activate.",
                                fontSize = 11.sp,
                                color = textSoftGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_permission_button")
                            ) {
                                Text("GRANT PERMISSION", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }

                // --- TAB 0: PHYSICAL DUAL HARDWARE LIGHTS CONTROLS ---
                if (selectedTab == 0) {
                    // Tactile Master Power Switch Glow Module with Amber/Mode highlight behind it
                    Box(
                        modifier = Modifier
                            .size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient radial glow from Vibrant layout (absolute -inset-12 bg-amber-400/20 blur-3xl)
                        val infiniteTransition = rememberInfiniteTransition(label = "powerPulse")
                        val outerGlowScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = if (isFlashlightOn || strobeTick || isSosActive) 1.2f else 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAnimation"
                        )
                        val outerGlowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = if (isFlashlightOn || strobeTick || isSosActive) 0.45f else 0.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alphaAnimation"
                        )

                        // Ambient backdrop glow using Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val activeColor = when {
                                isSosActive -> Color.Red
                                isStrobeActive -> neonBlue
                                else -> neonAmber
                            }
                            drawCircle(
                                color = activeColor,
                                radius = 96.dp.toPx() * outerGlowScale,
                                alpha = outerGlowAlpha * 0.25f
                            )
                        }

                        // Main Tactile Button from Vibrant HTML:
                        // "relative w-56 h-56 rounded-full bg-[#1C1F26] border-[8px] border-[#2D3139] shadow-[0_0_80px_rgba(251,191,36,0.15)]"
                        Box(
                            modifier = Modifier
                                .size(192.dp)
                                .shadow(
                                    elevation = if (isFlashlightOn || strobeTick || isSosActive) 24.dp else 4.dp,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .background(surfaceMetal)
                                .border(
                                    width = 8.dp,
                                    color = gridLineColor,
                                    shape = CircleShape
                                )
                                .clickable {
                                    triggerVibration(2)
                                    viewModel.toggleFlashlight()
                                }
                                .testTag("power_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Inner visual indicator ring: "w-24 h-24 rounded-full border-4 border-amber-400"
                                val indicatorColor = when {
                                    isSosActive -> Color.Red
                                    isStrobeActive -> if (strobeTick) neonBlue else neonBlue.copy(alpha = 0.4f)
                                    isFlashlightOn -> neonAmber
                                    else -> gridLineColor
                                }
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .border(4.dp, indicatorColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Vertical Power Pin line: "w-2 h-10 bg-amber-400 rounded-full"
                                    Box(
                                        modifier = Modifier
                                            .size(width = 6.dp, height = 30.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isSosActive) "SOS" else if (isStrobeActive) "STROBE" else if (isFlashlightOn) "ON" else "OFF",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = indicatorColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SECTION A: Physical LED Brightness sliders
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BRIGHTNESS强度",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSoftGray,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${(flashlightBrightness * 100).toInt()}%",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = neonAmber
                                )
                            }

                            // Dynamic disclaimer tag of physical capability
                            if (isPhysicalBrightnessSupported) {
                                Text(
                                    text = "✓ Precise multi-level hardware strength supported",
                                    fontSize = 10.sp,
                                    color = Color(0xFF22C55E).copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                            } else {
                                Text(
                                    text = "Device mimics level transitions via hardware timers. Physical LED operates at maximum safety lumen standard.",
                                    fontSize = 10.sp,
                                    color = textSoftGray.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            var lastLedPercent by remember { mutableStateOf((flashlightBrightness * 100).toInt()) }
                            Slider(
                                value = flashlightBrightness,
                                onValueChange = {
                                    val pct = (it * 100).toInt()
                                    if (pct != lastLedPercent) {
                                        lastLedPercent = pct
                                        triggerVibration(1)
                                    }
                                    viewModel.updateFlashlightBrightness(it)
                                },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = neonAmber,
                                    activeTrackColor = neonAmber,
                                    inactiveTrackColor = darkCharcoal
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("brightness_slider")
                            )
                        }
                    }

                    // SECTION B: Interactive Strobe Speed Matrix Controls
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, if (isStrobeActive) neonBlue.copy(alpha = 0.5f) else gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isStrobeActive) neonBlue else textSoftGray.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "STROBE MODULATOR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textLight,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Switch(
                                    checked = isStrobeActive,
                                    onCheckedChange = {
                                        triggerVibration(0)
                                        viewModel.toggleStrobe()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = neonBlue,
                                        checkedTrackColor = neonBlue.copy(alpha = 0.3f),
                                        uncheckedThumbColor = textSoftGray,
                                        uncheckedTrackColor = darkCharcoal
                                    ),
                                    modifier = Modifier.testTag("strobe_toggle")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frequency Speed",
                                    fontSize = 11.sp,
                                    color = textSoftGray
                                )
                                Text(
                                    text = "${String.format("%.1f", strobeFrequency)} Hz",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = neonBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            var lastStrobeHz by remember { mutableStateOf((strobeFrequency * 2).toInt()) }
                            Slider(
                                value = strobeFrequency,
                                onValueChange = {
                                    val hz = (it * 2).toInt()
                                    if (hz != lastStrobeHz) {
                                        lastStrobeHz = hz
                                        triggerVibration(1)
                                    }
                                    viewModel.updateStrobeFrequency(it)
                                },
                                valueRange = 1.0f..25.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = neonBlue,
                                    activeTrackColor = neonBlue,
                                    inactiveTrackColor = darkCharcoal
                                ),
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("strobe_frequency_slider")
                            )

                            // Fast selection frequency buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val frequencies = listOf(2.0f, 5.0f, 10.0f, 18.0f, 25.0f)
                                frequencies.forEach { freq ->
                                    val isSelected = strobeFrequency == freq
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) neonBlue.copy(alpha = 0.2f)
                                                else darkCharcoal
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) neonBlue else gridLineColor,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                if (!isSelected) {
                                                    triggerVibration(0)
                                                    viewModel.updateStrobeFrequency(freq)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${freq.toInt()}Hz",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) neonBlue else textSoftGray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION C: Morse Code & SOS SOS Controller
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, if (isSosActive) Color.Red.copy(alpha = 0.5f) else gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isSosActive) Color.Red else textSoftGray.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "RESCUE BEACON",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textLight,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Switch(
                                    checked = isSosActive,
                                    onCheckedChange = {
                                        triggerVibration(0)
                                        viewModel.toggleSos()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Red,
                                        checkedTrackColor = Color.Red.copy(alpha = 0.3f),
                                        uncheckedThumbColor = textSoftGray,
                                        uncheckedTrackColor = darkCharcoal
                                    ),
                                    modifier = Modifier.testTag("sos_toggle")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Customize the Morse signal text:",
                                fontSize = 11.sp,
                                color = textSoftGray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Interactive Text Input for unique Morse words!
                            var textInput by remember { mutableStateOf(sosMessage) }
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = {
                                    textInput = it
                                    viewModel.updateSosMessage(it)
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    fontSize = 13.sp
                                ),
                                placeholder = { Text("E.g. HELP or SOS", color = textSoftGray.copy(alpha = 0.5f)) },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = neonAmber,
                                    unfocusedBorderColor = gridLineColor,
                                    focusedContainerColor = darkCharcoal,
                                    unfocusedContainerColor = darkCharcoal
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sos_message_input")
                            )

                            // Quick emergency template phrases
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                val emergencyTemplates = listOf("SOS", "HELP", "OK", "FIND ME")
                                emergencyTemplates.forEach { term ->
                                    val isSelected = sosMessage == term
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color.Red.copy(alpha = 0.15f)
                                                else darkCharcoal
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Red else gridLineColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (!isSelected) {
                                                    triggerVibration(0)
                                                    textInput = term
                                                    viewModel.updateSosMessage(term)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = term,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Red else textSoftGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- TAB 1: LANTERN MODE CONTROLS (SCREEN BULB) ---
                if (selectedTab == 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SCREEN GLOW EMITTER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = neonBlue,
                                letterSpacing = 1.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Text(
                                text = "Provides an optimized soft, ambient glow of chooseable spectrum colors, preventing dynamic eye strain compared to harsh LED lamps.",
                                fontSize = 11.sp,
                                color = textSoftGray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                            )

                            // Master activate Glow Button
                            Button(
                                onClick = {
                                    triggerVibration(2)
                                    viewModel.toggleScreenLight()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isScreenLightActive) neonBlue else neonBlue.copy(alpha = 0.15f),
                                    contentColor = if (isScreenLightActive) Color.Black else neonBlue
                                ),
                                border = BorderStroke(1.dp, neonBlue),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("screen_lantern_activate_button")
                            ) {
                                Text(
                                    text = if (isScreenLightActive) "CLOSE SCREEN GLOW" else "LAUNCH SCREEN GLOW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Screen Brightness slider card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GLOW INTENSITY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textLight,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${(screenBrightness * 100).toInt()}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = neonBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Directly alters device backlight window parameters to deliver maximum visual range when active.",
                                fontSize = 10.sp,
                                color = textSoftGray,
                                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                            )

                            var lastScreenPercent by remember { mutableStateOf((screenBrightness * 100).toInt()) }
                            Slider(
                                value = screenBrightness,
                                onValueChange = {
                                    val pct = (it * 100).toInt()
                                    if (pct != lastScreenPercent) {
                                        lastScreenPercent = pct
                                        triggerVibration(1)
                                    }
                                    viewModel.updateScreenBrightness(it)
                                },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = neonBlue,
                                    activeTrackColor = neonBlue,
                                    inactiveTrackColor = darkCharcoal
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lantern_brightness_slider")
                            )
                        }
                    }

                    // Section: Screen Light Color Spectrum
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceMetal),
                        border = BorderStroke(1.dp, gridLineColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = "COLOR GLOW PALETTE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textLight,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val glowColors = listOf(
                                NamedColor("WARM CANDLE", Color(0xFFFFF3CD)),
                                NamedColor("SOFT WHITE", Color(0xFFFFFFFF)),
                                NamedColor("COOL BLUE", Color(0xFF8FFBFF)),
                                NamedColor("NIGHT VISION RED", Color(0xFFFF5252)),
                                NamedColor("BEACON GREEN", Color(0xFF69F0AE)),
                                NamedColor("DISCO PURPLE", Color(0xFFE040FB))
                            )

                            glowColors.forEach { namedColor ->
                                val isSelected = screenLightColor == namedColor.color
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) Color.White.copy(alpha = 0.04f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            if (!isSelected) {
                                                triggerVibration(0)
                                                viewModel.setScreenLightColor(namedColor.color)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(namedColor.color)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) neonBlue else Color.White.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = namedColor.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) neonBlue else textSoftGray
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Footer matching exactly the Vibrant Palette:
                // <footer class="px-6 py-8 flex justify-center"><div class="flex items-center gap-2"><div class="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div><span>Battery: 92% • Optimized</span></div></footer>
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(alpha = pAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Battery: 92% • Optimized",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSoftGray
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// Helper models for Screen light
data class NamedColor(val name: String, val color: Color)

// Simple heuristic to determine if color is light (high luminance) for contrast text rendering
fun isLightColor(color: Color): Boolean {
    val r = color.red
    val g = color.green
    val b = color.blue
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance > 0.45f
}
