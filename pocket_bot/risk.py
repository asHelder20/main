"""Guardas de gestão de risco: limites diários, operações simultâneas e
cooldown após perda. Independentes da estratégia, para que nenhuma
sequência de sinais consiga contornar os limites configurados."""
import time
from datetime import date

from .config import Config


class RiskManager:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        self._day = date.today()
        self.daily_pnl = 0.0
        self.trades_today = 0
        self.open_trades = 0
        self._last_loss_at = None

    def _roll_day(self):
        today = date.today()
        if today != self._day:
            self._day = today
            self.daily_pnl = 0.0
            self.trades_today = 0

    def can_trade(self):
        self._roll_day()
        if self.daily_pnl <= -abs(self.cfg.max_daily_loss):
            return False, "limite de perda diária atingido"
        if self.trades_today >= self.cfg.max_trades_per_day:
            return False, "limite de operações diárias atingido"
        if self.open_trades >= self.cfg.max_concurrent_trades:
            return False, "número máximo de operações simultâneas atingido"
        if self._last_loss_at is not None:
            elapsed = time.monotonic() - self._last_loss_at
            if elapsed < self.cfg.cooldown_after_loss_seconds:
                return False, f"em cooldown após perda ({self.cfg.cooldown_after_loss_seconds - elapsed:.0f}s restantes)"
        return True, ""

    def register_open(self):
        self.open_trades += 1
        self.trades_today += 1

    def register_result(self, profit: float):
        self.open_trades = max(0, self.open_trades - 1)
        self.daily_pnl += profit
        if profit < 0:
            self._last_loss_at = time.monotonic()
