package com.netshield.adblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
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
    private lateinit var tvCertificate: TextView

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
        else toast("VPN permission was not granted")
    }
    private val certificateInstaller = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshUi()
        toast("If Android accepted the certificate, review it in Security settings")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
