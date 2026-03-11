package com.gradar.ui

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.CheckBox
import android.widget.SeekBar
import com.gradar.GXRadarApp
import com.gradar.R
import com.gradar.capture.AlbionVpnService
import com.gradar.data.EntityClassifier
import com.gradar.data.GXRadarApp.Companion.getDatabase
import com.gradar.model.*
import com.gradar.util.DiscoveryLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RadarOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.gradar.action.OVERLAY_START"
        const val ACTION_STOP = "com.gradar.action.OVERLAY_STOP"
        const val DEFAULT_RADAR_SIZE = 300

        private const val TAG = "RadarOverlayService"

        private val _entities = MutableStateFlow<Map<Int, RadarEntity>>(emptyMap())
        val entities: StateFlow<Map<Int, RadarEntity>> = _entities

        private val _filterConfig = MutableStateFlow(FilterConfig())
        val filterConfig: StateFlow<FilterConfig> = _filterConfig

        private val _playerPosition = MutableStateFlow(Pair(0f, 0f))
        val playerPosition: StateFlow<Pair<Float, Float>> = _playerPosition
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: RadarOverlayView? = null
    private var filterPanelView: View? = null

    private lateinit var discoveryLogger: DiscoveryLogger
    private lateinit var entityClassifier: EntityClassifier

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Overlay service created")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        discoveryLogger = DiscoveryLogger(this)
        entityClassifier = EntityClassifier(getDatabase())

        AlbionVpnService.entityCallback = { entityId, typeId, uniqueName, x, z, factionFlags ->
            handleEntitySpawn(entityId, typeId, uniqueName, x, z, factionFlags)
        }
        AlbionVpnService.leaveCallback = { entityId ->
            handleEntityLeave(entityId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startOverlay()
            ACTION_STOP -> stopOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
        Log.d(TAG, "Overlay service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startOverlay() {
        if (overlayView != null) return

        startForeground(GXRadarApp.NOTIFICATION_OVERLAY_ID, createNotification())

        createRadarOverlay()
        createFilterPanel()

        serviceScope.launch {
            entities.collect { entityMap ->
                overlayView?.updateEntities(entityMap.values.toList())
            }
        }

        serviceScope.launch {
            filterConfig.collect { config ->
                overlayView?.updateFilterConfig(config)
            }
        }

        Log.i(TAG, "Overlay started")
    }

    private fun stopOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null

        filterPanelView?.let { windowManager.removeView(it) }
        filterPanelView = null

        AlbionVpnService.entityCallback = null
        AlbionVpnService.leaveCallback = null

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)

        Log.i(TAG, "Overlay stopped")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createRadarOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayView = RadarOverlayView(this)
        windowManager.addView(overlayView, params)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFilterPanel() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 100
        }

        filterPanelView = LayoutInflater.from(this).inflate(R.layout.filter_panel, null)
        setupFilterControls(filterPanelView!!)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        filterPanelView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(filterPanelView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(filterPanelView, params)
    }

    private fun setupFilterControls(view: View) {
        view.findViewById<SeekBar>(R.id.seekRadarSize).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val size = progress + 200
                    updateFilterConfig { it.copy(radarSize = size) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        val tierCheckBoxes = listOf(
            R.id.cbT1 to 1, R.id.cbT2 to 2, R.id.cbT3 to 3, R.id.cbT4 to 4,
            R.id.cbT5 to 5, R.id.cbT6 to 6, R.id.cbT7 to 7, R.id.cbT8 to 8
        )

        tierCheckBoxes.forEach { (id, tier) ->
            view.findViewById<CheckBox>(id).setOnCheckedChangeListener { _, isChecked ->
                updateFilterConfig { config ->
                    val tiers = if (isChecked) config.enabledTiers + tier else config.enabledTiers - tier
                    config.copy(enabledTiers = tiers)
                }
            }
        }

        view.findViewById<CheckBox>(R.id.cbMobNormal).setOnCheckedChangeListener { _, isChecked ->
            updateFilterConfig { it.copy(showMobs = isChecked) }
        }
        view.findViewById<CheckBox>(R.id.cbMobEnch).setOnCheckedChangeListener { _, isChecked ->
            updateFilterConfig { it.copy(showEnchantedMobs = isChecked) }
        }
        view.findViewById<CheckBox>(R.id.cbMobBoss).setOnCheckedChangeListener { _, isChecked ->
            updateFilterConfig { it.copy(showBosses = isChecked) }
        }

        view.findViewById<CheckBox>(R.id.cbPlayerPassive).setOnCheckedChangeListener { _, isChecked ->
            updateFilterConfig { it.copy(showPassivePlayers = isChecked) }
        }
        view.findViewById<CheckBox>(R.id.cbPlayerHostile).setOnCheckedChangeListener { _, isChecked ->
            updateFilterConfig { it.copy(showHostilePlayers = isChecked) }
        }
    }

    private fun updateFilterConfig(update: (FilterConfig) -> FilterConfig) {
        _filterConfig.value = update(_filterConfig.value)
    }

    private fun handleEntitySpawn(
        entityId: Int,
        typeId: Int,
        uniqueName: String?,
        x: Float,
        z: Float,
        factionFlags: Byte
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val info = entityClassifier.classifyEntity(typeId, uniqueName, factionFlags)

            if (info?.entityType == null) {
                discoveryLogger.logUnknownEntity(
                    eventCode = if (typeId == -1) 24 else if (info?.entityType == EntityType.RESOURCE) 18 else 20,
                    typeId = typeId,
                    uniqueName = uniqueName,
                    posX = x,
                    posZ = z
                )
                return@launch
            }

            val entity = RadarEntity(
                entityId = entityId,
                typeId = typeId,
                uniqueName = uniqueName,
                entityName = info.displayName,
                entityType = info.entityType,
                tier = info.tier,
                enchantLevel = info.enchantLevel,
                posX = x,
                posZ = z,
                factionFlags = factionFlags
            )

            _entities.value = _entities.value + (entityId to entity)
        }
    }

    private fun handleEntityLeave(entityId: Int) {
        _entities.value = _entities.value - entityId
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, GXRadarApp.CHANNEL_OVERLAY_SERVICE)
            .setContentTitle("GX Radar Active")
            .setContentText("Radar overlay running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}
