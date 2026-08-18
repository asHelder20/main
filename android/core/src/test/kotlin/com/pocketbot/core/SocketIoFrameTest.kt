package com.pocketbot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SocketIoFrameTest {

    @Test
    fun `parses engine open frame`() {
        val frame = SocketIoFrame.parseText(
            """0{"sid":"abc123","upgrades":[],"pingInterval":25000,"pingTimeout":20000}"""
        )
        assertIs<SocketIoFrame.EngineOpen>(frame)
    }

    @Test
    fun `parses engine ping`() {
        assertIs<SocketIoFrame.EnginePing>(SocketIoFrame.parseText("2"))
    }

    @Test
    fun `parses namespace connected ack`() {
        val frame = SocketIoFrame.parseText("""40{"sid":"xyz789"}""")
        assertIs<SocketIoFrame.NamespaceConnected>(frame)
    }

    @Test
    fun `parses named event with payload`() {
        val frame = SocketIoFrame.parseText("""451-["successauth",{"isDemo":1}]""")
        assertIs<SocketIoFrame.NamedEvent>(frame)
        frame as SocketIoFrame.NamedEvent
        assertEquals("successauth", frame.name)
        assertEquals(2, frame.payload.length())
    }

    @Test
    fun `parses named event without payload`() {
        val frame = SocketIoFrame.parseText("""451-["updateStream"]""")
        assertIs<SocketIoFrame.NamedEvent>(frame)
        assertEquals("updateStream", (frame as SocketIoFrame.NamedEvent).name)
    }

    @Test
    fun `parses not authorized`() {
        val frame = SocketIoFrame.parseText("""42["NotAuthorized",{"reason":"invalid session"}]""")
        assertIs<SocketIoFrame.NotAuthorized>(frame)
    }

    @Test
    fun `falls back to unknown for anything else`() {
        assertIs<SocketIoFrame.Unknown>(SocketIoFrame.parseText("garbage"))
    }
}
