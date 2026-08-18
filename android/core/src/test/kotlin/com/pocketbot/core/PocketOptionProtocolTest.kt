package com.pocketbot.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PocketOptionProtocolTest {

    @Test
    fun `changeSymbol builds the expected socket_io event`() {
        val msg = PocketOptionProtocol.changeSymbol("EURUSD_otc", 60)
        assertTrue(msg.startsWith("42"))
        val arr = JSONArray(msg.substring(2))
        assertEquals("changeSymbol", arr.getString(0))
        val payload = arr.getJSONObject(1)
        assertEquals("EURUSD_otc", payload.getString("asset"))
        assertEquals(60, payload.getInt("period"))
    }

    @Test
    fun `openOrder builds call and put payloads with correct isDemo flag`() {
        val callMsg = PocketOptionProtocol.openOrder("EURUSD_otc", 1.0, TradeAction.CALL, isDemo = true, durationSeconds = 60)
        val callPayload = JSONArray(callMsg.substring(2)).getJSONObject(1)
        assertEquals("call", callPayload.getString("action"))
        assertEquals(1, callPayload.getInt("isDemo"))
        assertEquals("buy", callPayload.getString("requestId"))
        assertEquals(100, callPayload.getInt("optionType"))

        val putMsg = PocketOptionProtocol.openOrder("EURUSD_otc", 2.5, TradeAction.PUT, isDemo = false, durationSeconds = 180)
        val putPayload = JSONArray(putMsg.substring(2)).getJSONObject(1)
        assertEquals("put", putPayload.getString("action"))
        assertEquals(0, putPayload.getInt("isDemo"))
        assertEquals(180, putPayload.getInt("time"))
    }

    @Test
    fun `loadHistoryPeriod uses the known offset table`() {
        val msg = PocketOptionProtocol.loadHistoryPeriod("EURUSD_otc", 60, endTimeEpochSeconds = 1_000_000)
        val payload = JSONArray(msg.substring(2)).getJSONObject(1)
        assertEquals(9000, payload.getInt("offset"))
        assertEquals(1_000_000L + 7200, payload.getLong("time"))
    }

    @Test
    fun `parses balance update`() {
        val json = JSONObject("""{"balance":1234.5,"isDemo":1,"uid":42}""")
        val update = PocketOptionProtocol.parseBalanceUpdate(json)
        assertEquals(1234.5, update?.balance)
        assertEquals(true, update?.isDemo)
    }

    @Test
    fun `ignores messages without a balance field`() {
        assertNull(PocketOptionProtocol.parseBalanceUpdate(JSONObject("""{"foo":1}""")))
    }

    @Test
    fun `parses order confirmation only for the buy requestId`() {
        val json = JSONObject("""{"requestId":"buy","id":"order-1","asset":"EURUSD_otc"}""")
        val confirmation = PocketOptionProtocol.parseOrderConfirmation(json)
        assertEquals("order-1", confirmation?.orderId)

        assertNull(PocketOptionProtocol.parseOrderConfirmation(JSONObject("""{"requestId":"other"}""")))
    }

    @Test
    fun `parses history candles sorted by time`() {
        val json = JSONObject(
            """{"data":[
                {"time":200,"open":1.1,"high":1.2,"low":1.0,"close":1.15},
                {"time":100,"open":1.0,"high":1.1,"low":0.9,"close":1.05}
            ]}"""
        )
        val candles = PocketOptionProtocol.parseHistoryCandles(json)
        assertEquals(listOf(100L, 200L), candles?.map { it.time })
    }

    @Test
    fun `parses a tick entry`() {
        val json = JSONArray("""[["EURUSD_otc", 1700000000, 1.10234]]""")
        val tick = PocketOptionProtocol.parseTick(json)
        assertEquals("EURUSD_otc", tick?.symbol)
        assertEquals(1700000000L, tick?.timeEpochSeconds)
        assertEquals(1.10234, tick?.price)
    }
}
