"""
A "EA" do bot: combina cruzamento de médias móveis (EMA), momentum (MACD)
e um filtro de volatilidade (Bandas de Bollinger) para gerar sinais de
entrada CALL/PUT, com uma pontuação de confiança (score) usada para
escolher, entre os vários pares monitorados, qual operar em cada ciclo.
"""
from dataclasses import dataclass
from typing import Optional

import pandas as pd

from .indicators import bollinger_bands, ema, macd, rsi


@dataclass
class Signal:
    action: str  # "call" ou "put"
    score: float  # 0.0 a 1.0, confiança do sinal
    reason: str
    pair: Optional[str] = None


def evaluate(df: pd.DataFrame, cfg) -> Optional[Signal]:
    close = df["close"].astype(float)

    ema_fast = ema(close, cfg.ema_fast_period)
    ema_slow = ema(close, cfg.ema_slow_period)
    rsi_series = rsi(close, cfg.rsi_period)
    _, _, hist = macd(close, cfg.macd_fast, cfg.macd_slow, cfg.macd_signal)
    _, _, _, width = bollinger_bands(close, cfg.bb_period, cfg.bb_std_mult)

    last_close = close.iloc[-1]
    last_ema_fast = ema_fast.iloc[-1]
    last_ema_slow = ema_slow.iloc[-1]
    last_rsi = rsi_series.iloc[-1]
    last_hist = hist.iloc[-1]
    last_width = width.iloc[-1]

    if pd.isna(last_width) or last_close == 0:
        return None
    if last_width < cfg.min_bb_width:
        return None  # volatilidade insuficiente: mercado "parado", evita operar
    if pd.isna(last_rsi):
        return None

    trend_strength = abs(last_ema_fast - last_ema_slow) / last_close
    momentum_strength = abs(last_hist) / last_close
    # fator de escala empírico para variações típicas de preço nos pares OTC
    score = min(1.0, (trend_strength + momentum_strength) * 300)

    # RSI aqui não exige "neutralidade": numa tendência saudável ele fica
    # deslocado para o lado da tendência (>50 em alta, <50 em baixa). Ele só
    # bloqueia a entrada quando a tendência já está exaurida (RSI extremo no
    # mesmo sentido), reduzindo o risco de comprar/vender no topo/fundo.
    if last_ema_fast > last_ema_slow and last_hist > 0:
        if last_rsi >= cfg.rsi_upper:
            return None  # alta possivelmente esgotada (sobrecompra)
        return Signal("call", score, "EMA rápida > EMA lenta, MACD positivo, sem sobrecompra")
    if last_ema_fast < last_ema_slow and last_hist < 0:
        if last_rsi <= cfg.rsi_lower:
            return None  # baixa possivelmente esgotada (sobrevenda)
        return Signal("put", score, "EMA rápida < EMA lenta, MACD negativo, sem sobrevenda")
    return None
