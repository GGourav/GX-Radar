package com.gradar.ui

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.gradar.model.EntityType
import com.gradar.model.FilterConfig
import com.gradar.model.RadarEntity
import kotlinx.coroutines.*
import kotlin.math.*

class RadarOverlayView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    companion object {
        private const val TARGET_FPS = 60L
        private const val FRAME_TIME_MS = 1000L / TARGET_FPS

        private val ENCHANT_COLORS = mapOf(
            1 to 0xFF00E676.toInt(),
            2 to 0xFF1565C0.toInt(),
            3 to 0xFFAA00FF.toInt(),
            4 to 0xFFFFD600.toInt()
        )
    }

    private var entities: List<RadarEntity> = emptyList()
    private var filterConfig: FilterConfig = FilterConfig()
    private var radarSize: Int = 300

    private var renderJob: Job? = null
    private val renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = 0x40000000
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x80FFFFFF
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private var playerX: Float = 0f
    private var playerZ: Float = 0f

    init {
        holder.addCallback(this)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    private fun startRenderThread() {
        renderJob = renderScope.launch {
            while (isActive) {
                val startTime = System.currentTimeMillis()
                renderFrame()
                val elapsed = System.currentTimeMillis() - startTime
                val delay = FRAME_TIME_MS - elapsed
                if (delay > 0) delay(delay)
            }
        }
    }

    private fun stopRenderThread() {
        renderJob?.cancel()
        renderJob = null
    }

    private fun renderFrame() {
        val holder = holder ?: return
        if (!holder.surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas != null) {
                drawFrame(canvas)
            }
        } catch (e: Exception) {
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun drawFrame(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val centerX = (width - radarSize / 2 - 50).toFloat()
        val centerY = (height / 2).toFloat()

        canvas.drawCircle(centerX, centerY, radarSize / 2f, radarPaint)
        canvas.drawCircle(centerX, centerY, radarSize / 2f, borderPaint)
        canvas.drawCircle(centerX, centerY, 4f, centerPaint)

        val filteredEntities = entities.filter { filterConfig.matches(it) }
        val sortedEntities = filteredEntities.sortedByDescending { it.distanceFrom(playerX, playerZ) }

        for (entity in sortedEntities) {
            val relX = (entity.posX - playerX) * filterConfig.zoom
            val relZ = (entity.posZ - playerZ) * filterConfig.zoom

            val screenX = centerX + relX
            val screenY = centerY + relZ

            val distFromCenter = sqrt((screenX - centerX).pow(2) + (screenY - centerY).pow(2))
            if (distFromCenter > radarSize / 2f - 10f) continue

            val color = entity.entityType.getDisplayColor()
            dotPaint.color = color

            if (entity.entityType == EntityType.MIST_WISP) {
                val diamondPath = Path().apply {
                    moveTo(screenX, screenY - 6f)
                    lineTo(screenX + 6f, screenY)
                    lineTo(screenX, screenY + 6f)
                    lineTo(screenX - 6f, screenY)
                    close()
                }
                canvas.drawPath(diamondPath, dotPaint)
            } else {
                canvas.drawCircle(screenX, screenY, 6f, dotPaint)
            }

            if (entity.enchantLevel in 1..4) {
                val ringColor = ENCHANT_COLORS[entity.enchantLevel] ?: continue
                ringPaint.color = ringColor
                canvas.drawCircle(screenX, screenY, 10f, ringPaint)
            }
        }
    }

    fun updateEntities(newEntities: List<RadarEntity>) {
        entities = newEntities
    }

    fun updateFilterConfig(config: FilterConfig) {
        filterConfig = config
        radarSize = config.radarSize
    }

    fun setPlayerPosition(x: Float, z: Float) {
        playerX = x
        playerZ = z
    }
}
