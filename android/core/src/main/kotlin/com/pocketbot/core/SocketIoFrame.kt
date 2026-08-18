package com.pocketbot.core

import org.json.JSONArray

/**
 * Framing Engine.IO/Socket.IO usado pela Pocket Option, extraído lendo o
 * código-fonte real de um cliente Python não-oficial e bem estabelecido
 * (protocolo idêntico ao usado pelo pocket_bot em Python). Cada frame de
 * texto do WebSocket carrega um prefixo de um ou dois caracteres que
 * identifica o tipo de pacote Engine.IO/Socket.IO.
 */
sealed class SocketIoFrame {
    /** "0{...}" - Engine.IO OPEN, primeira mensagem do servidor ao conectar. */
    data class EngineOpen(val raw: String) : SocketIoFrame()

    /** "2" - Engine.IO PING; o cliente deve responder PONG ("3") imediatamente. */
    object EnginePing : SocketIoFrame()

    /** "40{...}" - confirmação de conexão ao namespace; o cliente deve enviar o SSID em seguida. */
    object NamespaceConnected : SocketIoFrame()

    /** "451-[nome, payload...]" - evento nomeado do servidor (successauth, updateStream, etc.). */
    data class NamedEvent(val name: String, val payload: JSONArray) : SocketIoFrame()

    /** SSID rejeitado pela corretora. */
    data class NotAuthorized(val raw: String) : SocketIoFrame()

    data class Unknown(val raw: String) : SocketIoFrame()

    companion object {
        fun parseText(message: String): SocketIoFrame {
            return when {
                message == "2" -> EnginePing
                message.startsWith("0") -> EngineOpen(message)
                message.startsWith("40") && message.contains("sid") -> NamespaceConnected
                message.startsWith("451-[") -> {
                    val jsonPart = message.substring(message.indexOf('-') + 1)
                    val arr = JSONArray(jsonPart)
                    val name = if (arr.length() > 0) arr.optString(0) else ""
                    NamedEvent(name, arr)
                }
                message.startsWith("42") && message.contains("NotAuthorized") -> NotAuthorized(message)
                else -> Unknown(message)
            }
        }
    }
}
