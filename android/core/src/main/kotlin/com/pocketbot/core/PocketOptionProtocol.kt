package com.pocketbot.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Construção/leitura das mensagens de aplicação da Pocket Option, por cima
 * do framing de SocketIoFrame. Formatos extraídos lendo o código-fonte real
 * de um cliente Python não-oficial (ver README-ANDROID.md) - não foram
 * inventados, mas também não foram exercitados contra o servidor real a
 * partir daqui (sem SDK Android/dispositivo neste ambiente).
 */
object PocketOptionProtocol {

    // Servidores conhecidos (mesma lista usada pelos clientes de referência).
    const val WS_URL_DEMO = "wss://demo-api-eu.po.market/socket.io/?EIO=4&transport=websocket"
    const val WS_URL_REAL = "wss://api-eu.po.market/socket.io/?EIO=4&transport=websocket"

    fun wsUrl(demo: Boolean): String = if (demo) WS_URL_DEMO else WS_URL_REAL

    // ---- frames de controlo Engine.IO ----
    fun engineConnectAck(): String = "40"
    fun enginePong(): String = "3"

    /** Ping de aplicação que a Pocket Option espera a cada ~20s, além do ping/pong do Engine.IO. */
    fun appPing(): String = "42[\"ps\"]"

    // ---- eventos enviados pelo cliente ----

    fun changeSymbol(asset: String, periodSeconds: Int): String {
        val payload = JSONObject().put("asset", asset).put("period", periodSeconds)
        return "42" + JSONArray().put("changeSymbol").put(payload).toString()
    }

    private fun offsetForPeriod(periodSeconds: Int): Int = when (periodSeconds) {
        5 -> 1000; 10 -> 2000; 15 -> 3000; 30 -> 6000; 60 -> 9000
        120 -> 18000; 180 -> 27000; 300 -> 45000; 600 -> 90000
        900 -> 135000; 1800 -> 270000; 3600 -> 540000
        14400 -> 2160000; 86400 -> 12960000
        else -> 9000
    }

    /** Pede um lote de candles históricos fechados (para preencher o buffer inicial de um par). */
    fun loadHistoryPeriod(asset: String, periodSeconds: Int, endTimeEpochSeconds: Long): String {
        val payload = JSONObject()
            .put("asset", asset)
            .put("index", Random.nextInt(5000, 10000))
            .put("time", endTimeEpochSeconds + 7200)
            .put("offset", offsetForPeriod(periodSeconds))
            .put("period", periodSeconds)
        return "42" + JSONArray().put("loadHistoryPeriod").put(payload).toString()
    }

    /** Envia uma ordem de compra (CALL) ou venda (PUT). requestId é sempre "buy" nos clientes de referência. */
    fun openOrder(
        asset: String,
        amount: Double,
        action: TradeAction,
        isDemo: Boolean,
        durationSeconds: Int,
        requestId: String = "buy",
    ): String {
        val payload = JSONObject()
            .put("asset", asset)
            .put("amount", amount)
            .put("action", if (action == TradeAction.CALL) "call" else "put")
            .put("isDemo", if (isDemo) 1 else 0)
            .put("requestId", requestId)
            .put("optionType", 100)
            .put("time", durationSeconds)
        return "42" + JSONArray().put("openOrder").put(payload).toString()
    }

    // ---- parsing de payloads binários (o servidor envia os dados de aplicação como frames binários) ----

    fun parseBalanceUpdate(json: JSONObject): BalanceUpdate? {
        if (!json.has("balance")) return null
        val isDemo = json.optInt("isDemo", -1)
        return BalanceUpdate(json.getDouble("balance"), isDemo == 1)
    }

    fun parseOrderConfirmation(json: JSONObject): OrderConfirmation? {
        if (json.optString("requestId") != "buy") return null
        val id = if (json.has("id")) json.optString("id") else null
        return OrderConfirmation(id, json.toString())
    }

    /** Resposta de loadHistoryPeriod: {"data": [{"time":..,"open":..,"high":..,"low":..,"close":..}, ...]} */
    fun parseHistoryCandles(json: JSONObject): List<Candle>? {
        val data = json.optJSONArray("data") ?: return null
        val candles = mutableListOf<Candle>()
        for (i in 0 until data.length()) {
            val c = data.optJSONObject(i) ?: continue
            candles.add(
                Candle(
                    time = c.optLong("time"),
                    open = c.optDouble("open"),
                    high = c.optDouble("high"),
                    low = c.optDouble("low"),
                    close = c.optDouble("close"),
                )
            )
        }
        return candles.sortedBy { it.time }
    }

    data class Tick(val symbol: String, val timeEpochSeconds: Long, val price: Double)

    /** Payload de tick após um marcador "updateStream": lista com um item [symbol, time, price]. */
    fun parseTick(json: JSONArray): Tick? {
        if (json.length() == 0) return null
        val entry = json.optJSONArray(0) ?: return null
        if (entry.length() < 3) return null
        return Tick(
            symbol = entry.optString(0),
            timeEpochSeconds = entry.optLong(1),
            price = entry.optDouble(2),
        )
    }
}
