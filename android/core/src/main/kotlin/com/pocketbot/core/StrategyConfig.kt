package com.pocketbot.core

data class StrategyConfig(
    val psarAfStep: Double = 0.02,
    val psarAfMax: Double = 0.2,
    val rsiPeriod: Int = 14,
    val rsiLower: Double = 30.0,
    val rsiUpper: Double = 70.0,
    val bbPeriod: Int = 20,
    val bbStdMult: Double = 2.0,
    val minBbWidth: Double = 0.0006,
    val minSignalScore: Double = 0.35,
) {
    /** Nº mínimo de candles fechados necessários antes de a estratégia avaliar um par. */
    fun minHistory(): Int = maxOf(bbPeriod, rsiPeriod, 20) + 5
}
