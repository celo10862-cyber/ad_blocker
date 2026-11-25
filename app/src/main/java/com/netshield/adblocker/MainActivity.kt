package com.netshield.adblocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusPill: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvRuleCount: TextView
    private lateinit var btnToggleVpn: MaterialButton
    private val uiHandler = Handler(Looper.getMainLooper())

    private val vpnPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
        else Toast.makeText(this, "VPN permission is needed for DNS filtering.", Toast.LENGTH_LONG).show()
    }

    private val statsUpdater = object : Runnable {
        override fun run() {
            updateUiState()
            uiHandler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BlocklistManager.initialize(this)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvStatusPill = findViewById(R.id.tvStatusPill)
        tvStatusDescription = findViewById(R.id.tvStatusDescription)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)
        tvRuleCount = findViewById(R.id.tvRuleCount)
        btnToggleVpn = findViewById(R.id.btnToggleVpn)

        btnToggleVpn.setOnClickListener {
            if (AdBlockVpnService.active) {
                startService(Intent(this, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_STOP
                })
            } else {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent)
                else startVpnService()
            }
        }

        findViewById<MaterialButton>(R.id.btnTheme).setOnClickListener { showThemeDialog() }
        findViewById<MaterialButton>(R.id.btnApps).setOnClickListener { showAppSelectionDialog() }
        findViewById<MaterialButton>(R.id.btnResetStats).setOnClickListener {
            BlocklistManager.resetStatistics(this)
            updateUiState()
            Toast.makeText(this, "Statistics reset", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnCertificate).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Install NetShield local CA?")
                .setMessage("Android will ask you to install a user CA certificate. This certificate does not enable HTTPS inspection by itself. Only install it if you understand the security implications. You can remove it later in Android Security settings.")
                .setPositiveButton("Continue") { _, _ ->
                    runCatching { startActivity(CertificateAuthorityManager.createInstallIntent(this)) }
                        .onFailure { Toast.makeText(this, "Certificate setup failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<RadioGroup>(R.id.rgMode).apply {
            check(
                when (BlocklistManager.mode) {
                    ProtectionMode.STANDARD -> R.id.modeStandard
                    ProtectionMode.BALANCED -> R.id.modeBalanced
                    ProtectionMode.STRICT -> R.id.modeStrict
                }
            )
            setOnCheckedChangeListener { _, checkedId ->
                val selected = when (checkedId) {
                    R.id.modeStandard -> ProtectionMode.STANDARD
                    R.id.modeStrict -> ProtectionMode.STRICT
                    else -> ProtectionMode.BALANCED
                }
                BlocklistManager.setMode(this@MainActivity, selected)
                Toast.makeText(this@MainActivity, "${selected.name.lowercase().replaceFirstChar { it.uppercase() }} mode selected", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnUpdateHosts).setOnClickListener { button ->
            button.isEnabled = false
            button.text = "Updating filter lists…"
            BlocklistManager.syncRemoteHosts(this) { count, error ->
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "Update filter lists"
                    if (error == null) Toast.makeText(this, "Filter list updated · $count rules", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "Update failed: $error", Toast.LENGTH_LONG).show()
                    updateUiState()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnAddDomain).setOnClickListener {
            val input = findViewById<EditText>(R.id.etCustomDomain)
            val domain = input.text.toString()
            if (BlocklistManager.addCustomDomain(this, domain)) {
                input.text.clear()
                Toast.makeText(this, "Domain added to your blocklist", Toast.LENGTH_SHORT).show()
                updateUiState()
            } else {
                input.error = "Enter a valid domain, e.g. ads.example.com"
            }
        }
        findViewById<MaterialButton>(R.id.btnAllowDomain).setOnClickListener {
            val field = EditText(this).apply {
                hint = "example.com"
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
                setPadding(48, 24, 48, 12)
            }
            AlertDialog.Builder(this)
                .setTitle("Allow a domain")
                .setMessage("Allowed domains take priority over blocking rules.")
                .setView(field)
                .setPositiveButton("Allow") { _, _ ->
                    if (BlocklistManager.allowDomain(this, field.text.toString())) {
                        Toast.makeText(this, "Domain added to allowlist", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Enter a valid domain", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.btnOpenBrowser).setOnClickListener {
            startActivity(Intent(this, AdFreeWebActivity::class.java)
                .putExtra("URL", "https://duckduckgo.com"))
        }
        updateUiState()
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("netshield", MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "SYSTEM")
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
                "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun showThemeDialog() {
        val prefs = getSharedPreferences("netshield", MODE_PRIVATE)
        val choices = arrayOf("System default", "Light", "Dark")
        val values = arrayOf("SYSTEM", "LIGHT", "DARK")
        val current = prefs.getString("theme_mode", "SYSTEM")
        val checked = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Appearance")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                prefs.edit().putString("theme_mode", values[which]).apply()
                applySavedTheme()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppSelectionDialog() {
        val pm = packageManager
        val apps = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }.filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val selected = apps.map { BlocklistManager.allowedPackages.contains(it.packageName) }.toBooleanArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Apps using NetShield")
            .setMessage("Select apps that should use NetShield DNS filtering. If none are selected, all apps use the VPN DNS route.")
            .setMultiChoiceItems(labels, selected) { _, which, checked -> selected[which] = checked }
            .setPositiveButton("Save") { _, _ ->
                val packages = apps.indices.filter { selected[it] }.map { apps[it].packageName }.toSet()
                BlocklistManager.setAllowedPackages(this, packages)
                Toast.makeText(this, "App selection saved. Restart protection to apply it.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Select all") { _, _ ->
                BlocklistManager.setAllowedPackages(this, apps.map { it.packageName }.toSet())
                Toast.makeText(this, "All listed apps selected. Restart protection to apply.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVpnService() {
        ContextCompat.startForegroundService(this, Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        })
    }

    private fun updateUiState() {
        val active = AdBlockVpnService.active
        tvBlockedCount.text = BlocklistManager.blockedCount.get().toString()
        tvRuleCount.text = BlocklistManager.getRuleCount().toString()
        if (active) {
            tvStatus.text = "You're protected"
            tvStatusDescription.text = "NetShield is filtering supported DNS requests. Encrypted DNS and non-DNS traffic may bypass domain filtering."
            tvStatusPill.text = "● ACTIVE"
            btnToggleVpn.text = "Pause protection"
        } else {
            tvStatus.text = "Protection is paused"
            tvStatusDescription.text = "Enable DNS filtering to block matching ad and tracker domains."
            tvStatusPill.text = "● OFF"
            btnToggleVpn.text = "Enable protection"
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(statsUpdater)
        uiHandler.post(statsUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(statsUpdater)
    }
}
