package com.pocketbot.core

/** Um candle OHLC. `time` é o timestamp Unix (segundos) de abertura do candle. */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

/** Sinal de entrada gerado pela estratégia para um par. */
data class Signal(
    val pair: String,
    val action: TradeAction,
    val score: Double,
    val reason: String,
)

enum class TradeAction { CALL, PUT }

data class BalanceUpdate(
    val balance: Double,
    val isDemo: Boolean,
)

data class OrderConfirmation(
    val orderId: String?,
    val raw: String,
)

data class TradeResult(
    val orderId: String,
    val result: String, // "win" | "loss" | "draw"
    val profit: Double,
)

data class AssetInfo(
    val symbol: String,
    val isActive: Boolean,
    val allowedCandles: List<Int>,
)
