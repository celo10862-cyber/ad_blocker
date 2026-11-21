package com.netshield.adblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
<<<<<<< HEAD
=======
import android.os.Handler
import android.os.Looper
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
<<<<<<< HEAD
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnToggleVpn: Button
    private lateinit var tvCertificate: TextView

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
        else toast("VPN permission was not granted")
    }
    private val certificateInstaller = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshUi()
        toast("If Android accepted the certificate, review it in Security settings")
=======

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
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD
        BlocklistManager.initialize(applicationContext)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        btnToggleVpn = findViewById(R.id.btnToggleVpn)
        tvCertificate = findViewById(R.id.tvCertificate)

        btnToggleVpn.setOnClickListener {
            if (AdBlockVpnService.active) {
                startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
                refreshUi()
            } else {
                VpnService.prepare(this)?.let(vpnPermissionLauncher::launch) ?: startVpnService()
            }
        }
        findViewById<Button>(R.id.btnOpenYoutube).setOnClickListener { openBrowser("https://m.youtube.com") }
        findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener { openBrowser("https://duckduckgo.com") }
        findViewById<Button>(R.id.btnUpdateHosts).setOnClickListener { button ->
            button.isEnabled = false
            (button as Button).text = "Updating blocklists…"
            BlocklistManager.syncRemoteHosts(applicationContext) { count, error ->
                runOnUiThread {
                    button.isEnabled = true
                    button.text = getString(R.string.update_blocklists)
                    toast(error ?: "Blocklist updated · $count rules")
                    refreshUi()
                }
            }
        }
        findViewById<Button>(R.id.btnAddDomain).setOnClickListener {
            val input = findViewById<EditText>(R.id.etCustomDomain)
            val domain = input.text.toString()
            if (BlocklistManager.addCustomDomain(this, domain)) {
                input.text.clear(); toast("Domain added to blocklist"); refreshUi()
            } else toast("Enter a valid domain, e.g. ads.example.com")
        }
        findViewById<Button>(R.id.btnCreateCa).setOnClickListener {
            try {
                CertificateAuthorityManager.ensureCreated(this)
                refreshUi()
                toast("Local CA created. Private key stays in Android Keystore.")
            } catch (e: Exception) { toast("Could not create CA: ${e.message}") }
        }
        findViewById<Button>(R.id.btnInstallCa).setOnClickListener {
            try { certificateInstaller.launch(CertificateAuthorityManager.installIntent(this)) }
            catch (e: Exception) { toast("Could not open Android certificate installer: ${e.message}") }
        }
        refreshUi()
    }

    private fun openBrowser(url: String) = startActivity(Intent(this, AdFreeWebActivity::class.java).putExtra("URL", url))

    private fun startVpnService() {
        val service = Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        refreshUi()
    }

    private fun refreshUi() {
        val active = AdBlockVpnService.active
        tvStatus.text = if (active) "PROTECTION ACTIVE  ·  DNS FILTERING" else "PROTECTION PAUSED"
        tvStatus.setTextColor(if (active) Color.rgb(98, 235, 177) else Color.rgb(255, 178, 106))
        btnToggleVpn.text = if (active) "Pause protection" else "Start protection"
        tvStats.text = "${BlocklistManager.blockedCount.get()}\nRequests blocked\n\n${BlocklistManager.getRuleCount()}\nActive domain rules"
        tvCertificate.text = if (CertificateAuthorityManager.certificateExists(this))
            "Local CA created · installation is user-controlled" else "No local CA created"
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    override fun onResume() { super.onResume(); refreshUi() }
=======
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
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
}
