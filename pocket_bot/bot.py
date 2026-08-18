import asyncio
import logging

from BinaryOptionsToolsV2.pocketoption import PocketOptionAsync

from .config import Config
from .journal import Journal
from .market_data import MarketFeed
from .risk import RiskManager
from .strategy import evaluate

logger = logging.getLogger("pocket_bot.bot")


class TradingBot:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.risk = RiskManager(cfg)
        self.journal = Journal(cfg.journal_path)
        self._stopping = False

    def stop(self):
        self._stopping = True

    async def run(self):
        if not self.cfg.demo and not self.cfg.live_trading_confirmed:
            raise SystemExit(
                "PO_DEMO=false (conta REAL) mas LIVE_TRADING_CONFIRMED não está "
                "true no .env. Esta é uma proteção intencional: teste a estratégia "
                "em conta demo primeiro e só defina LIVE_TRADING_CONFIRMED=true "
                "depois de entender os riscos (o bot opera sem confirmação manual "
                "por entrada)."
            )

        min_history = max(self.cfg.ema_slow_period, self.cfg.bb_period, self.cfg.macd_slow) + 5

        async with PocketOptionAsync(self.cfg.ssid) as client:
            actual_demo = client.is_demo()
            logger.warning(
                "Conectado à Pocket Option | conta=%s | pares monitorados=%s",
                "DEMO" if actual_demo else "REAL",
                ", ".join(self.cfg.pairs),
            )
            if not actual_demo:
                logger.warning(
                    "OPERANDO COM DINHEIRO REAL. stake=%.2f expiracao=%ss "
                    "perda_max_diaria=%.2f max_operacoes_dia=%s",
                    self.cfg.stake_amount,
                    self.cfg.expiry_seconds,
                    self.cfg.max_daily_loss,
                    self.cfg.max_trades_per_day,
                )

            feed = MarketFeed(client, self.cfg.pairs, self.cfg.candle_period_seconds, self.cfg.history_size)
            await feed.start()
            try:
                await self._decision_loop(client, feed, min_history)
            finally:
                await feed.stop()

    async def _decision_loop(self, client, feed, min_history):
        while not self._stopping:
            await asyncio.sleep(self.cfg.decision_interval_seconds)
            try:
                await self._tick(client, feed, min_history)
            except Exception:
                logger.exception("Erro no ciclo de decisão; continuando no próximo ciclo")

    async def _tick(self, client, feed, min_history):
        can_trade, reason = self.risk.can_trade()
        if not can_trade:
            logger.info("Sem novas operações: %s", reason)
            return

        best = None
        for pair in self.cfg.pairs:
            df = feed.get_dataframe(pair)
            if df is None or len(df) < min_history:
                continue
            signal = evaluate(df, self.cfg)
            if signal is None:
                continue
            signal.pair = pair
            if best is None or signal.score > best.score:
                best = signal

        if best is None or best.score < self.cfg.min_signal_score:
            logger.debug("Nenhum sinal com score suficiente neste ciclo")
            return

        await self._execute(client, best)

    async def _execute(self, client, signal):
        logger.info(
            "Sinal escolhido: %s %s | score=%.2f | %s",
            signal.pair,
            signal.action.upper(),
            signal.score,
            signal.reason,
        )
        self.risk.register_open()
        try:
            if signal.action == "call":
                trade_id, _deal = await client.buy(signal.pair, self.cfg.stake_amount, self.cfg.expiry_seconds)
            else:
                trade_id, _deal = await client.sell(signal.pair, self.cfg.stake_amount, self.cfg.expiry_seconds)
        except Exception:
            logger.exception("Falha ao enviar ordem para %s", signal.pair)
            self.risk.register_result(0.0)
            return

        self.journal.record(
            pair=signal.pair,
            action=signal.action,
            amount=self.cfg.stake_amount,
            expiry_seconds=self.cfg.expiry_seconds,
            trade_id=trade_id,
            score=f"{signal.score:.3f}",
            reason=signal.reason,
        )
        asyncio.create_task(self._await_result(client, trade_id, signal))

    async def _await_result(self, client, trade_id, signal):
        try:
            result = await client.check_win(trade_id, timeout_seconds=self.cfg.expiry_seconds + 30)
        except Exception:
            logger.exception("Não foi possível confirmar o resultado da operação %s", trade_id)
            self.risk.register_result(0.0)
            return

        profit = result.get("profit", 0.0) or 0.0
        self.risk.register_result(profit)
        self.journal.record(
            pair=signal.pair,
            action=signal.action,
            trade_id=trade_id,
            result=result.get("result"),
            profit=profit,
        )
        logger.info(
            "Resultado %s: %s | lucro=%.2f | PnL do dia=%.2f",
            trade_id,
            result.get("result"),
            profit,
            self.risk.daily_pnl,
        )
