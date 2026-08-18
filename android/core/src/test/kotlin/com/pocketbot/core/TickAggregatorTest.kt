package com.pocketbot.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TickAggregatorTest {

    @Test
    fun `builds a single forming candle from ticks within the same bucket`() {
        // bucket de 60s [960,1020): todos os tempos abaixo caem nele
        val agg = TickAggregator(periodSeconds = 60, maxClosedCandles = 10)
        agg.onTick(960, 1.10)
        agg.onTick(970, 1.12)
        agg.onTick(1000, 1.08)
        agg.onTick(1019, 1.11)

        val snap = agg.snapshot()
        assertEquals(1, snap.size)
        val c = snap.first()
        assertEquals(960L, c.time)
        assertEquals(1.10, c.open)
        assertEquals(1.12, c.high)
        assertEquals(1.08, c.low)
        assertEquals(1.11, c.close)
    }

    @Test
    fun `closes the previous candle when a tick crosses into the next bucket`() {
        val agg = TickAggregator(periodSeconds = 60, maxClosedCandles = 10)
        agg.onTick(1000, 1.10) // bucket 960 ([960,1020))
        agg.onTick(1015, 1.15) // ainda bucket 960
        agg.onTick(1025, 1.20) // bucket 1020 ([1020,1080)) -> fecha o candle anterior

        val snap = agg.snapshot()
        assertEquals(2, snap.size)
        assertEquals(960L, snap[0].time)
        assertEquals(1.15, snap[0].close) // fechou com o último preço do bucket anterior
        assertEquals(1020L, snap[1].time)
        assertEquals(1.20, snap[1].open)
    }

    @Test
    fun `respects the max closed candles cap`() {
        val agg = TickAggregator(periodSeconds = 1, maxClosedCandles = 3)
        for (t in 0 until 10) {
            agg.onTick(t.toLong(), 1.0 + t * 0.01)
        }
        val snap = agg.snapshot()
        // 3 fechados + 1 em formação
        assertEquals(4, snap.size)
    }

    @Test
    fun `seed preloads historical closed candles`() {
        val agg = TickAggregator(periodSeconds = 60, maxClosedCandles = 5)
        agg.seed(listOf(Candle(0, 1.0, 1.0, 1.0, 1.0), Candle(60, 1.01, 1.01, 1.01, 1.01)))
        assertEquals(2, agg.snapshot().size)
    }

    @Test
    fun `ignores out-of-order ticks older than the forming candle`() {
        val agg = TickAggregator(periodSeconds = 60, maxClosedCandles = 10)
        agg.onTick(1200, 1.30) // bucket 1200
        agg.onTick(1000, 9.99) // bucket 960, mais antigo -> deve ser ignorado
        val snap = agg.snapshot()
        assertEquals(1, snap.size)
        assertEquals(1.30, snap.first().close)
    }
}
