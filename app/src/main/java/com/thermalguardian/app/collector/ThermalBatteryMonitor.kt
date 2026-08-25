package com.thermalguardian.app.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ThermalBatteryMonitor(private val context: Context) {

    private val isListening = AtomicBoolean(false)

    @Volatile
    var batteryLevel: Int = 100
        private set

    @Volatile
    var batteryTempCelsius: Float = 30.0f
        private set

    @Volatile
    var batteryVoltageMv: Int = 4000
        private set

    @Volatile
    var batteryCurrentMa: Float = 0.0f
        private set

    @Volatile
    var thermalStatus: Int = 0 // PowerManager.THERMAL_STATUS_NONE
        private set

    @Volatile
    var thermalHeadroom: Float = -1.0f
        private set

    private var powerManager: PowerManager? = null
    private var hardwarePropertiesManager: HardwarePropertiesManager? = null
    private var batteryManager: BatteryManager? = null

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryLevel = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                }

                val tempRaw = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (tempRaw > 0) {
                    batteryTempCelsius = tempRaw / 10.0f
                }

                batteryVoltageMv = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
            }
        }
    }

    fun start() {
        if (isListening.compareAndSet(false, true)) {
            powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            hardwarePropertiesManager = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
            batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            // Register Battery BroadcastReceiver
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)

            // Register Thermal Status Listener (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    thermalStatus = status
                }.also { listener ->
                    try {
                        powerManager?.addThermalStatusListener(context.mainExecutor, listener)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun stop() {
        if (isListening.compareAndSet(true, false)) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
                try {
                    powerManager?.removeThermalStatusListener(thermalListener!!)
                } catch (_: Exception) {}
                thermalListener = null
            }
        }
    }

    /**
     * Reads real-time current CPU/SoC temperature using multi-tiered fallback.
     */
    fun readCurrentCpuTemp(): Float {
        // Tier 1: HardwarePropertiesManager CPU Temps (API 24+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hardwarePropertiesManager != null) {
                val cpuTemps = hardwarePropertiesManager?.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )
                if (cpuTemps != null && cpuTemps.isNotEmpty()) {
                    val validTemps = cpuTemps.filter { it in 15.0f..105.0f }
                    if (validTemps.isNotEmpty()) {
                        return validTemps.maxOrNull() ?: validTemps.average().toFloat()
                    }
                }
            }
        } catch (_: Exception) {}

        // Tier 2: Sysfs thermal zones (Common on Qualcomm / Snapdragon / MediaTek devices)
        val sysfsTemp = readSysfsThermalTemp()
        if (sysfsTemp != null && sysfsTemp in 20.0f..105.0f) {
            return sysfsTemp
        }

        // Tier 3: Battery temperature fallback (+3.5°C estimated offset for SoC under load)
        return (batteryTempCelsius + 3.5f).coerceIn(20.0f, 95.0f)
    }

    /**
     * Query thermal headroom forecast for the next 10 seconds (API 30+)
     */
    fun updateThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager?.getThermalHeadroom(10) ?: -1.0f
                if (headroom >= 0f) {
                    thermalHeadroom = headroom
                    return headroom
                }
            } catch (_: Exception) {}
        }
        thermalHeadroom = -1.0f
        return -1.0f
    }

    /**
     * Reads current discharge in mA
     */
    fun readCurrentNowMa(): Float {
        if (batteryManager != null) {
            try {
                val microAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                if (microAmps != 0 && microAmps != Int.MIN_VALUE) {
                    val ma = kotlin.math.abs(microAmps) / 1000.0f
                    batteryCurrentMa = ma
                    return ma
                }
            } catch (_: Exception) {}
        }
        return batteryCurrentMa
    }

    private fun readSysfsThermalTemp(): Float? {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp"
        )

        for (path in thermalPaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                try {
                    val rawStr = file.readText().trim()
                    val rawVal = rawStr.toFloatOrNull()
                    if (rawVal != null) {
                        return if (rawVal > 1000f) rawVal / 1000.0f else rawVal
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }
}
