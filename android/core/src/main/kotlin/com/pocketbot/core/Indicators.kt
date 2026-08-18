package com.pocketbot.core

import kotlin.math.min

/**
 * Indicadores técnicos. Porta direta da versão Python já testada em
 * pocket_bot/indicators.py (mesmas fórmulas), para manter a estratégia
 * idêntica entre a VPS e o app Android.
 */
object Indicators {

    /** RSI de Wilder (média móvel exponencial dos ganhos/perdas). */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double?> {
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < 2) return result

        val alpha = 1.0 / period
        var avgGain = 0.0
        var avgLoss = 0.0
        var initialized = false

        for (i in 1 until closes.size) {
            val delta = closes[i] - closes[i - 1]
            val gain = if (delta > 0) delta else 0.0
            val loss = if (delta < 0) -delta else 0.0
            if (!initialized) {
                avgGain = gain
                avgLoss = loss
                initialized = true
            } else {
                avgGain = avgGain + alpha * (gain - avgGain)
                avgLoss = avgLoss + alpha * (loss - avgLoss)
            }
            result[i] = if (avgLoss == 0.0) {
                if (avgGain == 0.0) null else 100.0
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
        }
        return result
    }

    data class BollingerBands(val upper: Double?, val mid: Double?, val lower: Double?, val width: Double?)

    /** Bandas de Bollinger para o último ponto de uma janela de closes. */
    fun bollingerBandsAt(closes: List<Double>, index: Int, period: Int = 20, stdMult: Double = 2.0): BollingerBands {
        if (index + 1 < period) return BollingerBands(null, null, null, null)
        val window = closes.subList(index + 1 - period, index + 1)
        val mid = window.average()
        val variance = window.sumOf { (it - mid) * (it - mid) } / period
        val std = kotlin.math.sqrt(variance)
        val upper = mid + stdMult * std
        val lower = mid - stdMult * std
        val width = if (mid != 0.0) (upper - lower) / mid else null
        return BollingerBands(upper, mid, lower, width)
    }

    /**
     * Parabolic SAR (Wilder). Mesma implementação de pocket_bot/indicators.py.
     * Retorna a série de valores do SAR; close > sar indica tendência de alta.
     */
    fun psar(highs: List<Double>, lows: List<Double>, afStep: Double = 0.02, afMax: Double = 0.2): List<Double> {
        val n = highs.size
        val sar = MutableList(n) { 0.0 }
        if (n == 0) return sar

        var bull = true
        var af = afStep
        var ep = highs[0]
        sar[0] = lows[0]

        for (i in 1 until n) {
            val prevSar = sar[i - 1]
            var currSar = prevSar + af * (ep - prevSar)

            if (bull) {
                val floor1 = lows[i - 1]
                val floor2 = if (i >= 2) lows[i - 2] else lows[i - 1]
                currSar = min(currSar, min(floor1, floor2))
                if (lows[i] < currSar) {
                    bull = false
                    currSar = ep
                    ep = lows[i]
                    af = afStep
                } else if (highs[i] > ep) {
                    ep = highs[i]
                    af = min(af + afStep, afMax)
                }
            } else {
                val ceil1 = highs[i - 1]
                val ceil2 = if (i >= 2) highs[i - 2] else highs[i - 1]
                currSar = maxOf(currSar, ceil1, ceil2)
                if (highs[i] > currSar) {
                    bull = true
                    currSar = ep
                    ep = highs[i]
                    af = afStep
                } else if (lows[i] < ep) {
                    ep = lows[i]
                    af = min(af + afStep, afMax)
                }
            }
            sar[i] = currSar
        }
        return sar
    }
}
