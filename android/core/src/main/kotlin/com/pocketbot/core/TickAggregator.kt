package com.pocketbot.core

/**
 * Agrega ticks (preço a preço) em candles OHLC de um período fixo, mantendo
 * um histórico limitado de candles fechados mais o candle em formação -
 * equivalente ao que o get_candles_live da BinaryOptionsToolsV2 faz no bot
 * em Python, mas implementado aqui porque a Pocket Option só entrega ticks
 * crus pelo WebSocket (o histórico inicial vem à parte, por loadHistoryPeriod).
 */
class TickAggregator(
    private val periodSeconds: Int,
    private val maxClosedCandles: Int,
) {
    private val closed = ArrayDeque<Candle>()
    private var forming: Candle? = null

    /** Preenche o histórico inicial (chamado uma vez, com a resposta de loadHistoryPeriod). */
    fun seed(history: List<Candle>) {
        closed.clear()
        history.sortedBy { it.time }.takeLast(maxClosedCandles).forEach { closed.addLast(it) }
    }

    private fun bucketStart(timeEpochSeconds: Long): Long =
        timeEpochSeconds - (timeEpochSeconds % periodSeconds)

    /** Processa um novo tick, fechando o candle em formação se o tick pertencer ao próximo período. */
    fun onTick(timeEpochSeconds: Long, price: Double) {
        val bucket = bucketStart(timeEpochSeconds)
        val current = forming

        if (current == null || bucket > current.time) {
            if (current != null) {
                closed.addLast(current)
                while (closed.size > maxClosedCandles) closed.removeFirst()
            }
            forming = Candle(time = bucket, open = price, high = price, low = price, close = price)
            return
        }

        if (bucket < current.time) return // tick atrasado/fora de ordem, ignora

        forming = current.copy(
            high = maxOf(current.high, price),
            low = minOf(current.low, price),
            close = price,
        )
    }

    /** Candles fechados + candle em formação (se já houver), ordenados por tempo - pronto para a estratégia. */
    fun snapshot(): List<Candle> {
        val result = closed.toMutableList()
        forming?.let { result.add(it) }
        return result
    }
}
