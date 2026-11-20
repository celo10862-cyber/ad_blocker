package com.netshield.adblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnToggleVpn: Button
    private val uiHandler = Handler(Looper.getMainLooper())

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission required for system-wide blocking", Toast.LENGTH_SHORT).show()
        }
    }

    private val statsUpdater = object : Runnable {
        override fun run() {
            updateUiState()
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        btnToggleVpn = findViewById(R.id.btnToggleVpn)

        btnToggleVpn.setOnClickListener {
            if (AdBlockVpnService.active) {
                val stopIntent = Intent(this, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_STOP
                }
                startService(stopIntent)
            } else {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    startVpnService()
                }
            }
        }

        findViewById<Button>(R.id.btnOpenYoutube).setOnClickListener {
            startActivity(Intent(this, AdFreeWebActivity::class.java).putExtra("URL", "https://m.youtube.com"))
        }

        findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener {
            startActivity(Intent(this, AdFreeWebActivity::class.java).putExtra("URL", "https://duckduckgo.com"))
        }

        findViewById<Button>(R.id.btnUpdateHosts).setOnClickListener { btn ->
            btn.isEnabled = false
            Toast.makeText(this, "Downloading latest ad & popup blocklists...", Toast.LENGTH_SHORT).show()
            BlocklistManager.syncRemoteHosts { count ->
                runOnUiThread {
                    btn.isEnabled = true
                    Toast.makeText(this, "Synced $count ad/tracker domains!", Toast.LENGTH_SHORT).show()
                    updateUiState()
                }
            }
        }

        findViewById<Button>(R.id.btnAddDomain).setOnClickListener {
            val et = findViewById<EditText>(R.id.etCustomDomain)
            val domain = et.text.toString().trim()
            if (domain.isNotEmpty()) {
                BlocklistManager.addCustomDomain(domain)
                et.text.clear()
                Toast.makeText(this, "Blocked: $domain", Toast.LENGTH_SHORT).show()
                updateUiState()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun updateUiState() {
        tvStats.text = "Ads Blocked: ${BlocklistManager.blockedCount.get()} | Active Rules: ${BlocklistManager.getRuleCount()}"
        if (AdBlockVpnService.active) {
            tvStatus.text = "Protection: ACTIVE (System-Wide)"
            tvStatus.setTextColor(Color.parseColor("#69F0AE"))
            btnToggleVpn.text = "Disable Ad Shield"
        } else {
            tvStatus.text = "Protection: OFF"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            btnToggleVpn.text = "Enable System-Wide Ad Shield"
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(statsUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(statsUpdater)
    }
}
