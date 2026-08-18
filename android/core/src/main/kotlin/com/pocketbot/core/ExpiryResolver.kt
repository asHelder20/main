package com.pocketbot.core

/**
 * A Pocket Option só aceita durações de expiração fixas por ativo (ver
 * campo allowed_candles retornado pela corretora). Porta de
 * TradingBot._resolve_pairs em pocket_bot/bot.py: ajusta a duração
 * desejada para a mais próxima que o ativo realmente aceita.
 */
object ExpiryResolver {

    data class Resolution(val pair: String, val expirySeconds: Int, val adjusted: Boolean)

    fun resolve(assets: List<AssetInfo>, desiredPairs: List<String>, desiredExpirySeconds: Int): List<Resolution> {
        val bySymbol = assets.associateBy { it.symbol }
        val result = mutableListOf<Resolution>()

        for (pair in desiredPairs) {
            val asset = bySymbol[pair] ?: continue
            if (!asset.isActive) continue
            if (asset.allowedCandles.isEmpty()) continue

            if (desiredExpirySeconds in asset.allowedCandles) {
                result.add(Resolution(pair, desiredExpirySeconds, adjusted = false))
            } else {
                val closest = asset.allowedCandles.minByOrNull { kotlin.math.abs(it - desiredExpirySeconds) }!!
                result.add(Resolution(pair, closest, adjusted = true))
            }
        }
        return result
    }
}
