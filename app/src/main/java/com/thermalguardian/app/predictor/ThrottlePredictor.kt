package com.thermalguardian.app.predictor

import com.thermalguardian.app.collector.SensorDataCollector
import com.thermalguardian.app.data.model.MetricSample
import com.thermalguardian.app.data.model.RiskLevel
import com.thermalguardian.app.data.model.ThrottlePrediction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ThrottlePredictor
 *
 * Real-time trend-based thermal throttling predictor:
 * - Inputs: Live streams of Temperature (°C), FPS, and Battery Drain (%/min) from SensorDataCollector.
 * - Rolling Window: Maintains the last 15 seconds of readings.
 * - Multi-Mode Prediction:
 *   1. Primary Thermal Model: Uses OLS linear slope of temperature, FPS variance, and thermal status.
 *   2. Battery-Drain Fallback Model: If temperature sensors are unavailable, uses discharge rate (%/min)
 *      and FPS stability to estimate throttling risk.
 */
class ThrottlePredictor(
    private val windowDurationSeconds: Int = 15,
    private val criticalTempThreshold: Float = 43.5f,
    private val targetFps: Float = 60.0f
) {

    private data class Reading(
        val timestampMs: Long,
        val tempCelsius: Float,
        val fps: Float,
        val batteryDrainRate: Float,
        val thermalHeadroom: Float = -1f,
        val thermalStatus: Int = 0,
        val isTempSensorAvailable: Boolean = true
    )

    private val sampleWindow = ArrayDeque<Reading>(windowDurationSeconds * 2)

    private var tempEma: Float = 0f
    private var fpsEma: Float = 0f
    private val emaAlpha: Float = 0.25f

    // ==========================================
    // EXPOSED FLOWS
    // ==========================================
    private val _predictionFlow = MutableStateFlow(ThrottlePrediction())
    val predictionFlow: StateFlow<ThrottlePrediction> = _predictionFlow.asStateFlow()

    val riskLevelFlow: Flow<RiskLevel> = _predictionFlow
        .map { it.riskLevel }
        .distinctUntilChanged()

    fun attachSensorCollector(
        coroutineScope: CoroutineScope,
        sensorCollector: SensorDataCollector
    ): Job {
        return coroutineScope.launch {
            sensorCollector.sampleFlow.collect { sample ->
                processSample(sample)
            }
        }
    }

    @Synchronized
    fun processSample(sample: MetricSample): ThrottlePrediction {
        return processReading(
            tempCelsius = sample.tempCelsius,
            fps = sample.fps,
            batteryDrainRate = sample.batteryDrainRatePerMin,
            timestampMs = sample.timestampMs,
            thermalHeadroom = sample.thermalHeadroom,
            thermalStatus = sample.thermalStatus,
            isTempSensorAvailable = sample.isTempSensorAvailable
        )
    }

    @Synchronized
    fun processReading(
        tempCelsius: Float,
        fps: Float,
        batteryDrainRate: Float = 0f,
        timestampMs: Long = System.currentTimeMillis(),
        thermalHeadroom: Float = -1f,
        thermalStatus: Int = 0,
        isTempSensorAvailable: Boolean = true
    ): ThrottlePrediction {
        val hasValidTemp = isTempSensorAvailable && tempCelsius > 5.0f

        val reading = Reading(
            timestampMs = timestampMs,
            tempCelsius = if (hasValidTemp) tempCelsius else 0f,
            fps = fps,
            batteryDrainRate = batteryDrainRate,
            thermalHeadroom = thermalHeadroom,
            thermalStatus = thermalStatus,
            isTempSensorAvailable = hasValidTemp
        )
        sampleWindow.addLast(reading)

        // 1. Maintain 15-second rolling window
        val windowCutoff = timestampMs - (windowDurationSeconds * 1000L)
        while (sampleWindow.isNotEmpty() && sampleWindow.first().timestampMs < windowCutoff) {
            sampleWindow.removeFirst()
        }

        // 2. Update Exponential Moving Averages (EMA)
        if (tempEma == 0f && hasValidTemp) {
            tempEma = tempCelsius
            fpsEma = fps
        } else {
            if (hasValidTemp) {
                tempEma = (emaAlpha * tempCelsius) + ((1.0f - emaAlpha) * tempEma)
            }
            fpsEma = (emaAlpha * fps) + ((1.0f - emaAlpha) * fpsEma)
        }

        // 3. Calculate Temperature Rate of Change (°C per 10 sec & °C per min)
        val tempPoints = sampleWindow.filter { it.isTempSensorAvailable }.map { Pair(it.timestampMs, it.tempCelsius) }
        val tempSlopePerSec = if (tempPoints.size >= 3) computeLinearSlopePerSec(tempPoints) else 0.0f
        val tempRateOfChangePer10Sec = tempSlopePerSec * 10.0f
        val tempSlopePerMin = tempSlopePerSec * 60.0f

        // 4. Calculate FPS Stability (Variance over rolling window)
        val fpsList = sampleWindow.map { it.fps }
        val fpsVariance = computeVariance(fpsList)

        // 5. Calculate FPS Drop Percentage relative to top-tier baseline in window
        val baselineFps = if (sampleWindow.isNotEmpty()) {
            val sortedFps = fpsList.sortedDescending()
            val topCount = (sortedFps.size * 0.75f).toInt().coerceAtLeast(1)
            sortedFps.take(topCount).average().toFloat().coerceAtLeast(30.0f)
        } else {
            targetFps
        }

        val fpsDropPercent = if (baselineFps > 0f) {
            (((baselineFps - fps) / baselineFps) * 100.0f).coerceAtLeast(0.0f)
        } else {
            0.0f
        }

        // 6. Calculate Battery Drain Acceleration
        val drainPoints = sampleWindow.map { Pair(it.timestampMs, it.batteryDrainRate) }
        val batteryDrainAcceleration = computeLinearSlopePerSec(drainPoints) * 60.0f // % / min^2

        // 7. Estimated seconds to reach critical throttle threshold
        val secondsToThrottle: Int? = if (hasValidTemp && tempRateOfChangePer10Sec > 0.1f && tempCelsius < criticalTempThreshold) {
            val deltaTemp = criticalTempThreshold - tempCelsius
            val seconds = ((deltaTemp / tempSlopePerSec)).toInt()
            if (seconds in 5..300) seconds else null
        } else {
            null
        }

        // 8. Evaluate Risk Scoring Heuristics
        val (riskLevel, recommendation, isIncident, isFallbackActive) = evaluateRisk(
            hasValidTemp = hasValidTemp,
            currentTemp = tempCelsius,
            tempRatePer10Sec = tempRateOfChangePer10Sec,
            tempSlopePerMin = tempSlopePerMin,
            currentFps = fps,
            fpsVariance = fpsVariance,
            fpsDropPercent = fpsDropPercent,
            batteryDrainRate = batteryDrainRate,
            thermalHeadroom = thermalHeadroom,
            thermalStatus = thermalStatus,
            secondsToThrottle = secondsToThrottle
        )

        val prediction = ThrottlePrediction(
            riskLevel = riskLevel,
            currentTemp = if (hasValidTemp) tempCelsius else 0f,
            tempEma = tempEma,
            tempRateOfChangePer10Sec = tempRateOfChangePer10Sec,
            tempSlopePerMin = tempSlopePerMin,
            currentFps = fps,
            fpsEma = fpsEma,
            fpsVariance = fpsVariance,
            fpsDropPercent = fpsDropPercent,
            batteryDrainAcceleration = batteryDrainAcceleration,
            estimatedSecondsToThrottle = secondsToThrottle,
            recommendation = recommendation,
            isIncidentTriggered = isIncident,
            isFallbackModelActive = isFallbackActive
        )

        _predictionFlow.value = prediction
        return prediction
    }

    private fun evaluateRisk(
        hasValidTemp: Boolean,
        currentTemp: Float,
        tempRatePer10Sec: Float,
        tempSlopePerMin: Float,
        currentFps: Float,
        fpsVariance: Float,
        fpsDropPercent: Float,
        batteryDrainRate: Float,
        thermalHeadroom: Float,
        thermalStatus: Int,
        secondsToThrottle: Int?
    ): Quadruple<RiskLevel, String, Boolean, Boolean> {

        // ==========================================
        // FALLBACK MODE (Temperature Sensor Unavailable)
        // ==========================================
        if (!hasValidTemp) {
            val isHighBatteryStress = batteryDrainRate >= 1.0f && (fpsDropPercent >= 8.0f || fpsVariance >= 25.0f)
            val isMedBatteryStress = batteryDrainRate >= 0.6f || fpsVariance >= 20.0f

            return when {
                isHighBatteryStress -> Quadruple(
                    RiskLevel.HIGH,
                    "High power draw & FPS drop! Lower refresh rate or brightness.",
                    true,
                    true
                )
                isMedBatteryStress -> Quadruple(
                    RiskLevel.MEDIUM,
                    "Elevated battery drain rate detected. Monitor workload.",
                    false,
                    true
                )
                else -> Quadruple(
                    RiskLevel.LOW,
                    "Battery drain and framerate stable.",
                    false,
                    true
                )
            }
        }

        // ==========================================
        // PRIMARY THERMAL MODEL
        // ==========================================
        val isFastTempWithFpsDrop = (tempRatePer10Sec >= 0.60f && (fpsDropPercent >= 8.0f || fpsVariance >= 35.0f))
        val isCriticalAbsoluteTemp = currentTemp >= criticalTempThreshold
        val isLowThermalHeadroom = thermalHeadroom in 0.01f..0.15f
        val isSevereThermalStatus = thermalStatus >= 3
        val isSevereFpsCollapse = currentTemp >= 40.0f && fpsDropPercent >= 18.0f

        if (isFastTempWithFpsDrop || isCriticalAbsoluteTemp || isLowThermalHeadroom || isSevereThermalStatus || isSevereFpsCollapse) {
            val msg = when {
                secondsToThrottle != null && secondsToThrottle < 45 ->
                    "Throttling in ~${secondsToThrottle}s! Lower graphics quality or cap at 60 FPS."
                isFastTempWithFpsDrop ->
                    "Imminent Throttling! Fast thermal ramp (+${String.format("%.2f", tempRatePer10Sec)}°C/10s) & FPS dropping (-${fpsDropPercent.toInt()}%)."
                isSevereFpsCollapse ->
                    "Thermal stutter detected (-${fpsDropPercent.toInt()}% FPS). Reduce display brightness."
                else ->
                    "High thermal stress (>= ${String.format("%.1f", currentTemp)}°C)! Lower render resolution or enable power saving."
            }
            return Quadruple(RiskLevel.HIGH, msg, true, false)
        }

        val isModerateTempRise = tempRatePer10Sec >= 0.30f
        val isHighFpsVariance = fpsVariance >= 20.0f || fpsDropPercent >= 10.0f
        val isModerateHeadroom = thermalHeadroom in 0.16f..0.45f
        val isModerateStatus = thermalStatus == 2

        if (isModerateTempRise || isHighFpsVariance || isModerateHeadroom || isModerateStatus) {
            val msg = when {
                isModerateTempRise && isHighFpsVariance ->
                    "Temp rising moderately (+${String.format("%.2f", tempRatePer10Sec)}°C/10s) with framerate jitter. Close background apps."
                isModerateTempRise ->
                    "Temperature rising (+${String.format("%.2f", tempRatePer10Sec)}°C/10s). Monitor load."
                else ->
                    "FPS instability detected (variance ${String.format("%.1f", fpsVariance)}). Consider lowering demanding effects."
            }
            return Quadruple(RiskLevel.MEDIUM, msg, false, false)
        }

        return Quadruple(RiskLevel.LOW, "Thermals and framerate stable. No throttling anticipated.", false, false)
    }

    internal fun computeLinearSlopePerSec(points: List<Pair<Long, Float>>): Float {
        val n = points.size
        if (n < 3) return 0.0f

        val t0 = points.first().first
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0

        for (pt in points) {
            val x = (pt.first - t0) / 1000.0
            val y = pt.second.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }

        val denominator = (n * sumXX) - (sumX * sumX)
        if (denominator == 0.0) return 0.0f

        val slopePerSec = ((n * sumXY) - (sumX * sumY)) / denominator
        return slopePerSec.toFloat().coerceIn(-10.0f, 10.0f)
    }

    internal fun computeVariance(values: List<Float>): Float {
        if (values.size < 2) return 0.0f
        val mean = values.average().toFloat()
        var sumSquares = 0.0
        for (v in values) {
            val diff = v - mean
            sumSquares += diff * diff
        }
        return (sumSquares / values.size).toFloat()
    }

    @Synchronized
    fun reset() {
        sampleWindow.clear()
        tempEma = 0f
        fpsEma = 0f
        _predictionFlow.value = ThrottlePrediction()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
