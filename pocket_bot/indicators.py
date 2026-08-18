"""Indicadores técnicos usados pela estratégia do bot."""
import numpy as np
import pandas as pd


def rsi(series: pd.Series, period: int = 14) -> pd.Series:
    delta = series.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1 / period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))


def bollinger_bands(series: pd.Series, period: int = 20, std_mult: float = 2.0):
    mid = series.rolling(period).mean()
    std = series.rolling(period).std()
    upper = mid + std_mult * std
    lower = mid - std_mult * std
    width = (upper - lower) / mid
    return upper, mid, lower, width


def psar(high: pd.Series, low: pd.Series, af_step: float = 0.02, af_max: float = 0.2) -> pd.Series:
    """Parabolic SAR (Wilder). Indicador de tendência/reversão usado como
    sinal principal pelos bots de Pocket Option mais comuns (ex.: o bot
    open-source de referência pocket_option_trading_bot usa exatamente
    este indicador). Retorna a série de valores do SAR; comparar com o
    preço de fechamento indica a tendência (fechamento > SAR = alta)."""
    high = high.reset_index(drop=True)
    low = low.reset_index(drop=True)
    n = len(high)
    sar = pd.Series(index=high.index, dtype=float)
    if n == 0:
        return sar

    bull = True
    af = af_step
    ep = high.iloc[0]
    sar.iloc[0] = low.iloc[0]

    for i in range(1, n):
        prev_sar = sar.iloc[i - 1]
        curr_sar = prev_sar + af * (ep - prev_sar)

        if bull:
            curr_sar = min(curr_sar, low.iloc[i - 1], low.iloc[i - 2] if i >= 2 else low.iloc[i - 1])
            if low.iloc[i] < curr_sar:
                bull = False
                curr_sar = ep
                ep = low.iloc[i]
                af = af_step
            elif high.iloc[i] > ep:
                ep = high.iloc[i]
                af = min(af + af_step, af_max)
        else:
            curr_sar = max(curr_sar, high.iloc[i - 1], high.iloc[i - 2] if i >= 2 else high.iloc[i - 1])
            if high.iloc[i] > curr_sar:
                bull = True
                curr_sar = ep
                ep = high.iloc[i]
                af = af_step
            elif low.iloc[i] < ep:
                ep = low.iloc[i]
                af = min(af + af_step, af_max)

        sar.iloc[i] = curr_sar

    return sar
