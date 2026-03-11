package com.gradar.capture

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.gradar.GXRadarApp
import com.gradar.R
import com.gradar.model.PacketStats
import com.gradar.parser.PhotonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * VPN Service for capturing Albion Online game traffic.
 * Creates a TUN interface to intercept UDP packets on port 5056.
 */
class AlbionVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.gradar.action.START"
        const val ACTION_STOP = "com.gradar.action.STOP"
        const val ALBION_PORT = 5056
        const val MTU = 32767
        const val VPN_ADDRESS = "10.0.0.1"
        const val DNS_SERVER = "8.8.8.8"

        private const val TAG = "AlbionVpnService"

        private val _connectionStatus = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val connectionStatus: StateFlow<ConnectionState> = _connectionStatus.asStateFlow()

        private val _packetStats = MutableStateFlow(PacketStats())
        val packetStats: StateFlow<PacketStats> = _packetStats.asStateFlow()

        private val _entityCallback = MutableStateFlow<((Int, Int, String?, Float, Float, Byte) -> Unit)?>(null)
        var entityCallback: ((entityId: Int, typeId: Int, uniqueName: String?, x: Float, z: Float, factionFlags: Byte) -> Unit)?
            get() = _entityCallback.value
            set(value) { _entityCallback.value = value }

        private val _leaveCallback = MutableStateFlow<((Int) -> Unit)?>(null)
        var leaveCallback: ((entityId: Int) -> Unit)?
            get() = _leaveCallback.value
            set(value) { _leaveCallback.value = value }
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val interfaceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureJob: Job? = null
    private val parser = PhotonParser()
    private var stats = PacketStats()

    private val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
    }

    private fun startCapture() {
        if (vpnInterface != null) {
            Log.w(TAG, "VPN already running")
            return
        }

        _connectionStatus.value = ConnectionState.Connecting

        try {
            // Create VPN interface
            vpnInterface = Builder()
                .setSession("GX Radar")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_SERVER)
                .setMtu(MTU)
                .setBlocking(true)
                .establish()

            if (vpnInterface == null) {
                _connectionStatus.value = ConnectionState.Error("Failed to establish VPN interface")
                return
            }

            val interfaceName = vpnInterface!!.fd.let { "tun${it % 100}" }
            _connectionStatus.value = ConnectionState.Connected(interfaceName)
            Log.i(TAG, "VPN interface established: $interfaceName")

            // Start foreground notification
            startForeground(GXRadarApp.NOTIFICATION_VPN_ID, createNotification())

            // Start capture coroutine
            captureJob = captureScope.launch {
                capturePackets()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            _connectionStatus.value = ConnectionState.Error(e.message ?: "Unknown error")
            vpnInterface?.close()
            vpnInterface = null
        }
    }

    private fun stopCapture() {
        Log.d(TAG, "Stopping capture")

        captureJob?.cancel()
        captureJob = null

        vpnInterface?.close()
        vpnInterface = null

        _connectionStatus.value = ConnectionState.Disconnected
        stats = PacketStats()
        _packetStats.value = stats

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun capturePackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val channel = inputStream.channel

        val buffer = ByteBuffer.allocateDirect(MTU)

        Log.i(TAG, "Starting packet capture loop")

        try {
            while (isActive && vpnInterface != null) {
                buffer.clear()
                val bytesRead = channel.read(buffer)

                if (bytesRead > 0) {
                    buffer.flip()
                    processPacket(buffer, bytesRead)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "Capture loop cancelled")
            } else {
                Log.e(TAG, "Capture loop error", e)
            }
        }
    }

    private fun processPacket(buffer: ByteBuffer, length: Int) {
        if (length < 20) return // Minimum IP header size

        stats = stats.copy(totalPackets = stats.totalPackets + 1)

        // Parse IP header
        val version = (buffer.get(0).toInt() shr 4) and 0x0F
        if (version != 4) {
            forwardPacket(buffer, length)
            return
        }

        val ihl = (buffer.get(0).toInt() and 0x0F) * 4
        if (length < ihl) return

        val protocol = buffer.get(9).toInt() and 0xFF

        if (protocol != 17) {
            forwardPacket(buffer, length)
            return
        }

        val udpStart = ihl
        if (length < udpStart + 8) return

        val srcPort = ((buffer.get(udpStart).toInt() and 0xFF) shl 8) or (buffer.get(udpStart + 1).toInt() and 0xFF)
        val dstPort = ((buffer.get(udpStart + 2).toInt() and 0xFF) shl 8) or (buffer.get(udpStart + 3).toInt() and 0xFF)

        if (srcPort == ALBION_PORT || dstPort == ALBION_PORT) {
            stats = stats.copy(gamePackets = stats.gamePackets + 1)

            val udpLength = ((buffer.get(udpStart + 4).toInt() and 0xFF) shl 8) or (buffer.get(udpStart + 5).toInt() and 0xFF)
            val payloadStart = udpStart + 8
            val payloadLength = udpLength - 8

            if (payloadLength > 0 && length >= payloadStart + payloadLength) {
                val payload = ByteArray(payloadLength)
                buffer.position(payloadStart)
                buffer.get(payload)
                parsePhotonPacket(payload)
            }
        } else {
            forwardPacket(buffer, length)
        }

        _packetStats.value = stats
    }

    private fun parsePhotonPacket(payload: ByteArray) {
        try {
            val events = parser.parse(payload)
            for (event in events) {
                when (event.eventCode) {
                    18, 19 -> handleResourceSpawn(event)
                    20 -> handleMobSpawn(event)
                    24 -> handlePlayerSpawn(event)
                    1 -> handleEntityLeave(event)
                    3, 4 -> handleEntityMove(event)
                }
                stats = stats.copy(eventsProcessed = stats.eventsProcessed + 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Photon packet: ${e.message}")
        }
    }

    private fun handleResourceSpawn(event: PhotonParser.GameEvent) {
        val entityId = event.getInt(0) ?: return
        val typeId = event.getShort(1) ?: return
        val position = event.getByteArray(2)
        val uniqueName = event.getString(17)

        if (position != null && position.size >= 16) {
            val x = extractX(position)
            val z = extractZ(position)
            entityCallback?.invoke(entityId, typeId.toInt(), uniqueName, x, z, 0)
            stats = stats.copy(entitiesSpawned = stats.entitiesSpawned + 1)
        }
    }

    private fun handleMobSpawn(event: PhotonParser.GameEvent) {
        val entityId = event.getInt(0) ?: return
        val typeId = event.getShort(1) ?: return
        val position = event.getByteArray(2)
        val uniqueName = event.getString(17)

        if (position != null && position.size >= 16) {
            val x = extractX(position)
            val z = extractZ(position)
            entityCallback?.invoke(entityId, typeId.toInt(), uniqueName, x, z, 0)
            stats = stats.copy(entitiesSpawned = stats.entitiesSpawned + 1)
        }
    }

    private fun handlePlayerSpawn(event: PhotonParser.GameEvent) {
        val entityId = event.getInt(0) ?: return
        val name = event.getString(1) ?: "Unknown"
        val position = event.getByteArray(2)
        val factionFlags = event.getByte(11) ?: 0

        if (position != null && position.size >= 16) {
            val x = extractX(position)
            val z = extractZ(position)
            entityCallback?.invoke(entityId, -1, name, x, z, factionFlags)
            stats = stats.copy(entitiesSpawned = stats.entitiesSpawned + 1)
        }
    }

    private fun handleEntityLeave(event: PhotonParser.GameEvent) {
        val entityId = event.getInt(0) ?: return
        leaveCallback?.invoke(entityId)
        stats = stats.copy(entitiesDespawned = stats.entitiesDespawned + 1)
    }

    private fun handleEntityMove(event: PhotonParser.GameEvent) {
        val entityId = event.getInt(0) ?: return
        val position = event.getByteArray(1)

        if (position != null && position.size >= 16) {
            val x = extractX(position)
            val z = extractZ(position)
            entityCallback?.invoke(entityId, -2, null, x, z, 0)
        }
    }

    private fun extractX(bytes: ByteArray): Float {
        return java.nio.ByteBuffer.wrap(bytes, 8, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .float
    }

    private fun extractZ(bytes: ByteArray): Float {
        return java.nio.ByteBuffer.wrap(bytes, 12, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .float
    }

    private fun forwardPacket(buffer: ByteBuffer, length: Int) {
        // TODO: Implement forward proxy using protect() socket
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, GXRadarApp.CHANNEL_VPN_SERVICE)
            .setContentTitle(getString(R.string.service_running))
            .setContentText("Capturing game traffic on port $ALBION_PORT")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
