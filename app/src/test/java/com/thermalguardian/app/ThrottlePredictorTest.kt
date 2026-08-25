package com.thermalguardian.app

import com.thermalguardian.app.data.model.RiskLevel
import com.thermalguardian.app.predictor.ThrottlePredictor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThrottlePredictorTest {

    private lateinit var predictor: ThrottlePredictor

    @Before
    fun setUp() {
        predictor = ThrottlePredictor(
            windowDurationSeconds = 15,
            criticalTempThreshold = 43.5f,
            targetFps = 60.0f
        )
    }

    /**
     * Test Case 1: LOW RISK
     * - Temperature rising slowly (< 0.3°C / 10s => e.g., +0.01°C / sec = +0.1°C / 10s)
     * - FPS stable at 60 FPS (variance ~ 0.0)
     */
    @Test
    fun testLowRisk_slowTempRiseAndStableFps() = runBlocking {
        var prediction = predictor.processReading(
            tempCelsius = 32.0f,
            fps = 60.0f,
            batteryDrainRate = 0.2f,
            timestampMs = 1000L
        )

        for (i in 2..15) {
            prediction = predictor.processReading(
                tempCelsius = 32.0f + (i * 0.01f), // +0.01°C/sec -> 0.1°C/10s
                fps = 60.0f,
                batteryDrainRate = 0.2f,
                timestampMs = i * 1000L
            )
        }

        assertEquals(RiskLevel.LOW, prediction.riskLevel)
        assertTrue("Temp rate of change should be < 0.3°C/10s", prediction.tempRateOfChangePer10Sec < 0.3f)
        assertTrue("FPS variance should be very low", prediction.fpsVariance < 1.0f)
        assertEquals(RiskLevel.LOW, predictor.riskLevelFlow.first())
    }

    /**
     * Test Case 2: MEDIUM RISK (Scenario A - Moderate Temp Rise)
     * - Temperature rising moderately between 0.3°C and 0.6°C / 10s (+0.045°C / sec = +0.45°C / 10s)
     * - FPS still relatively steady
     */
    @Test
    fun testMediumRisk_moderateTempRise() = runBlocking {
        var prediction = predictor.processReading(
            tempCelsius = 35.0f,
            fps = 60.0f,
            batteryDrainRate = 0.4f,
            timestampMs = 1000L
        )

        for (i in 2..15) {
            prediction = predictor.processReading(
                tempCelsius = 35.0f + (i * 0.045f), // +0.045°C/sec -> +0.45°C/10s
                fps = 60.0f,
                batteryDrainRate = 0.4f,
                timestampMs = i * 1000L
            )
        }

        assertEquals(RiskLevel.MEDIUM, prediction.riskLevel)
        assertTrue("Rate should be >= 0.3°C/10s", prediction.tempRateOfChangePer10Sec >= 0.3f)
        assertTrue("Rate should be <= 0.6°C/10s", prediction.tempRateOfChangePer10Sec <= 0.6f)
    }

    /**
     * Test Case 2: MEDIUM RISK (Scenario B - Increasing FPS Variance)
     * - Temp is cool (33°C), but FPS exhibits severe jitter/stuttering (variance >= 20.0)
     */
    @Test
    fun testMediumRisk_highFpsVariance() = runBlocking {
        val fluctuatingFps = listOf(60f, 40f, 60f, 38f, 59f, 35f, 60f, 42f, 58f, 36f, 60f, 40f, 59f, 35f, 60f)

        var prediction = predictor.processReading(
            tempCelsius = 33.0f,
            fps = fluctuatingFps[0],
            timestampMs = 1000L
        )

        for (i in 1 until fluctuatingFps.size) {
            prediction = predictor.processReading(
                tempCelsius = 33.0f,
                fps = fluctuatingFps[i],
                timestampMs = (i + 1) * 1000L
            )
        }

        assertEquals(RiskLevel.MEDIUM, prediction.riskLevel)
        assertTrue("FPS variance should be high (> 20.0)", prediction.fpsVariance >= 20.0f)
    }

    /**
     * Test Case 3: HIGH RISK
     * - Temperature rising fast (> 0.6°C / 10s => e.g., +0.10°C / sec = +1.0°C / 10s)
     * - AND FPS starting to drop (e.g. from 60 FPS down to 48 FPS, > 8% drop)
     */
    @Test
    fun testHighRisk_fastTempRiseAndFpsDropping() = runBlocking {
        // First 8 seconds: Establishing baseline at 60 FPS with rapid temp climb
        for (i in 1..8) {
            predictor.processReading(
                tempCelsius = 38.0f + (i * 0.10f), // +0.10°C/sec -> +1.0°C/10s
                fps = 60.0f,
                batteryDrainRate = 0.8f,
                timestampMs = i * 1000L
            )
        }

        // Next seconds: Temp continues climbing fast AND FPS drops to 45 FPS
        var prediction = predictor.processReading(
            tempCelsius = 39.0f,
            fps = 48.0f,
            batteryDrainRate = 1.2f,
            timestampMs = 9000L
        )

        for (i in 10..15) {
            prediction = predictor.processReading(
                tempCelsius = 38.0f + (i * 0.10f), // Fast temp rise
                fps = 45.0f, // FPS dropped by ~25%
                batteryDrainRate = 1.4f,
                timestampMs = i * 1000L
            )
        }

        assertEquals(RiskLevel.HIGH, prediction.riskLevel)
        assertTrue("Temp rate of change should exceed 0.6°C/10s", prediction.tempRateOfChangePer10Sec > 0.6f)
        assertTrue("FPS drop should be greater than 8%", prediction.fpsDropPercent > 8.0f)
        assertTrue("Should flag incident", prediction.isIncidentTriggered)
    }

    /**
     * Test Case 4: Fallback Battery-Drain Predictor Mode (when temperature sensor is unavailable)
     */
    @Test
    fun testFallbackMode_whenTemperatureUnavailable() = runBlocking {
        for (i in 1..10) {
            predictor.processReading(
                tempCelsius = 0f,
                fps = 60f,
                batteryDrainRate = 0.3f,
                timestampMs = i * 1000L,
                isTempSensorAvailable = false
            )
        }

        // High battery stress + FPS drop
        val prediction = predictor.processReading(
            tempCelsius = 0f,
            fps = 45f, // ~25% drop
            batteryDrainRate = 1.5f,
            timestampMs = 11000L,
            isTempSensorAvailable = false
        )

        assertEquals(RiskLevel.HIGH, prediction.riskLevel)
        assertTrue(prediction.isFallbackModelActive)
    }

    /**
     * Test Case 5: Mathematical helper verification
     */
    @Test
    fun testMathCalculations_slopeAndVariance() {
        val points = listOf(
            Pair(0L, 30.0f),
            Pair(10000L, 31.0f),
            Pair(20000L, 32.0f)
        )
        val slopePerSec = predictor.computeLinearSlopePerSec(points)
        assertEquals(0.10f, slopePerSec, 0.005f)

        val values = listOf(10f, 20f, 30f)
        val variance = predictor.computeVariance(values)
        assertEquals(66.666f, variance, 0.01f)
    }
}
