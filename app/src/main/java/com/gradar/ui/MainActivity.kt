package com.gradar.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gradar.R
import com.gradar.capture.AlbionVpnService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvEntityCount: TextView

    private var isRunning = false
    private val REQUEST_VPN_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        observeState()
    }

    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvEntityCount = findViewById(R.id.tvEntityCount)

        btnStartStop.setOnClickListener {
            if (isRunning) {
                stopCapture()
            } else {
                checkAndStartCapture()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            AlbionVpnService.connectionStatus.collect { state ->
                when (state) {
                    is AlbionVpnService.ConnectionState.Disconnected -> {
                        isRunning = false
                        tvStatus.text = getString(R.string.service_stopped)
                        btnStartStop.text = getString(R.string.start_capture)
                    }
                    is AlbionVpnService.ConnectionState.Connecting -> {
                        tvStatus.text = "Connecting..."
                    }
                    is AlbionVpnService.ConnectionState.Connected -> {
                        isRunning = true
                        tvStatus.text = getString(R.string.service_running)
                        btnStartStop.text = getString(R.string.stop_capture)
                        startOverlayService()
                    }
                    is AlbionVpnService.ConnectionState.Error -> {
                        isRunning = false
                        tvStatus.text = "Error: ${state.message}"
                        btnStartStop.text = getString(R.string.start_capture)
                    }
                }
            }
        }

        lifecycleScope.launch {
            AlbionVpnService.packetStats.collect { stats ->
                tvEntityCount.text = "Entities: ${stats.entitiesSpawned} | Unknown: ${stats.unknownEntities}"
            }
        }
    }

    private fun checkAndStartCapture() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION)
        } else {
            startVpnService()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, R.string.vpn_permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AlbionVpnService::class.java).apply {
            action = AlbionVpnService.ACTION_START
        }
        startService(intent)
    }

    private fun stopCapture() {
        val vpnIntent = Intent(this, AlbionVpnService::class.java).apply {
            action = AlbionVpnService.ACTION_STOP
        }
        startService(vpnIntent)

        val overlayIntent = Intent(this, RadarOverlayService::class.java).apply {
            action = RadarOverlayService.ACTION_STOP
        }
        stopService(overlayIntent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, RadarOverlayService::class.java).apply {
            action = RadarOverlayService.ACTION_START
        }
        startForegroundService(intent)
    }

    override fun onResume() {
        super.onResume()
    }
}
