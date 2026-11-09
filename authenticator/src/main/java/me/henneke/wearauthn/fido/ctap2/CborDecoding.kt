package me.henneke.wearauthn.fido.ctap2

import me.henneke.wearauthn.decodeToStringOrNull
import kotlin.experimental.and

// CTAP2 canonical CBOR nests at most four levels deep.
private const val MAX_NESTING_DEPTH = 16

private class ByteReader(private val bytes: ByteArray) : Iterator<Byte> {
    private var position = 0

    val remaining: Int
        get() = bytes.size - position

    override fun hasNext() = position < bytes.size

    override fun next() = bytes[position++]
}

@ExperimentalUnsignedTypes
fun fromCborToEnd(iter: Iterator<Byte>): CborValue? {
    return fromCborToEnd(iter.asSequence().toList().toByteArray())
}

@ExperimentalUnsignedTypes
fun fromCborToEnd(bytes: ByteArray): CborValue? {
    val reader = ByteReader(bytes)
    return fromCbor(reader, 0).takeUnless { reader.hasNext() }
}

@ExperimentalUnsignedTypes
fun fromCbor(bytes: Iterable<Byte>): CborValue? {
    return fromCborToEnd(bytes.iterator())
}

@ExperimentalUnsignedTypes
private fun fromCbor(reader: ByteReader, depth: Int): CborValue? {
    with(reader) {
        if (!hasNext())
            return null
        val initialByte = next()
        val additionalInfo = initialByte and 0x1f
        val value: ULong = when (additionalInfo) {
            in 0..23 -> {
                additionalInfo.toULong()
            }
            24.toByte() -> {
                nextInteger(UByte.SIZE_BYTES)?.takeUnless { it <= 23.toULong() }
            }
            25.toByte() -> {
                nextInteger(UShort.SIZE_BYTES)?.takeUnless { it <= UByte.MAX_VALUE }
            }
            26.toByte() -> {
                nextInteger(UInt.SIZE_BYTES)?.takeUnless { it <= UShort.MAX_VALUE }
            }
            27.toByte() -> {
                nextInteger(ULong.SIZE_BYTES)?.takeUnless { it <= UInt.MAX_VALUE }
            }
            else -> null
        }
            ?: return null
        return when (val majorType = (initialByte.toUByte().toUInt() shr 5).toUByte()) {
            MajorType.UNSIGNED_INTEGER.value -> {
                if (value <= Long.MAX_VALUE.toULong()) CborLong(
                    value.toLong()
                )
                else CborUnsignedInteger(value)
            }
            MajorType.NEGATIVE_INTEGER.value -> {
                if (value <= Long.MAX_VALUE.toULong()) CborLong(
                    -1 - value.toLong()
                )
                else CborNegativeInteger(value)
            }
            MajorType.BYTE_STRING.value, MajorType.TEXT_STRING.value -> {
                if (value > remaining.toULong())
                    return null
                val array = ByteArray(value.toInt()) { next() }
                if (majorType == MajorType.BYTE_STRING.value) {
                    CborByteString(array)
                } else {
                    CborTextString(array.decodeToStringOrNull() ?: return null)
                }
            }
            MajorType.ARRAY.value -> {
                if (depth >= MAX_NESTING_DEPTH || value > remaining.toULong())
                    return null
                val array = Array(value.toInt()) {
                    fromCbor(reader, depth + 1) ?: return null
                }
                CborArray(array)
            }
            MajorType.MAP.value -> {
                if (depth >= MAX_NESTING_DEPTH || value > (remaining / 2).toULong())
                    return null
                val size = value.toInt()
                val map = HashMap<CborValue, CborValue>(size)
                repeat(size) {
                    val key = fromCbor(reader, depth + 1) ?: return null
                    if (map.containsKey(key))
                        return null
                    map[key] = fromCbor(reader, depth + 1) ?: return null
                }
                when {
                    map.keys.all { it is CborTextString } ->
                        CborTextStringMap(map.mapKeys { (it.key as CborTextString).value })
                    map.keys.all { it is CborLong } ->
                        CborLongMap(map.mapKeys { (it.key as CborLong).value })
                    else -> CborMap(map)
                }
            }
            MajorType.SIMPLE.value -> {
                when (additionalInfo) {
                    in 0..19 -> CborSimpleValue(
                        additionalInfo.toUByte()
                    )
                    20.toByte() -> CborBoolean(false)
                    21.toByte() -> CborBoolean(true)
                    22.toByte() -> CborNull
                    23.toByte() -> CborUndefined
                    24.toByte() ->
                        CborSimpleValue(value.toUByte()).takeUnless { value < 32.toULong() }
                    // TODO: Support half-precision floating point numbers.
                    25.toByte() -> CborUndefined
                    26.toByte() -> CborFloatingPointNumber(
                        Float.fromBits(
                            value.toInt()
                        ).toDouble()
                    )
                    27.toByte() -> CborFloatingPointNumber(
                        Double.fromBits(
                            value.toLong()
                        )
                    )
                    else -> null
                }
            }
            else -> null
        }
    }
}

@ExperimentalUnsignedTypes
private fun Iterator<Byte>.nextInteger(numBytes: Int): ULong? {
    var value: ULong = 0U
    require(numBytes in 1..8)
    repeat(numBytes) {
        if (!hasNext())
            return null
        value = (value shl 8) + next().toUByte().toULong()
    }
    return value
}

