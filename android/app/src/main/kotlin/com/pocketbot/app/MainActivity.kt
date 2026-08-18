package com.pocketbot.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SecurePrefs
    private lateinit var inputSsid: TextInputEditText
    private lateinit var inputPairs: TextInputEditText
    private lateinit var inputStake: TextInputEditText
    private lateinit var inputMaxLoss: TextInputEditText
    private lateinit var inputExpiry: TextInputEditText
    private lateinit var switchLiveConfirmed: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var textLog: android.widget.TextView
    private lateinit var textStatus: android.widget.TextView

    private val logLines = ArrayDeque<String>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional; sem notificação o serviço continua a correr */ }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(TradingService.EXTRA_LOG) ?: return
            logLines.addLast(message)
            while (logLines.size > 200) logLines.removeFirst()
            textLog.text = logLines.joinToString("\n")
            textStatus.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SecurePrefs(this)

        inputSsid = findViewById(R.id.inputSsid)
        inputPairs = findViewById(R.id.inputPairs)
        inputStake = findViewById(R.id.inputStake)
        inputMaxLoss = findViewById(R.id.inputMaxLoss)
        inputExpiry = findViewById(R.id.inputExpiry)
        switchLiveConfirmed = findViewById(R.id.switchLiveConfirmed)
        textLog = findViewById(R.id.textLog)
        textStatus = findViewById(R.id.textStatus)

        loadPrefsIntoForm()

        findViewById<android.widget.Button>(R.id.buttonStart).setOnClickListener { onStartClicked() }
        findViewById<android.widget.Button>(R.id.buttonStop).setOnClickListener { onStopClicked() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(TradingService.ACTION_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(logReceiver)
    }

    private fun loadPrefsIntoForm() {
        inputSsid.setText(prefs.ssid)
        inputPairs.setText(prefs.pairsCsv)
        inputStake.setText(prefs.stakeAmount.toString())
        inputMaxLoss.setText(prefs.maxDailyLoss.toString())
        inputExpiry.setText(prefs.expirySeconds.toString())
        switchLiveConfirmed.isChecked = prefs.liveTradingConfirmed
    }

    private fun savePrefsFromForm() {
        prefs.ssid = inputSsid.text?.toString()?.trim().orEmpty()
        prefs.pairsCsv = inputPairs.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: SecurePrefs.DEFAULT_PAIRS
        prefs.stakeAmount = inputStake.text?.toString()?.toDoubleOrNull() ?: 1.0
        prefs.maxDailyLoss = inputMaxLoss.text?.toString()?.toDoubleOrNull() ?: 20.0
        prefs.expirySeconds = inputExpiry.text?.toString()?.toIntOrNull() ?: 60
        prefs.liveTradingConfirmed = switchLiveConfirmed.isChecked
    }

    private fun onStartClicked() {
        savePrefsFromForm()

        if (prefs.ssid.isBlank()) {
            AlertDialog.Builder(this).setMessage("Preenche o SSID antes de iniciar.").setPositiveButton("OK", null).show()
            return
        }

        if (switchLiveConfirmed.isChecked) {
            AlertDialog.Builder(this)
                .setTitle("Confirmar operação com dinheiro real")
                .setMessage(
                    "Marcaste a confirmação de conta REAL. O bot só vai operar dinheiro " +
                        "real se a própria corretora confirmar que a conta é real. Se o SSID " +
                        "for de conta demo, esta confirmação é ignorada. Tens a certeza que " +
                        "queres continuar?"
                )
                .setPositiveButton("Sim, continuar") { _, _ -> startService() }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            startService()
        }
    }

    private fun startService() {
        val intent = Intent(this, TradingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        textStatus.text = "A iniciar..."
    }

    private fun onStopClicked() {
        val intent = Intent(this, TradingService::class.java).setAction(TradingService.ACTION_STOP)
        startService(intent)
        textStatus.text = getString(R.string.status_stopped)
    }
}
