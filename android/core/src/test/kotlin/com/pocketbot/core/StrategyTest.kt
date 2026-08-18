package com.pocketbot.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrategyTest {

    private fun candlesFromSteps(seed: Int, n: Int = 150): List<Candle> {
        val rnd = Random(seed)
        val closes = mutableListOf(1.10000)
        repeat(n - 1) { closes.add(closes.last() + rnd.nextDouble(-0.00013, 0.00017)) }
        return closes.mapIndexed { i, c ->
            Candle(
                time = i.toLong(),
                open = c,
                high = c + kotlin.math.abs(rnd.nextDouble(0.0, 0.00006)),
                low = c - kotlin.math.abs(rnd.nextDouble(0.0, 0.00006)),
                close = c,
            )
        }
    }

    @Test
    fun `not enough candles yields no signal`() {
        val cfg = StrategyConfig()
        assertNull(Strategy.evaluate("EURUSD_otc", emptyList(), cfg))
        assertNull(Strategy.evaluate("EURUSD_otc", candlesFromSteps(1, 2), cfg))
    }

    @Test
    fun `at least some seeds produce a signal, and scores are within 0 and 1`() {
        val cfg = StrategyConfig()
        var hits = 0
        for (seed in 0 until 60) {
            val signal = Strategy.evaluate("EURUSD_otc", candlesFromSteps(seed), cfg)
            if (signal != null) {
                hits++
                assertTrue(signal.score in 0.0..1.0, "score fora de [0,1]: ${signal.score}")
                assertTrue(signal.pair == "EURUSD_otc")
            }
        }
        assertTrue(hits > 0, "esperava pelo menos um sinal entre 60 séries sintéticas")
    }

    @Test
    fun `pickBest chooses the highest score above the threshold`() {
        val cfg = StrategyConfig(minSignalScore = 0.3)
        val signals = listOf(
            Signal("A", TradeAction.CALL, 0.2, "baixo"),
            Signal("B", TradeAction.PUT, 0.5, "melhor"),
            Signal("C", TradeAction.CALL, 0.4, "medio"),
        )
        val best = Strategy.pickBest(signals, cfg)
        assertTrue(best?.pair == "B")
    }

    @Test
    fun `pickBest returns null when nothing clears the threshold`() {
        val cfg = StrategyConfig(minSignalScore = 0.9)
        val signals = listOf(Signal("A", TradeAction.CALL, 0.2, "baixo"))
        assertNull(Strategy.pickBest(signals, cfg))
    }
}
