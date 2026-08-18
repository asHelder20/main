package com.pocketbot.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class IndicatorsTest {

    @Test
    fun `psar stays below price during a strong uptrend`() {
        val rnd = Random(1)
        val n = 80
        val closes = (0 until n).runningFold(1.1000) { acc, _ -> acc + rnd.nextDouble(0.00002, 0.00015) }.drop(1)
        val highs = closes.map { it + 0.00004 }
        val lows = closes.map { it - 0.00004 }

        val sar = Indicators.psar(highs, lows)

        assertTrue(sar.last() < closes.last(), "SAR deveria ficar abaixo do preço numa tendência de alta forte")
    }

    @Test
    fun `psar stays above price during a strong downtrend`() {
        val rnd = Random(2)
        val n = 80
        val closes = (0 until n).runningFold(1.1000) { acc, _ -> acc - rnd.nextDouble(0.00002, 0.00015) }.drop(1)
        val highs = closes.map { it + 0.00004 }
        val lows = closes.map { it - 0.00004 }

        val sar = Indicators.psar(highs, lows)

        assertTrue(sar.last() > closes.last(), "SAR deveria ficar acima do preço numa tendência de baixa forte")
    }

    @Test
    fun `rsi is high after a sustained rally and low after a sustained selloff`() {
        val up = (0..30).map { 1.0 + it * 0.001 }
        val rsiUp = Indicators.rsi(up, 14)
        assertTrue(rsiUp.last()!! > 70.0, "RSI deveria estar alto após alta sustentada, foi ${rsiUp.last()}")

        val down = (0..30).map { 1.0 - it * 0.001 }
        val rsiDown = Indicators.rsi(down, 14)
        assertTrue(rsiDown.last()!! < 30.0, "RSI deveria estar baixo após queda sustentada, foi ${rsiDown.last()}")
    }

    @Test
    fun `bollinger width is zero for a flat series and positive for a volatile one`() {
        val flat = List(30) { 1.1 }
        val bbFlat = Indicators.bollingerBandsAt(flat, flat.size - 1, period = 20)
        assertTrue(bbFlat.width == 0.0, "largura deveria ser zero numa série constante, foi ${bbFlat.width}")

        val volatile = (0 until 30).map { 1.1 + if (it % 2 == 0) 0.01 else -0.01 }
        val bbVol = Indicators.bollingerBandsAt(volatile, volatile.size - 1, period = 20)
        assertTrue(bbVol.width!! > 0.0, "largura deveria ser positiva numa série volátil")
    }
}
