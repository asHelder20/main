import asyncio
import logging
import signal

from pocket_bot.bot import TradingBot
from pocket_bot.config import Config


def setup_logging():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


async def main():
    setup_logging()
    cfg = Config.from_env()
    bot = TradingBot(cfg)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, bot.stop)
        except NotImplementedError:
            pass  # Windows não suporta add_signal_handler

    await bot.run()


if __name__ == "__main__":
    asyncio.run(main())
