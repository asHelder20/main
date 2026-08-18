package com.pocketbot.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiskManagerTest {

    @Test
    fun `can trade initially`() {
        val rm = RiskManager(RiskConfig())
        assertTrue(rm.canTrade().canTrade)
    }

    @Test
    fun `cooldown blocks trading right after a loss`() {
        var now = 1_000_000L
        val rm = RiskManager(RiskConfig(cooldownAfterLossSeconds = 60)) { now }
        rm.registerOpen()
        rm.registerResult(-5.0)

        assertFalse(rm.canTrade().canTrade, "deveria estar em cooldown logo após a perda")

        now += 61_000
        assertTrue(rm.canTrade().canTrade, "cooldown deveria ter expirado")
    }

    @Test
    fun `daily loss limit blocks further trading`() {
        val rm = RiskManager(RiskConfig(maxDailyLoss = 10.0, cooldownAfterLossSeconds = 0))
        rm.registerOpen()
        rm.registerResult(-10.0)
        assertFalse(rm.canTrade().canTrade)
    }

    @Test
    fun `max concurrent trades blocks a new entry until one closes`() {
        val rm = RiskManager(RiskConfig(maxConcurrentTrades = 1))
        rm.registerOpen()
        assertFalse(rm.canTrade().canTrade)
        rm.registerResult(1.0)
        assertTrue(rm.canTrade().canTrade)
    }

    @Test
    fun `max trades per day blocks further entries`() {
        val rm = RiskManager(RiskConfig(maxTradesPerDay = 1, maxConcurrentTrades = 10, cooldownAfterLossSeconds = 0))
        rm.registerOpen()
        rm.registerResult(1.0)
        assertFalse(rm.canTrade().canTrade)
    }
}
