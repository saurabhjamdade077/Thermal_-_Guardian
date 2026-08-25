package com.thermalguardian.app

import com.thermalguardian.app.data.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {

    @Test
    fun testGradeCalculation_optimalConditions_givesSGrade() {
        val (grade, score) = SessionSummary.computeGrade(
            avgFps = 60.0f,
            onePercentLowFps = 57.0f,
            peakTemp = 36.0f,
            incidentCount = 0,
            batteryDrainPerHour = 12.0f
        )

        assertEquals("S", grade)
        assertEquals(100, score)
    }

    @Test
    fun testGradeCalculation_highThermalStress_givesDegradedGrade() {
        val (grade, score) = SessionSummary.computeGrade(
            avgFps = 52.0f,
            onePercentLowFps = 31.0f,
            peakTemp = 45.5f,
            incidentCount = 3,
            batteryDrainPerHour = 32.0f
        )

        assertEquals("D", grade)
        assertEquals(16, score)
    }

    @Test
    fun testSessionTimeFormatting() {
        assertEquals("32 min", SessionSummary.formatSessionTime(1920L))
        assertEquals("1 min", SessionSummary.formatSessionTime(65L))
        assertEquals("45s", SessionSummary.formatSessionTime(45L))
    }
}
