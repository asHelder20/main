package com.pocketbot.core

data class RiskConfig(
    val maxDailyLoss: Double = 20.0,
    val maxTradesPerDay: Int = 20,
    val maxConcurrentTrades: Int = 1,
    val cooldownAfterLossSeconds: Long = 60,
)
