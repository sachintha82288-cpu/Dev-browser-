package io.github.sachintha82288.devbrowser.util

/**
 * Stage A: self-contained QR encoder (byte mode, error-correction level L,
 * versions 1-10, mask 0).
 *
 * No third-party dependency is allowed for QR, so this is a direct port of the
 * QR specification: GF(256) Reed-Solomon, block interleaving, function patterns,
 * BCH format/version information and the standard zig-zag data placement. The
 * port was checked against a reference implementation whose output decodes with
 * zxing for versions 2, 4, 6, 7, 8, 9 and 10.
 */
object QrCode {

    /** Encoded matrix: `true` marks a dark module. Row-major, `size` x `size`. */
    data class Result(val size: Int, val modules: List<BooleanArray>)

    private val ECC_CODEWORDS = intArrayOf(7, 10, 15, 20, 26, 18, 20, 24, 30, 18)
    private val NUM_BLOCKS = intArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 4)
    private val DATA_CODEWORDS = intArrayOf(19, 34, 55, 80, 108, 136, 156, 194, 232, 274)

    private val ALIGNMENT = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
    )

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var value = 1
        for (i in 0 until 255) {
            EXP[i] = value
            LOG[value] = i
            value = value shl 1
            if (value and 0x100 != 0) value = value xor 0x11D
        }
        for (i in 255 until 512) {
            EXP[i] = EXP[i - 255]
        }
    }

    /** Returns null when the text does not fit into version 10 (about 273 bytes). */
    fun encode(text: String): Result? {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = chooseVersion(data.size) ?: return null
        val codewords = buildCodewords(data, version)
        val interleaved = interleave(codewords, version)
        val matrix = buildMatrix(version, interleaved)
        return Result(matrix.size, matrix.map { row -> row.copyOf() })
    }

    private fun chooseVersion(payloadLength: Int): Int? {
        for (version in 1..10) {
            val lengthBits = if (version <= 9) 8 else 16
            val needed = 4 + lengthBits + payloadLength * 8
            if (needed <= DATA_CODEWORDS[version - 1] * 8) return version
        }
        return null
    }

    private fun buildCodewords(data: ByteArray, version: Int): IntArray {
        val capacity = DATA_CODEWORDS[version - 1] * 8
        val bits = ArrayList<Boolean>(capacity)
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value shr i) and 1 == 1)
            }
        }
        append(0b0100, 4)
        append(data.size, if (version <= 9) 8 else 16)
        data.forEach { byte ->
            append(byte.toInt() and 0xFF, 8)
        }
        val terminator = minOf(4, capacity - bits.size)
        repeat(terminator) { bits.add(false) }
        while (bits.size % 8 != 0) bits.add(false)
        val pad = intArrayOf(0xEC, 0x11)
        var index = 0
        while (bits.size < capacity) {
            append(pad[index % 2], 8)
            index++
        }
        val codewords = IntArray(capacity / 8)
        for (i in codewords.indices) {
            var value = 0
            for (b in 0 until 8) {
                value = (value shl 1) or if (bits[i * 8 + b]) 1 else 0
            }
            codewords[i] = value
        }
        return codewords
    }

    private fun interleave(codewords: IntArray, version: Int): IntArray {
        val numBlocks = NUM_BLOCKS[version - 1]
        val eccLength = ECC_CODEWORDS[version - 1]
        val total = codewords.size + numBlocks * eccLength
        val shortBlockLength = total / numBlocks
        val numShort = numBlocks - total % numBlocks
        val shortDataLength = shortBlockLength - eccLength
        val divisor = rsDivisor(eccLength)

        val dataBlocks = ArrayList<IntArray>()
        val eccBlocks = ArrayList<IntArray>()
        var offset = 0
        for (i in 0 until numBlocks) {
            val dataLength = shortDataLength + if (i < numShort) 0 else 1
            val block = codewords.copyOfRange(offset, offset + dataLength)
            offset += dataLength
            dataBlocks.add(block)
            eccBlocks.add(rsRemainder(block, divisor))
        }

        val out = IntArray(total)
        var position = 0
        val maxData = dataBlocks.maxOf { it.size }
        for (i in 0 until maxData) {
            for (block in dataBlocks) {
                if (i < block.size) out[position++] = block[i]
            }
        }
        for (i in 0 until eccLength) {
            for (block in eccBlocks) {
                out[position++] = block[i]
            }
        }
        return out
    }

    private fun gfMultiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (byte in data) {
            val factor = byte xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in divisor.indices) {
                result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
        }
        return result
    }

    private fun bchRemainder(value: Int, polynomial: Int, degree: Int): Int {
        var remainder = value
        while (Integer.toBinaryString(remainder).length - 1 >= degree) {
            remainder = remainder xor (polynomial shl (Integer.toBinaryString(remainder).length - 1 - degree))
        }
        return remainder
    }

    private fun buildMatrix(version: Int, codewords: IntArray): Array<BooleanArray> {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setFunction(x: Int, y: Int, value: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = value
                reserved[y][x] = true
            }
        }

        // Timing patterns.
        for (i in 0 until size) {
            setFunction(6, i, i % 2 == 0)
            setFunction(i, 6, i % 2 == 0)
        }

        // Finder patterns with separators (9x9 including the quiet separator).
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(Math.abs(dx), Math.abs(dy))
                    setFunction(cx + dx, cy + dy, dist != 2 && dist != 4)
                }
            }
        }
        finder(3, 3)
        finder(size - 4, 3)
        finder(3, size - 4)

        // Alignment patterns (skipped where they would collide with finders).
        val centers = ALIGNMENT[version - 1]
        for (cy in centers) {
            for (cx in centers) {
                val collides = (cx == 6 && cy == 6) ||
                    (cx == 6 && cy == centers.last()) ||
                    (cx == centers.last() && cy == 6)
                if (collides) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = maxOf(Math.abs(dx), Math.abs(dy))
                        setFunction(cx + dx, cy + dy, dist != 1)
                    }
                }
            }
        }

        // Reserve format areas.
        for (i in 0 until 9) {
            if (i != 6) {
                setFunction(8, i, false)
                setFunction(i, 8, false)
            }
        }
        for (i in 0 until 8) {
            setFunction(size - 1 - i, 8, false)
            setFunction(8, size - 1 - i, false)
        }
        setFunction(8, size - 8, true)

        // Version information for versions 7 and up.
        if (version >= 7) {
            val bits = (version shl 12) or bchRemainder(version shl 12, 0x1F25, 12)
            for (i in 0 until 18) {
                val bit = (bits shr i) and 1 == 1
                val a = size - 11 + i % 3
                val b = i / 3
                setFunction(a, b, bit)
                setFunction(b, a, bit)
            }
        }

        // Zig-zag data placement.
        var bitIndex = 0
        fun nextBit(): Boolean {
            if (bitIndex >= codewords.size * 8) return false
            val value = (codewords[bitIndex shr 3] shr (7 - (bitIndex and 7))) and 1 == 1
            bitIndex++
            return value
        }
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            val upward = ((right + 1) and 2) == 0
            for (vert in 0 until size) {
                val y = if (upward) size - 1 - vert else vert
                for (j in 0 until 2) {
                    val x = right - j
                    if (!reserved[y][x]) {
                        modules[y][x] = nextBit()
                    }
                }
            }
            right -= 2
        }

        // Mask 0 and format information.
        val dataBits = 0b01 shl 3
        val format = ((dataBits shl 10) or bchRemainder(dataBits shl 10, 0x537, 10)) xor 0x5412
        val formatBits = BooleanArray(15) { i -> (format shr i) and 1 == 1 }
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!reserved[y][x] && (x + y) % 2 == 0) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }
        for (i in 0 until 6) setFunction(8, i, formatBits[i])
        setFunction(8, 7, formatBits[6])
        setFunction(8, 8, formatBits[7])
        setFunction(7, 8, formatBits[8])
        for (i in 9 until 15) setFunction(14 - i, 8, formatBits[i])
        for (i in 0 until 8) setFunction(size - 1 - i, 8, formatBits[i])
        for (i in 8 until 15) setFunction(8, size - 15 + i, formatBits[i])
        setFunction(8, size - 8, true)

        return modules
    }
}
