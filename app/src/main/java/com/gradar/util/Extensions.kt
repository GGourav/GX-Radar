package com.gradar.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toHex(): String {
    return joinToString("") { "%02X".format(it) }
}

fun ByteArray.readLittleEndianFloat(offset: Int): Float {
    if (offset + 4 > size) return 0f
    return ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .float
}

fun ByteArray.readBigInt(offset: Int): Int {
    if (offset + 4 > size) return 0
    return ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.BIG_ENDIAN)
        .int
}

fun ByteArray.readBigShort(offset: Int): Short {
    if (offset + 2 > size) return 0
    return ByteBuffer.wrap(this, offset, 2)
        .order(ByteOrder.BIG_ENDIAN)
        .short
}
