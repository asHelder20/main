package com.pocketbot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pocketbot.core.BalanceUpdate
import com.pocketbot.core.Candle
import com.pocketbot.core.OrderConfirmation
import com.pocketbot.core.PocketOptionProtocol
import com.pocketbot.core.RiskConfig
import com.pocketbot.core.RiskManager
import com.pocketbot.core.Signal
import com.pocketbot.core.Strategy
import com.pocketbot.core.StrategyConfig
import com.pocketbot.core.TickAggregator
import com.pocketbot.core.TradeAction

/**
 * Serviço em primeiro plano que mantém a ligação à Pocket Option e corre o
 * loop de decisão (mesmo algoritmo do bot Python: PSAR + RSI + Bollinger).
 *
 * Limitação de protocolo conhecida: não encontrei, na leitura do cliente
 * de referência, uma mensagem confirmada de "resultado da operação" push
 * do servidor. Em vez de arriscar um formato de mensagem inventado para
 * algo que mexe com dinheiro, o P&L é inferido comparando o saldo antes e
 * depois de cada operação expirar (o campo "balance" é confirmadamente
 * empurrado pelo servidor). Ver README-ANDROID.md.
 */
class TradingService : Service(), PocketOptionClient.Listener {

    private lateinit var prefs: SecurePrefs
    private lateinit var journal: TradeJournal
    private lateinit var risk: RiskManager
    private var client: PocketOptionClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val strategyConfig = StrategyConfig()
    private val aggregators = mutableMapOf<String, TickAggregator>()
    private val pairExpirySeconds = mutableMapOf<String, Int>()

    private var pairQueue: ArrayDeque<String> = ArrayDeque()
    private var currentHistoryPair: String? = null
    private var lastKnownBalance: Double? = null
    private var demoConfirmedByServer: Boolean? = null
    private var isRunning = false

    private data class PendingSettlement(val orderId: String, val pair: String, val action: String, val balanceBefore: Double, val settleAtMillis: Long)
    private val pendingSettlements = mutableListOf<PendingSettlement>()

    private val decisionRunnable = object : Runnable {
        override fun run() {
            runDecisionCycle()
            handler.postDelayed(this, DECISION_INTERVAL_MS)
        }
    }

    private val historyTimeoutRunnable = Runnable {
        log("Sem resposta de histórico para ${currentHistoryPair}; a subscrever mesmo assim")
        advanceHistoryQueue()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs(this)
        journal = TradeJournal(this)
        risk = RiskManager(RiskConfig(maxDailyLoss = prefs.maxDailyLoss))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBot()
                return START_NOT_STICKY
            }
            else -> startBot()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopBot()
        super.onDestroy()
    }

    // ---- ciclo de vida do bot ----

    private fun startBot() {
        if (isRunning) {
            log("Bot já está a correr, ignorando pedido de início duplicado")
            return
        }
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification("A ligar..."))

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pocketbot:trading").apply { acquire(12 * 60 * 60 * 1000L) }

        val ssid = prefs.ssid
        if (ssid.isBlank()) {
            log("PO_SSID não configurado. Abra a app e preencha antes de iniciar.")
            stopSelf()
            return
        }

        // Suposição inicial só para escolher o servidor WS; a fonte da verdade é
        // BalanceUpdate.isDemo, confirmado pelo próprio servidor (ver onBalance).
        val guessedDemo = Regex("\"isDemo\"\\s*:\\s*1").containsMatchIn(ssid)

        pairExpirySeconds.clear()
        aggregators.clear()
        prefs.pairs().forEach {
            aggregators[it] = TickAggregator(periodSeconds = CANDLE_PERIOD_SECONDS, maxClosedCandles = HISTORY_SIZE)
            pairExpirySeconds[it] = prefs.expirySeconds
        }

        client = PocketOptionClient(ssid, guessedDemo, this).also { it.connect() }
    }

    private fun stopBot() {
        isRunning = false
        demoConfirmedByServer = null
        lastKnownBalance = null
        pendingSettlements.clear()
        handler.removeCallbacks(decisionRunnable)
        handler.removeCallbacks(historyTimeoutRunnable)
        client?.disconnect()
        client = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- PocketOptionClient.Listener ----

    override fun onAuthenticated() {
        log("Autenticado na Pocket Option")
        pairQueue = ArrayDeque(prefs.pairs())
        advanceHistoryQueue()
    }

    override fun onNotAuthorized() {
        log("SSID rejeitado pela corretora (expirado ou inválido). Pare e atualize o SSID.")
        stopBot()
    }

    override fun onBalance(update: BalanceUpdate) {
        // Trava de segurança real: só a corretora sabe de facto se a conta é
        // demo ou real (o SSID podia estar errado/desatualizado). Isto espelha
        // a mesma trava que o bot em Python aplica depois de conectar.
        if (demoConfirmedByServer == null) {
            demoConfirmedByServer = update.isDemo
            if (!update.isDemo && !prefs.liveTradingConfirmed) {
                log("Conta REAL detetada mas a confirmação de operar em conta real não está ativa. A parar.")
                stopBot()
                return
            }
            log(if (update.isDemo) "Conta DEMO confirmada" else "Conta REAL confirmada - operando com dinheiro real")
            updateNotification("Ligado (${if (update.isDemo) "demo" else "REAL"}) | saldo ${update.balance}")
            handler.post(decisionRunnable)
        }

        lastKnownBalance = update.balance
        reconcileSettlements(update.balance)
    }

    override fun onOrderConfirmed(confirmation: OrderConfirmation) {
        log("Ordem confirmada: ${confirmation.orderId ?: "sem id"}")
    }

    override fun onHistoryCandles(candles: List<Candle>) {
        val pair = currentHistoryPair ?: return
        aggregators[pair]?.seed(candles)
        log("Histórico de $pair carregado (${candles.size} candles)")
        advanceHistoryQueue()
    }

    override fun onTick(tick: PocketOptionProtocol.Tick) {
        aggregators[tick.symbol]?.onTick(tick.timeEpochSeconds, tick.price)
    }

    override fun onDisconnected(reason: String) {
        log("Desligado: $reason")
        updateNotification("Desligado: $reason")
    }

    override fun onLog(message: String) {
        log(message)
    }

    // ---- fila de backfill sequencial (ver nota de protocolo na doc da classe) ----

    private fun advanceHistoryQueue() {
        handler.removeCallbacks(historyTimeoutRunnable)
        if (pairQueue.isEmpty()) {
            currentHistoryPair = null
            log("Todos os pares subscritos, bot operacional")
            return
        }
        val pair = pairQueue.removeFirst()
        currentHistoryPair = pair
        client?.requestHistory(pair, CANDLE_PERIOD_SECONDS)
        handler.postDelayed(historyTimeoutRunnable, HISTORY_TIMEOUT_MS)
        client?.subscribe(pair, CANDLE_PERIOD_SECONDS)
    }

    // ---- loop de decisão (mesmo algoritmo do bot Python) ----

    private fun runDecisionCycle() {
        val decision = risk.canTrade()
        if (!decision.canTrade) {
            log("Sem novas operações: ${decision.reason}")
            return
        }

        val minHistory = strategyConfig.minHistory()
        val signals = aggregators.mapNotNull { (pair, agg) ->
            val candles = agg.snapshot()
            if (candles.size < minHistory) null else Strategy.evaluate(pair, candles, strategyConfig)
        }
        val best = Strategy.pickBest(signals, strategyConfig) ?: run {
            log("Nenhum sinal com score suficiente neste ciclo")
            return
        }

        execute(best)
    }

    private fun execute(signal: Signal) {
        val expiry = pairExpirySeconds[signal.pair] ?: prefs.expirySeconds
        val balanceBefore = lastKnownBalance
        if (balanceBefore == null) {
            log("Saldo ainda desconhecido, a ignorar sinal para não perder o rasto do resultado")
            return
        }

        log("Sinal escolhido: ${signal.pair} ${signal.action} score=${"%.2f".format(signal.score)} ${signal.reason}")
        risk.registerOpen()
        client?.placeOrder(signal.pair, prefs.stakeAmount, signal.action, expiry)

        val orderId = "pending-${System.currentTimeMillis()}"
        journal.recordEntry(signal.pair, signal.action.name, prefs.stakeAmount, expiry, orderId, signal.score, signal.reason)
        pendingSettlements.add(
            PendingSettlement(
                orderId = orderId,
                pair = signal.pair,
                action = signal.action.name,
                balanceBefore = balanceBefore,
                settleAtMillis = System.currentTimeMillis() + expiry * 1000L + SETTLEMENT_BUFFER_MS,
            )
        )
    }

    private fun reconcileSettlements(currentBalance: Double) {
        val now = System.currentTimeMillis()
        val due = pendingSettlements.filter { it.settleAtMillis <= now }
        due.forEach { settlement ->
            val profit = currentBalance - settlement.balanceBefore
            risk.registerResult(profit)
            journal.recordResult(settlement.pair, settlement.action, settlement.orderId, if (profit >= 0) "win_or_draw" else "loss", profit)
            log("Liquidação inferida ${settlement.pair}: lucro=${"%.2f".format(profit)} PnL_dia=${"%.2f".format(risk.dailyPnl)}")
        }
        pendingSettlements.removeAll(due)
    }

    // ---- notificação/foreground ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, TradingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(0, getString(R.string.action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun log(message: String) {
        android.util.Log.i("TradingService", message)
        sendBroadcast(Intent(ACTION_LOG).putExtra(EXTRA_LOG, message).setPackage(packageName))
    }

    companion object {
        const val ACTION_STOP = "com.pocketbot.app.action.STOP"
        const val ACTION_LOG = "com.pocketbot.app.action.LOG"
        const val EXTRA_LOG = "log"

        private const val CHANNEL_ID = "pocket_bot_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CANDLE_PERIOD_SECONDS = 60
        private const val HISTORY_SIZE = 100
        private const val DECISION_INTERVAL_MS = 15_000L
        private const val HISTORY_TIMEOUT_MS = 8_000L
        private const val SETTLEMENT_BUFFER_MS = 15_000L
    }
}
