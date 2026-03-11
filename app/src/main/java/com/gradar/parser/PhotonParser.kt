package com.gradar.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhotonParser {

    companion object {
        const val CMD_ACK = 1
        const val CMD_CONNECT = 2
        const val CMD_DISCONNECT = 4
        const val CMD_SEND_RELIABLE = 6
        const val CMD_SEND_UNRELIABLE = 7

        const val MSG_OPERATION_REQUEST = 0x02
        const val MSG_OPERATION_RESPONSE = 0x03
        const val MSG_EVENT = 0x04

        const val TYPE_BYTE = 98
        const val TYPE_BOOLEAN = 111
        const val TYPE_SHORT = 107
        const val TYPE_INTEGER = 105
        const val TYPE_LONG = 108
        const val TYPE_FLOAT = 102
        const val TYPE_DOUBLE = 100
        const val TYPE_STRING = 115
        const val TYPE_BYTE_ARRAY = 120
        const val TYPE_ARRAY = 121
        const val TYPE_OBJECT_ARRAY = 122
        const val TYPE_HASHTABLE = 104
        const val TYPE_DICTIONARY = 68
        const val TYPE_NULL = 42
    }

    data class GameEvent(
        val eventCode: Int,
        val parameters: MutableMap<Int, Any> = mutableMapOf()
    ) {
        fun getInt(key: Int): Int? = (parameters[key] as? Number)?.toInt()
        fun getShort(key: Int): Short? = (parameters[key] as? Number)?.toShort()
        fun getByte(key: Int): Byte? = (parameters[key] as? Number)?.toByte()
        fun getFloat(key: Int): Float? = (parameters[key] as? Number)?.toFloat()
        fun getString(key: Int): String? = parameters[key] as? String
        fun getByteArray(key: Int): ByteArray? = parameters[key] as? ByteArray
    }

    fun parse(payload: ByteArray): List<GameEvent> {
        if (payload.size < 12) return emptyList()

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val events = mutableListOf<GameEvent>()

        try {
            val peerId = buffer.short.toInt() and 0xFFFF
            val flags = buffer.get().toInt() and 0xFF
            val commandCount = buffer.get().toInt() and 0xFF
            val timestamp = buffer.int
            val challenge = buffer.int

            for (i in 0 until commandCount) {
                if (buffer.remaining() < 12) break

                val commandType = buffer.get().toInt() and 0xFF
                val channelId = buffer.get().toInt() and 0xFF
                val commandFlags = buffer.get().toInt() and 0xFF
                buffer.get()

                val commandLength = buffer.int
                val sequenceNumber = buffer.int

                if (commandType == CMD_SEND_UNRELIABLE) {
                    buffer.int
                }

                val payloadStart = buffer.position()
                val event = parseCommand(buffer, commandType, commandLength)
                if (event != null) {
                    events.add(event)
                }

                buffer.position(payloadStart + commandLength - 12)
            }
        } catch (e: Exception) {
        }

        return events
    }

    private fun parseCommand(buffer: ByteBuffer, commandType: Int, commandLength: Int): GameEvent? {
        if (commandType != CMD_SEND_RELIABLE && commandType != CMD_SEND_UNRELIABLE) {
            return null
        }

        val magic = buffer.get().toInt() and 0xFF
        if (magic != 0xF3) return null

        val messageType = buffer.get().toInt() and 0xFF
        if (messageType != MSG_EVENT) return null

        return parseEvent(buffer)
    }

    private fun parseEvent(buffer: ByteBuffer): GameEvent {
        buffer.get()

        val tableSize = buffer.short.toInt() and 0xFFFF
        val event = GameEvent(eventCode = -1)

        for (i in 0 until tableSize) {
            if (buffer.remaining() < 2) break

            val key = buffer.get().toInt() and 0xFF
            val typeCode = buffer.get().toInt() and 0xFF

            val value = readValue(buffer, typeCode)
            if (value != null) {
                event.parameters[key] = value
                if (key == 252 && value is Number) {
                    event.eventCode = value.toInt()
                }
            }
        }

        if (event.eventCode == -1) {
            event.eventCode = 2
        }

        return event
    }

    private fun readValue(buffer: ByteBuffer, typeCode: Int): Any? {
        return when (typeCode) {
            TYPE_BYTE -> buffer.get()
            TYPE_BOOLEAN -> buffer.get() != 0.toByte()
            TYPE_SHORT -> buffer.short
            TYPE_INTEGER -> buffer.int
            TYPE_LONG -> buffer.long
            TYPE_FLOAT -> buffer.float
            TYPE_DOUBLE -> buffer.double
            TYPE_STRING -> {
                val length = buffer.short.toInt() and 0xFFFF
                if (buffer.remaining() >= length) {
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    String(bytes, Charsets.UTF_8)
                } else null
            }
            TYPE_BYTE_ARRAY -> {
                val length = buffer.int
                if (buffer.remaining() >= length && length in 0..65536) {
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    bytes
                } else null
            }
            TYPE_NULL -> null
            TYPE_ARRAY -> {
                val length = buffer.short.toInt() and 0xFFFF
                val elementType = buffer.get().toInt() and 0xFF
                val array = mutableListOf<Any?>()
                for (i in 0 until length) {
                    array.add(readValue(buffer, elementType))
                }
                array
            }
            TYPE_HASHTABLE -> {
                val length = buffer.short.toInt() and 0xFFFF
                val map = mutableMapOf<Any?, Any?>()
                for (i in 0 until length) {
                    val keyType = buffer.get().toInt() and 0xFF
                    val key = readValue(buffer, keyType)
                    val valType = buffer.get().toInt() and 0xFF
                    val value = readValue(buffer, valType)
                    map[key] = value
                }
                map
            }
            TYPE_DICTIONARY -> {
                val keyType = buffer.get().toInt() and 0xFF
                val valType = buffer.get().toInt() and 0xFF
                val length = buffer.short.toInt() and 0xFFFF
                val map = mutableMapOf<Any?, Any?>()
                for (i in 0 until length) {
                    val key = readValue(buffer, keyType)
                    val value = readValue(buffer, valType)
                    map[key] = value
                }
                map
            }
            else -> null
        }
    }
}
