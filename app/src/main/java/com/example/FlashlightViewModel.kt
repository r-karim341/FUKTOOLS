package com.example

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FlashlightViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    // Support flags
    private val _isPhysicalFlashSupported = MutableStateFlow(false)
    val isPhysicalFlashSupported: StateFlow<Boolean> = _isPhysicalFlashSupported.asStateFlow()

    private val _isPhysicalBrightnessSupported = MutableStateFlow(false)
    val isPhysicalBrightnessSupported: StateFlow<Boolean> = _isPhysicalBrightnessSupported.asStateFlow()

    private val _maxPhysicalBrightness = MutableStateFlow(1)
    val maxPhysicalBrightness: StateFlow<Int> = _maxPhysicalBrightness.asStateFlow()

    // Interactive States
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _flashlightBrightness = MutableStateFlow(1.0f) // 0.1f to 1.0f
    val flashlightBrightness: StateFlow<Float> = _flashlightBrightness.asStateFlow()

    private val _isStrobeActive = MutableStateFlow(false)
    val isStrobeActive: StateFlow<Boolean> = _isStrobeActive.asStateFlow()

    private val _strobeFrequency = MutableStateFlow(5.0f) // 1.0Hz to 25.0Hz
    val strobeFrequency: StateFlow<Float> = _strobeFrequency.asStateFlow()

    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private val _sosMessage = MutableStateFlow("SOS")
    val sosMessage: StateFlow<String> = _sosMessage.asStateFlow()

    // Screen light (soft lantern) states
    private val _isScreenLightActive = MutableStateFlow(false)
    val isScreenLightActive: StateFlow<Boolean> = _isScreenLightActive.asStateFlow()

    private val _screenLightColor = MutableStateFlow(Color(0xFFFFF3CD)) // default warm light
    val screenLightColor: StateFlow<Color> = _screenLightColor.asStateFlow()

    private val _screenBrightness = MutableStateFlow(0.8f) // 0.1f to 1.0f
    val screenBrightness: StateFlow<Float> = _screenBrightness.asStateFlow()

    // Visual tick stream to flash UI components in sync with strobe rate
    private val _strobeIndicatorTick = MutableStateFlow(false)
    val strobeIndicatorTick: StateFlow<Boolean> = _strobeIndicatorTick.asStateFlow()

    private val _hardwareErrorMessage = MutableStateFlow<String?>(null)
    val hardwareErrorMessage: StateFlow<String?> = _hardwareErrorMessage.asStateFlow()

    private var strobeJob: Job? = null
    private var sosJob: Job? = null

    init {
        initCamera()
    }

    private fun initCamera() {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val manager = cameraManager
            if (manager == null) {
                _hardwareErrorMessage.value = "Camera service is not available on this device."
                return
            }

            // Find camera layout with a physical LED flash
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    _isPhysicalFlashSupported.value = true
                    
                    // Check if physical brightness alteration is possible (API 33+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        if (maxLevel > 1) {
                            _isPhysicalBrightnessSupported.value = true
                            _maxPhysicalBrightness.value = maxLevel
                        }
                    }
                    break
                }
            }

            // Fallback to first camera if no back-facing camera with flash was found
            if (cameraId == null && manager.cameraIdList.isNotEmpty()) {
                cameraId = manager.cameraIdList[0]
            }
        } catch (e: Exception) {
            Log.e("FlashlightVM", "Failed to initialize camera characteristics: ${e.message}")
            _hardwareErrorMessage.value = "Hardware initialization error: ${e.localizedMessage}"
        }
    }

    fun toggleFlashlight() {
        // Turn off SOS and Strobe modes when manual toggle is clicked
        stopSos()
        stopStrobe()

        val newState = !_isFlashlightOn.value
        _isFlashlightOn.value = newState
        setPhysicalTorch(newState, _flashlightBrightness.value)
    }

    fun updateFlashlightBrightness(level: Float) {
        _flashlightBrightness.value = level
        if (_isFlashlightOn.value) {
            setPhysicalTorch(true, level)
        }
    }

    fun toggleStrobe() {
        stopSos()
        _isFlashlightOn.value = false

        if (_isStrobeActive.value) {
            stopStrobe()
        } else {
            _isStrobeActive.value = true
            startStrobe()
        }
    }

    fun updateStrobeFrequency(freq: Float) {
        _strobeFrequency.value = freq
        if (_isStrobeActive.value) {
            // Restart strobe with the updated interval
            startStrobe()
        }
    }

    fun toggleSos() {
        stopStrobe()
        _isFlashlightOn.value = false

        if (_isSosActive.value) {
            stopSos()
        } else {
            _isSosActive.value = true
            startSos()
        }
    }

    fun updateSosMessage(message: String) {
        val sanitized = message.trim().uppercase()
        if (sanitized.isNotEmpty()) {
            _sosMessage.value = sanitized
            if (_isSosActive.value) {
                startSos() // restart with new sequence
            }
        }
    }

    fun toggleScreenLight() {
        val newState = !_isScreenLightActive.value
        _isScreenLightActive.value = newState
        
        // Turn off physical elements if screen light active to conserve battery & deliver clean lantern experience
        if (newState) {
            stopStrobe()
            stopSos()
            _isFlashlightOn.value = false
            setPhysicalTorch(false)
        }
    }

    fun setScreenLightColor(color: Color) {
        _screenLightColor.value = color
    }

    fun updateScreenBrightness(brightness: Float) {
        _screenBrightness.value = brightness
    }

    private fun setPhysicalTorch(enabled: Boolean, level: Float = 1.0f) {
        val manager = cameraManager
        val id = cameraId
        if (manager == null || id == null || !_isPhysicalFlashSupported.value) {
            return
        }
        try {
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _isPhysicalBrightnessSupported.value) {
                    val maxLevel = _maxPhysicalBrightness.value
                    // Map 0.1f..1.0f to 1..maxLevel
                    val targetStrength = (level * maxLevel).coerceIn(1f, maxLevel.toFloat()).toInt()
                    manager.turnOnTorchWithStrengthLevel(id, targetStrength)
                } else {
                    manager.setTorchMode(id, true)
                }
            } else {
                manager.setTorchMode(id, false)
            }
        } catch (e: Exception) {
            Log.e("FlashlightVM", "Error writing torch mode: ${e.message}")
            _hardwareErrorMessage.value = "Hardware control interrupted: ${e.localizedMessage}"
            
            // Fallback attempt
            try {
                if (enabled) {
                    manager.setTorchMode(id, true)
                } else {
                    manager.setTorchMode(id, false)
                }
            } catch (fallbackEx: Exception) {
                Log.e("FlashlightVM", "Fallback torch toggle failed: ${fallbackEx.message}")
            }
        }
    }

    private fun startStrobe() {
        strobeJob?.cancel()
        strobeJob = viewModelScope.launch {
            try {
                while (_isStrobeActive.value) {
                    val frequency = _strobeFrequency.value
                    // Period in ms
                    val periodMs = (1000 / frequency).toLong()
                    val halfPeriod = (periodMs / 2).coerceAtLeast(10L)

                    // Flash ON
                    setPhysicalTorch(true, _flashlightBrightness.value)
                    _strobeIndicatorTick.value = true
                    delay(halfPeriod)

                    // Flash OFF
                    setPhysicalTorch(false)
                    _strobeIndicatorTick.value = false
                    delay(halfPeriod)
                }
            } finally {
                setPhysicalTorch(false)
                _strobeIndicatorTick.value = false
            }
        }
    }

    private fun stopStrobe() {
        _isStrobeActive.value = false
        strobeJob?.cancel()
        strobeJob = null
        setPhysicalTorch(false)
        _strobeIndicatorTick.value = false
    }

    private fun startSos() {
        sosJob?.cancel()
        sosJob = viewModelScope.launch {
            try {
                val morseAlphabet = mapOf(
                    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
                    'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
                    'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
                    'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
                    'Y' to "-.--", 'Z' to "--..", '1' to ".----", '2' to "..---", '3' to "...--",
                    '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
                    '9' to "----.", '0' to "-----"
                )

                // Base timing unit (200ms)
                val dotDuration = 200L
                val dashDuration = 600L
                val partDelay = 200L // space between dots & dashes of same letter
                val letterDelay = 600L // space between letters
                val wordDelay = 1400L // space between words

                while (_isSosActive.value) {
                    val phrase = _sosMessage.value
                    
                    for (i in phrase.indices) {
                        if (!_isSosActive.value) break

                        val char = phrase[i]
                        if (char == ' ') {
                            delay(wordDelay)
                            continue
                        }

                        val pattern = morseAlphabet[char]
                        if (pattern != null) {
                            for (j in pattern.indices) {
                                if (!_isSosActive.value) break

                                val element = pattern[j]
                                val duration = if (element == '.') dotDuration else dashDuration
                                
                                setPhysicalTorch(true, _flashlightBrightness.value)
                                _strobeIndicatorTick.value = true
                                delay(duration)
                                
                                setPhysicalTorch(false)
                                _strobeIndicatorTick.value = false
                                delay(partDelay)
                            }
                            delay(letterDelay)
                        }
                    }
                    // Delay between repeating the whole message
                    delay(2000L)
                }
            } finally {
                setPhysicalTorch(false)
                _strobeIndicatorTick.value = false
            }
        }
    }

    private fun stopSos() {
        _isSosActive.value = false
        sosJob?.cancel()
        sosJob = null
        setPhysicalTorch(false)
        _strobeIndicatorTick.value = false
    }

    fun clearHardwareError() {
        _hardwareErrorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStrobe()
        stopSos()
        setPhysicalTorch(false)
    }
}
