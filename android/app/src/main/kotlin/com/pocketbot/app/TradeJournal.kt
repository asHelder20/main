package com.pocketbot.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro CSV de operações, guardado no armazenamento privado do app. */
class TradeJournal(context: Context) {
    private val file = File(context.filesDir, "trade_journal.csv")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    init {
        if (!file.exists()) {
            file.writeText("timestamp,pair,action,amount,expiry_seconds,order_id,score,reason,result,profit\n")
        }
    }

    fun recordEntry(pair: String, action: String, amount: Double, expirySeconds: Int, orderId: String?, score: Double, reason: String) {
        appendRow(pair, action, amount.toString(), expirySeconds.toString(), orderId ?: "", score.toString(), reason, "", "")
    }

    fun recordResult(pair: String, action: String, orderId: String, result: String, profit: Double) {
        appendRow(pair, action, "", "", orderId, "", "", result, profit.toString())
    }

    private fun appendRow(pair: String, action: String, amount: String, expiry: String, orderId: String, score: String, reason: String, result: String, profit: String) {
        val timestamp = dateFormat.format(Date())
        val fields = listOf(timestamp, pair, action, amount, expiry, orderId, score, csvEscape(reason), result, profit)
        file.appendText(fields.joinToString(",") + "\n")
    }

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"")) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
