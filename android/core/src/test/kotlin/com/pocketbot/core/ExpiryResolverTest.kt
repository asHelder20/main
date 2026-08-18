package com.pocketbot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiryResolverTest {

    @Test
    fun `keeps the desired expiry when the asset allows it`() {
        val assets = listOf(AssetInfo("EURUSD_otc", isActive = true, allowedCandles = listOf(5, 15, 30, 60, 180, 300)))
        val result = ExpiryResolver.resolve(assets, listOf("EURUSD_otc"), 60)
        assertEquals(1, result.size)
        assertEquals(60, result[0].expirySeconds)
        assertTrue(!result[0].adjusted)
    }

    @Test
    fun `snaps to the closest allowed expiry when not supported`() {
        val assets = listOf(AssetInfo("GBPUSD_otc", isActive = true, allowedCandles = listOf(5, 15, 30, 180, 300)))
        val result = ExpiryResolver.resolve(assets, listOf("GBPUSD_otc"), 60)
        assertEquals(30, result[0].expirySeconds)
        assertTrue(result[0].adjusted)
    }

    @Test
    fun `drops inactive or unknown pairs`() {
        val assets = listOf(
            AssetInfo("USDJPY_otc", isActive = false, allowedCandles = listOf(60)),
        )
        val result = ExpiryResolver.resolve(assets, listOf("USDJPY_otc", "EURJPY_otc"), 60)
        assertEquals(0, result.size)
    }
}
