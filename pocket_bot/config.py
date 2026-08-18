import os
from dataclasses import dataclass
from pathlib import Path
from typing import List

from dotenv import load_dotenv

load_dotenv()

DEFAULT_PAIRS = [
    "EURUSD_otc",
    "GBPUSD_otc",
    "USDJPY_otc",
    "EURJPY_otc",
    "AUDCAD_otc",
    "USDCHF_otc",
    "NZDUSD_otc",
    "EURGBP_otc",
]


def _env_bool(name: str, default: bool) -> bool:
    val = os.getenv(name)
    if val is None or val.strip() == "":
        return default
    return val.strip().lower() in ("1", "true", "yes", "on")


def _env_float(name: str, default: float) -> float:
    val = os.getenv(name)
    return float(val) if val not in (None, "") else default


def _env_int(name: str, default: int) -> int:
    val = os.getenv(name)
    return int(val) if val not in (None, "") else default


def _env_list(name: str, default: List[str]) -> List[str]:
    val = os.getenv(name)
    if not val:
        return list(default)
    return [p.strip() for p in val.split(",") if p.strip()]


@dataclass
class Config:
    ssid: str
    demo: bool
    live_trading_confirmed: bool
    pairs: List[str]
    candle_period_seconds: int
    history_size: int
    expiry_seconds: int
    stake_amount: float
    decision_interval_seconds: int
    min_signal_score: float
    psar_af_step: float
    psar_af_max: float
    rsi_period: int
    rsi_lower: float
    rsi_upper: float
    bb_period: int
    bb_std_mult: float
    min_bb_width: float
    max_daily_loss: float
    max_trades_per_day: int
    max_concurrent_trades: int
    cooldown_after_loss_seconds: int
    journal_path: Path

    @classmethod
    def from_env(cls) -> "Config":
        ssid = os.getenv("PO_SSID", "").strip()
        if not ssid:
            raise SystemExit(
                "PO_SSID não configurado. Copie .env.example para .env e "
                "preencha com o SSID copiado dos cookies do navegador em "
                "pocketoption.com (veja instruções no README)."
            )
        return cls(
            ssid=ssid,
            demo=_env_bool("PO_DEMO", False),
            live_trading_confirmed=_env_bool("LIVE_TRADING_CONFIRMED", False),
            pairs=_env_list("PO_PAIRS", DEFAULT_PAIRS),
            candle_period_seconds=_env_int("CANDLE_PERIOD_SECONDS", 60),
            history_size=_env_int("HISTORY_SIZE", 100),
            expiry_seconds=_env_int("EXPIRY_SECONDS", 60),
            stake_amount=_env_float("STAKE_AMOUNT", 1.0),
            decision_interval_seconds=_env_int("DECISION_INTERVAL_SECONDS", 15),
            min_signal_score=_env_float("MIN_SIGNAL_SCORE", 0.35),
            psar_af_step=_env_float("PSAR_AF_STEP", 0.02),
            psar_af_max=_env_float("PSAR_AF_MAX", 0.2),
            rsi_period=_env_int("RSI_PERIOD", 14),
            rsi_lower=_env_float("RSI_LOWER", 30),
            rsi_upper=_env_float("RSI_UPPER", 70),
            bb_period=_env_int("BB_PERIOD", 20),
            bb_std_mult=_env_float("BB_STD_MULT", 2.0),
            min_bb_width=_env_float("MIN_BB_WIDTH", 0.0006),
            max_daily_loss=_env_float("MAX_DAILY_LOSS", 20.0),
            max_trades_per_day=_env_int("MAX_TRADES_PER_DAY", 20),
            max_concurrent_trades=_env_int("MAX_CONCURRENT_TRADES", 1),
            cooldown_after_loss_seconds=_env_int("COOLDOWN_AFTER_LOSS_SECONDS", 60),
            journal_path=Path(os.getenv("JOURNAL_PATH", "trade_journal.csv")),
        )
