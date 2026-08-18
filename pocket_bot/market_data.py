"""
Mantém, para cada par monitorado, um DataFrame atualizado em tempo real
com os candles (histórico fechado + candle em formação), usando o feed
"gap-free" get_candles_live da BinaryOptionsToolsV2. Cada par tem sua
própria tarefa assíncrona, com reconexão automática em caso de queda.
"""
import asyncio
import logging

import pandas as pd

logger = logging.getLogger("pocket_bot.market_data")

RETRY_DELAY_SECONDS = 10


class MarketFeed:
    def __init__(self, client, pairs, period_seconds: int, history_size: int):
        self.client = client
        self.pairs = pairs
        self.period_seconds = period_seconds
        self.history_size = history_size
        self._frames = {}
        self._tasks = []

    async def start(self):
        for pair in self.pairs:
            task = asyncio.create_task(self._consume(pair), name=f"feed-{pair}")
            self._tasks.append(task)

    async def stop(self):
        for task in self._tasks:
            task.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)

    async def _consume(self, pair: str):
        while True:
            try:
                async for closed_candles, forming in self.client.get_candles_live(
                    pair, self.period_seconds, hours=2.0, max_rows=self.history_size
                ):
                    rows = list(closed_candles)
                    if forming:
                        rows = rows + [forming]
                    if not rows:
                        continue
                    df = pd.DataFrame(rows).sort_values("time").reset_index(drop=True)
                    self._frames[pair] = df
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception(
                    "Feed de %s falhou; tentando novamente em %ss", pair, RETRY_DELAY_SECONDS
                )
                await asyncio.sleep(RETRY_DELAY_SECONDS)
                continue
            # o gerador terminou normalmente (raro); espera um pouco e reinicia
            await asyncio.sleep(RETRY_DELAY_SECONDS)

    def get_dataframe(self, pair: str):
        return self._frames.get(pair)
