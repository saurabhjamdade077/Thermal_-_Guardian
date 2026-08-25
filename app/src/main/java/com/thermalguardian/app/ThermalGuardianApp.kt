package com.thermalguardian.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.thermalguardian.app.data.db.AppDatabase
import com.thermalguardian.app.data.repository.SessionRepository

class ThermalGuardianApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Room Database & Repository
        database = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(database.sessionDao())

        // Create Foreground Service Notification Channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.service_notification_channel_id)
            val channelName = getString(R.string.service_notification_channel_name)
            val channelDescription = "Notifications for the Thermal Guardian live performance overlay"
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: ThermalGuardianApp
            private set
    }
}
