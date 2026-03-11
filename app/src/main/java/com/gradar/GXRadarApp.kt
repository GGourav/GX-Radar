package com.gradar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.gradar.data.EntityDatabase

/**
 * Application class for GX Radar.
 * Initializes core components and provides singleton access to database.
 */
class GXRadarApp : Application() {

    companion object {
        const val CHANNEL_VPN_SERVICE = "vpn_service"
        const val CHANNEL_OVERLAY_SERVICE = "overlay_service"
        const val NOTIFICATION_VPN_ID = 1001
        const val NOTIFICATION_OVERLAY_ID = 1002

        @Volatile
        private var INSTANCE: GXRadarApp? = null

        fun getInstance(): GXRadarApp =
            INSTANCE ?: throw IllegalStateException("Application not initialized")

        fun getDatabase(): EntityDatabase = getInstance().database
    }

    lateinit var database: EntityDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        // Initialize Room database
        database = Room.databaseBuilder(
            applicationContext,
            EntityDatabase::class.java,
            "gradar_entities"
        )
            .fallbackToDestructiveMigration()
            .build()

        // Create notification channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // VPN Service Channel
            val vpnChannel = NotificationChannel(
                CHANNEL_VPN_SERVICE,
                "Packet Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for capturing game traffic"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(vpnChannel)

            // Overlay Service Channel
            val overlayChannel = NotificationChannel(
                CHANNEL_OVERLAY_SERVICE,
                "Radar Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radar overlay display service"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(overlayChannel)
        }
    }
}
