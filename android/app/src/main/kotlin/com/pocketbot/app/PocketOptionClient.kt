package com.pocketbot.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pocketbot.core.BalanceUpdate
import com.pocketbot.core.Candle
import com.pocketbot.core.OrderConfirmation
import com.pocketbot.core.PocketOptionProtocol
import com.pocketbot.core.SocketIoFrame
import com.pocketbot.core.TradeAction
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente WebSocket para a Pocket Option. Protocolo (framing Engine.IO/
 * Socket.IO, formato das mensagens de auth/subscrição/ordens) extraído
 * lendo o código-fonte de um cliente Python não-oficial - ver
 * README-ANDROID.md para as fontes e para os avisos sobre o que NÃO foi
 * testado a partir deste projeto.
 *
 * Nota de protocolo: a resposta de loadHistoryPeriod não identifica o
 * ativo a que pertence, então só é seguro ter um pedido de histórico
 * pendente de cada vez (o próprio cliente de referência tem a mesma
 * limitação). TradingService subscreve os pares sequencialmente por isso.
 */
class PocketOptionClient(
    private val ssid: String,
    private val demo: Boolean,
    private val listener: Listener,
) {
    interface Listener {
        fun onAuthenticated()
        fun onNotAuthorized()
        fun onBalance(update: BalanceUpdate)
        fun onOrderConfirmed(confirmation: OrderConfirmation)
        fun onHistoryCandles(candles: List<Candle>)
        fun onTick(tick: PocketOptionProtocol.Tick)
        fun onDisconnected(reason: String)
        fun onLog(message: String)
    }

    private val httpClient = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null
    private var awaitingStreamTick = false
    private val handler = Handler(Looper.getMainLooper())

    private val pingRunnable = object : Runnable {
        override fun run() {
            webSocket?.send(PocketOptionProtocol.appPing())
            handler.postDelayed(this, 20_000)
        }
    }

    fun connect() {
        val request = Request.Builder()
            .url(PocketOptionProtocol.wsUrl(demo))
            .addHeader("Origin", "https://pocketoption.com")
            .addHeader("Cache-Control", "no-cache")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            .build()

        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onLog("WebSocket conectado, aguardando handshake")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleTextFrame(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleBinaryFrame(bytes)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handler.removeCallbacks(pingRunnable)
                    listener.onDisconnected("fechado: $reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handler.removeCallbacks(pingRunnable)
                    listener.onDisconnected("falha: ${t.message}")
                }
            },
        )
    }

    private fun handleTextFrame(ws: WebSocket, text: String) {
        when (val frame = SocketIoFrame.parseText(text)) {
            is SocketIoFrame.EngineOpen -> ws.send(PocketOptionProtocol.engineConnectAck())
            is SocketIoFrame.EnginePing -> ws.send(PocketOptionProtocol.enginePong())
            is SocketIoFrame.NamespaceConnected -> ws.send(ssid)
            is SocketIoFrame.NamedEvent -> handleNamedEvent(frame)
            is SocketIoFrame.NotAuthorized -> listener.onNotAuthorized()
            is SocketIoFrame.Unknown -> Log.d(TAG, "frame desconhecido: ${frame.raw}")
        }
    }

    private fun handleNamedEvent(frame: SocketIoFrame.NamedEvent) {
        when (frame.name) {
            "successauth" -> {
                handler.post(pingRunnable)
                listener.onAuthenticated()
            }
            "updateStream" -> awaitingStreamTick = true
            "NotAuthorized" -> listener.onNotAuthorized()
        }
    }

    private fun handleBinaryFrame(bytes: ByteString) {
        val text = bytes.utf8().trim()
        try {
            if (text.startsWith("[")) {
                val arr = JSONArray(text)
                if (awaitingStreamTick) {
                    awaitingStreamTick = false
                    PocketOptionProtocol.parseTick(arr)?.let { listener.onTick(it) }
                }
                return
            }
            if (!text.startsWith("{")) return
            val obj = JSONObject(text)

            PocketOptionProtocol.parseBalanceUpdate(obj)?.let { listener.onBalance(it); return }
            PocketOptionProtocol.parseOrderConfirmation(obj)?.let { listener.onOrderConfirmed(it); return }
            PocketOptionProtocol.parseHistoryCandles(obj)?.let { listener.onHistoryCandles(it); return }
        } catch (e: Exception) {
            listener.onLog("Falha ao interpretar frame binário: ${e.message}")
        }
    }

    fun requestHistory(asset: String, periodSeconds: Int) {
        webSocket?.send(
            PocketOptionProtocol.loadHistoryPeriod(asset, periodSeconds, System.currentTimeMillis() / 1000),
        )
    }

    fun subscribe(asset: String, periodSeconds: Int) {
        webSocket?.send(PocketOptionProtocol.changeSymbol(asset, periodSeconds))
    }

    fun placeOrder(asset: String, amount: Double, action: TradeAction, durationSeconds: Int) {
        webSocket?.send(PocketOptionProtocol.openOrder(asset, amount, action, demo, durationSeconds))
    }

    fun disconnect() {
        handler.removeCallbacks(pingRunnable)
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    companion object {
        private const val TAG = "PocketOptionClient"
    }
}
