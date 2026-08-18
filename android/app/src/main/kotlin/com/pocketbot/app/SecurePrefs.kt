package com.pocketbot.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda o SSID e a configuração do bot cifrados em disco (AES-256), em vez
 * de texto simples - o SSID dá acesso total à conta da Pocket Option.
 */
class SecurePrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pocket_bot_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var ssid: String
        get() = prefs.getString(KEY_SSID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SSID, value).apply()

    var liveTradingConfirmed: Boolean
        get() = prefs.getBoolean(KEY_LIVE_CONFIRMED, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_CONFIRMED, value).apply()

    var stakeAmount: Double
        get() = prefs.getFloat(KEY_STAKE, 1.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_STAKE, value.toFloat()).apply()

    var maxDailyLoss: Double
        get() = prefs.getFloat(KEY_MAX_LOSS, 20.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_MAX_LOSS, value.toFloat()).apply()

    var expirySeconds: Int
        get() = prefs.getInt(KEY_EXPIRY, 60)
        set(value) = prefs.edit().putInt(KEY_EXPIRY, value).apply()

    var pairsCsv: String
        get() = prefs.getString(KEY_PAIRS, DEFAULT_PAIRS) ?: DEFAULT_PAIRS
        set(value) = prefs.edit().putString(KEY_PAIRS, value).apply()

    fun pairs(): List<String> = pairsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private const val KEY_SSID = "ssid"
        private const val KEY_LIVE_CONFIRMED = "live_trading_confirmed"
        private const val KEY_STAKE = "stake_amount"
        private const val KEY_MAX_LOSS = "max_daily_loss"
        private const val KEY_EXPIRY = "expiry_seconds"
        private const val KEY_PAIRS = "pairs"
        const val DEFAULT_PAIRS =
            "EURUSD_otc,GBPUSD_otc,USDJPY_otc,EURJPY_otc,AUDCAD_otc,USDCHF_otc,NZDUSD_otc,EURGBP_otc"
    }
}
