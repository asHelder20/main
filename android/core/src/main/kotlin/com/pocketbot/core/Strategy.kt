package com.pocketbot.core

import kotlin.math.abs
import kotlin.math.min

/**
 * A "EA": mesmo algoritmo de pocket_bot/strategy.py (Python) - reversão do
 * Parabolic SAR como sinal principal, RSI como filtro de exaustão e largura
 * das Bandas de Bollinger como filtro de volatilidade mínima.
 */
object Strategy {

    fun evaluate(pair: String, candles: List<Candle>, cfg: StrategyConfig): Signal? {
        if (candles.size < 3) return null

        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val closes = candles.map { it.close }

        val sar = Indicators.psar(highs, lows, cfg.psarAfStep, cfg.psarAfMax)
        val rsiSeries = Indicators.rsi(closes, cfg.rsiPeriod)

        val lastIdx = closes.size - 1
        val lastTrend = closes[lastIdx] > sar[lastIdx]
        val prevTrend = closes[lastIdx - 1] > sar[lastIdx - 1]
        if (lastTrend == prevTrend) return null // só entra logo após a reversão do PSAR

        val lastClose = closes[lastIdx]
        val lastSar = sar[lastIdx]
        val bb = Indicators.bollingerBandsAt(closes, lastIdx, cfg.bbPeriod, cfg.bbStdMult)
        val lastRsi = rsiSeries[lastIdx]

        if (bb.width == null || lastRsi == null || lastClose == 0.0) return null
        if (bb.width < cfg.minBbWidth) return null // volatilidade insuficiente

        val distance = abs(lastClose - lastSar) / lastClose
        // fator de escala empírico para variações típicas de preço nos pares OTC
        val score = min(1.0, distance * 400)

        return if (lastTrend) {
            if (lastRsi >= cfg.rsiUpper) null // alta possivelmente esgotada (sobrecompra)
            else Signal(pair, TradeAction.CALL, score, "PSAR reverteu para alta, sem sobrecompra")
        } else {
            if (lastRsi <= cfg.rsiLower) null // baixa possivelmente esgotada (sobrevenda)
            else Signal(pair, TradeAction.PUT, score, "PSAR reverteu para baixa, sem sobrevenda")
        }
    }

    /** Escolhe, entre os sinais gerados por vários pares no mesmo ciclo, o de maior score. */
    fun pickBest(signals: List<Signal>, cfg: StrategyConfig): Signal? =
        signals.filter { it.score >= cfg.minSignalScore }.maxByOrNull { it.score }
}
