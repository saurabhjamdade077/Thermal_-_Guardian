package com.thermalguardian.app.collector

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean

class FpsMonitor {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)

    @Volatile
    var currentFps: Float = 60.0f
        private set

    @Volatile
    var onePercentLowFps: Float = 60.0f
        private set

    private val frameTimestamps = ArrayDeque<Long>(200)
    private val frameDurationsMs = ArrayDeque<Float>(200)
    private var lastFrameTimeNanos: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning.get()) return

            if (lastFrameTimeNanos > 0L) {
                val frameIntervalNanos = frameTimeNanos - lastFrameTimeNanos
                val frameDurationMs = frameIntervalNanos / 1_000_000.0f

                synchronized(this) {
                    val now = System.currentTimeMillis()
                    frameTimestamps.addLast(now)
                    frameDurationsMs.addLast(frameDurationMs)

                    // Retain only samples within the last 1000ms
                    val windowThreshold = now - 1000L
                    while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < windowThreshold) {
                        frameTimestamps.removeFirst()
                        frameDurationsMs.removeFirst()
                    }

                    val sampleCount = frameTimestamps.size
                    if (sampleCount >= 2) {
                        val durationSeconds = (frameTimestamps.last() - frameTimestamps.first()) / 1000.0f
                        currentFps = if (durationSeconds > 0.1f) {
                            (sampleCount / durationSeconds).coerceIn(1.0f, 144.0f)
                        } else {
                            currentFps
                        }

                        // Compute 1% low FPS (99th percentile slowest frame duration)
                        if (frameDurationsMs.size >= 10) {
                            val sortedDurations = frameDurationsMs.sortedDescending()
                            val index99th = (sortedDurations.size * 0.01f).toInt().coerceIn(0, sortedDurations.size - 1)
                            val worstFrameTimeMs = sortedDurations[index99th]
                            if (worstFrameTimeMs > 0.1f) {
                                onePercentLowFps = (1000.0f / worstFrameTimeMs).coerceIn(1.0f, currentFps)
                            }
                        }
                    }
                }
            }

            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            lastFrameTimeNanos = 0L
            mainHandler.post {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            mainHandler.post {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
            synchronized(this) {
                frameTimestamps.clear()
                frameDurationsMs.clear()
            }
        }
    }
}
