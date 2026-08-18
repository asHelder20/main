"""
A "EA" do bot: reproduz o algoritmo mais comum entre os bots de Pocket
Option pesquisados (ex.: o bot open-source pocket_option_trading_bot, que
usa Parabolic SAR como estratégia principal, e as recomendações recorrentes
de RSI + Bandas de Bollinger como filtro) — entra na direção de uma
reversão recém-detectada do PSAR, descartando o sinal se o RSI já estiver
esgotado no mesmo sentido ou se a volatilidade estiver baixa demais. O
score de confiança é usado para escolher, entre os pares monitorados, qual
operar em cada ciclo.
"""
from dataclasses import dataclass
from typing import Optional

import pandas as pd

from .indicators import bollinger_bands, psar, rsi


@dataclass
class Signal:
    action: str  # "call" ou "put"
    score: float  # 0.0 a 1.0, confiança do sinal
    reason: str
    pair: Optional[str] = None


def evaluate(df: pd.DataFrame, cfg) -> Optional[Signal]:
    high = df["high"].astype(float)
    low = df["low"].astype(float)
    close = df["close"].astype(float)

    if len(close) < 3:
        return None

    sar = psar(high, low, cfg.psar_af_step, cfg.psar_af_max)
    rsi_series = rsi(close, cfg.rsi_period)
    _, _, _, width = bollinger_bands(close, cfg.bb_period, cfg.bb_std_mult)

    trend = (close.reset_index(drop=True) > sar).reset_index(drop=True)
    last_trend = trend.iloc[-1]
    prev_trend = trend.iloc[-2]
    if last_trend == prev_trend:
        return None  # só entra logo após a reversão do PSAR, como o bot de referência

    last_close = close.iloc[-1]
    last_sar = sar.iloc[-1]
    last_width = width.iloc[-1]
    last_rsi = rsi_series.iloc[-1]

    if pd.isna(last_width) or pd.isna(last_rsi) or last_close == 0:
        return None
    if last_width < cfg.min_bb_width:
        return None  # volatilidade insuficiente: mercado "parado", evita operar

    distance = abs(last_close - last_sar) / last_close
    # fator de escala empírico para variações típicas de preço nos pares OTC
    score = min(1.0, distance * 400)

    if last_trend:  # PSAR reverteu para baixo do preço: tendência de alta
        if last_rsi >= cfg.rsi_upper:
            return None  # alta possivelmente esgotada (sobrecompra)
        return Signal("call", score, "PSAR reverteu para alta, sem sobrecompra")

    if last_rsi <= cfg.rsi_lower:
        return None  # baixa possivelmente esgotada (sobrevenda)
    return Signal("put", score, "PSAR reverteu para baixa, sem sobrevenda")
