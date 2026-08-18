package com.pocketbot.core

import java.time.Instant
import java.time.ZoneOffset

/**
 * Guardas de gestão de risco: limites diários, operações simultâneas e
 * cooldown após perda. Porta de pocket_bot/risk.py. `nowMillis` é
 * injetável para permitir testes determinísticos.
 */
class RiskManager(
    private val cfg: RiskConfig,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var day = currentUtcDay()
    var dailyPnl: Double = 0.0
        private set
    private var tradesToday = 0
    private var openTrades = 0
    private var lastLossAtMillis: Long? = null

    private fun currentUtcDay(): Long =
        Instant.ofEpochMilli(nowMillis()).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    private fun rollDayIfNeeded() {
        val today = currentUtcDay()
        if (today != day) {
            day = today
            dailyPnl = 0.0
            tradesToday = 0
        }
    }

    data class Decision(val canTrade: Boolean, val reason: String = "")

    fun canTrade(): Decision {
        rollDayIfNeeded()
        if (dailyPnl <= -kotlin.math.abs(cfg.maxDailyLoss)) {
            return Decision(false, "limite de perda diária atingido")
        }
        if (tradesToday >= cfg.maxTradesPerDay) {
            return Decision(false, "limite de operações diárias atingido")
        }
        if (openTrades >= cfg.maxConcurrentTrades) {
            return Decision(false, "número máximo de operações simultâneas atingido")
        }
        lastLossAtMillis?.let {
            val elapsedSeconds = (nowMillis() - it) / 1000
            if (elapsedSeconds < cfg.cooldownAfterLossSeconds) {
                val remaining = cfg.cooldownAfterLossSeconds - elapsedSeconds
                return Decision(false, "em cooldown após perda (${remaining}s restantes)")
            }
        }
        return Decision(true)
    }

    fun registerOpen() {
        openTrades += 1
        tradesToday += 1
    }

    fun registerResult(profit: Double) {
        openTrades = maxOf(0, openTrades - 1)
        dailyPnl += profit
        if (profit < 0) {
            lastLossAtMillis = nowMillis()
        }
    }
}
