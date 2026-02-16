package com.ghostwhisper.service

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Implements DCT (Discrete Cosine Transform) based steganography. Uses a variation of the Koch &
 * Zhao algorithm to embed data in the frequency domain, making it robust against JPEG
 * compression/re-compression.
 */
object DCTSteganographyHelper {

    private const val HEADER_SIGNATURE = "GW" // 2 bytes
    private const val BLOCK_SIZE = 8

    // Coefficients to use for embedding (Mid-frequency).
    // These indices are 0-63 (zigzag order or row-major).
    // Using (4,1) and (3,2) or similar mid-band coeffs.
    // Let's use indices from flattened 8x8 array.
    private val C1_INDEX = 4 * 8 + 1 // Row 4, Col 1
    private val C2_INDEX = 3 * 8 + 2 // Row 3, Col 2

    // Threshold for difference. Larger = more robust but more visible distortion.
    private const val P_THRESHOLD = 25.0

    // Standard JPEG Luminance Quantization Table (approx quality 50)
    // Used to simulate what coefficients will look like after compression
    private val Q_TABLE =
            doubleArrayOf(
                    16.0,
                    11.0,
                    10.0,
                    16.0,
                    24.0,
                    40.0,
                    51.0,
                    61.0,
                    12.0,
                    12.0,
                    14.0,
                    19.0,
                    26.0,
                    58.0,
                    60.0,
                    55.0,
                    14.0,
                    13.0,
                    16.0,
                    24.0,
                    40.0,
                    57.0,
                    69.0,
                    56.0,
                    14.0,
                    17.0,
                    22.0,
                    29.0,
                    51.0,
                    87.0,
                    80.0,
                    62.0,
                    18.0,
                    22.0,
                    37.0,
                    56.0,
                    68.0,
                    109.0,
                    103.0,
                    77.0,
                    24.0,
                    35.0,
                    55.0,
                    64.0,
                    81.0,
                    104.0,
                    113.0,
                    92.0,
                    49.0,
                    64.0,
                    78.0,
                    87.0,
                    103.0,
                    121.0,
                    120.0,
                    101.0,
                    72.0,
                    92.0,
                    95.0,
                    98.0,
                    112.0,
                    100.0,
                    103.0,
                    99.0
            )

    fun encode(bitmap: Bitmap, message: String): Bitmap? {
        val width = (bitmap.width / BLOCK_SIZE) * BLOCK_SIZE
        val height = (bitmap.height / BLOCK_SIZE) * BLOCK_SIZE

        // Prepare Message: Length (4 bytes) + Signature (2 bytes) + Payload
        // Using fixed length header for robustness
        val sigBytes = HEADER_SIGNATURE.toByteArray(StandardCharsets.UTF_8)
        val msgBytes = message.toByteArray(StandardCharsets.UTF_8)
        val lenBytes = intToBytes(msgBytes.size)

        val allBytes = sigBytes + lenBytes + msgBytes
        val allBits = bytesToBits(allBytes)

        val totalBlocks = (width / BLOCK_SIZE) * (height / BLOCK_SIZE)
        if (allBits.size > totalBlocks) {
            return null // Not enough blocks (1 bit per 8x8 block for robustness)
        }

        // Reusable buffers to avoid massive GC churn
        val yBlock = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }
        val cbBlock = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }
        val crBlock = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }

        // DCT Output buffers
        val dctY = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }
        val idctY = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        var bitIndex = 0

        for (yBlockIdx in 0 until height step BLOCK_SIZE) {
            for (xBlockIdx in 0 until width step BLOCK_SIZE) {
                if (bitIndex >= allBits.size) break

                // Pass buffers explicitly
                embedBitInBlock(
                        mutableBitmap,
                        xBlockIdx,
                        yBlockIdx,
                        allBits[bitIndex],
                        yBlock,
                        cbBlock,
                        crBlock,
                        dctY,
                        idctY
                )
                bitIndex++
            }
            if (bitIndex >= allBits.size) break
        }

        return mutableBitmap
    }

    fun decode(bitmap: Bitmap): String? {
        val width = (bitmap.width / BLOCK_SIZE) * BLOCK_SIZE
        val height = (bitmap.height / BLOCK_SIZE) * BLOCK_SIZE

        var sigVerified = false
        var msgLength = -1

        val headerBits = ArrayList<Int>()
        val msgBits = ArrayList<Int>()

        // Reusable buffers
        val yBlock = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }
        val dctY = Array(BLOCK_SIZE) { DoubleArray(BLOCK_SIZE) }

        for (yBlockIdx in 0 until height step BLOCK_SIZE) {
            for (xBlockIdx in 0 until width step BLOCK_SIZE) {

                val bit = extractBitFromBlock(bitmap, xBlockIdx, yBlockIdx, yBlock, dctY)

                if (!sigVerified) {
                    headerBits.add(bit)
                    // Check if we have enough for signature (16 bits) + length (32 bits) = 48 bits
                    if (headerBits.size == 48) {
                        val headerBytes = bitsToBytes(headerBits)
                        val sig = String(headerBytes.sliceArray(0 until 2), StandardCharsets.UTF_8)

                        if (sig != HEADER_SIGNATURE) return null

                        sigVerified = true
                        msgLength = bytesToInt(headerBytes.sliceArray(2 until 6))

                        // Sanity check on length
                        val maxCapacity = (totalBlocks(width, height) - 48)
                        if (msgLength <= 0 || msgLength * 8 > maxCapacity) return null
                    }
                } else {
                    if (msgBits.size < msgLength * 8) {
                        msgBits.add(bit)
                    } else {
                        // Done
                        return String(bitsToBytes(msgBits), StandardCharsets.UTF_8)
                    }
                }
            }
            if (sigVerified && msgBits.size >= msgLength * 8) break
        }

        if (sigVerified && msgBits.size == msgLength * 8) {
            return String(bitsToBytes(msgBits), StandardCharsets.UTF_8)
        }

        return null
    }

    private fun embedBitInBlock(
            bitmap: Bitmap,
            startX: Int,
            startY: Int,
            bit: Int,
            yBlock: Array<DoubleArray>,
            cbBlock: Array<DoubleArray>,
            crBlock: Array<DoubleArray>,
            dctY: Array<DoubleArray>,
            idctY: Array<DoubleArray>
    ) {
        // 1. RGB -> YCbCr (Populate buffers)
        for (y in 0 until BLOCK_SIZE) {
            for (x in 0 until BLOCK_SIZE) {
                val pixel = bitmap.getPixel(startX + x, startY + y)
                val r = Color.red(pixel).toDouble()
                val g = Color.green(pixel).toDouble()
                val b = Color.blue(pixel).toDouble()

                // Standard JPEG conversion
                yBlock[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
                cbBlock[y][x] = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b
                crBlock[y][x] = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b
            }
        }

        // 2. DCT on Y channel (Reuse dctY buffer)
        performDCT(yBlock, dctY)

        // 3. Embed bit in DCT coeffs of Y
        val c1 = dctY[C1_INDEX / 8][C1_INDEX % 8]
        val c2 = dctY[C2_INDEX / 8][C2_INDEX % 8]

        // Use absolute values logic for robustness and sign check
        // Koch & Zhao:
        // To send 0: |C1| > |C2| + P
        // To send 1: |C2| > |C1| + P
        // We modify coefficients. Note: We're not doing full JPEG quantization step here,
        // but we assume these coeffs are "frequency strengths".

        var newC1 = c1
        var newC2 = c2
        val P = P_THRESHOLD

        if (bit == 0) {
            if (Math.abs(c1) <= Math.abs(c2) + P) {
                // Force C1 to be larger
                val target = Math.abs(c2) + P + 1
                newC1 = if (c1 >= 0) target else -target
            }
        } else {
            if (Math.abs(c2) <= Math.abs(c1) + P) {
                // Force C2 to be larger
                val target = Math.abs(c1) + P + 1
                newC2 = if (c2 >= 0) target else -target
            }
        }

        dctY[C1_INDEX / 8][C1_INDEX % 8] = newC1
        dctY[C2_INDEX / 8][C2_INDEX % 8] = newC2

        // 4. Inverse DCT (Reuse idctY buffer)
        performIDCT(dctY, idctY)

        // 5. YCbCr -> RGB & clamp
        for (y in 0 until BLOCK_SIZE) {
            for (x in 0 until BLOCK_SIZE) {
                val Y = idctY[y][x]
                val Cb = cbBlock[y][x]
                val Cr = crBlock[y][x]

                var r = (Y + 1.402 * (Cr - 128)).roundToInt()
                var g = (Y - 0.344136 * (Cb - 128) - 0.714136 * (Cr - 128)).roundToInt()
                var b = (Y + 1.772 * (Cb - 128)).roundToInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                bitmap.setPixel(startX + x, startY + y, Color.rgb(r, g, b))
            }
        }
    }

    private fun extractBitFromBlock(
            bitmap: Bitmap,
            startX: Int,
            startY: Int,
            yBlock: Array<DoubleArray>,
            dctY: Array<DoubleArray>
    ): Int {
        // RGB -> Y Only
        for (y in 0 until BLOCK_SIZE) {
            for (x in 0 until BLOCK_SIZE) {
                val pixel = bitmap.getPixel(startX + x, startY + y)
                val r = Color.red(pixel).toDouble()
                val g = Color.green(pixel).toDouble()
                val b = Color.blue(pixel).toDouble()
                yBlock[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
            }
        }

        performDCT(yBlock, dctY)

        val c1 = dctY[C1_INDEX / 8][C1_INDEX % 8]
        val c2 = dctY[C2_INDEX / 8][C2_INDEX % 8]

        return if (Math.abs(c1) > Math.abs(c2)) 0 else 1
    }

    // --- DCT Math Helpers ---

    private fun performDCT(input: Array<DoubleArray>, output: Array<DoubleArray>) {
        val N = BLOCK_SIZE.toDouble()

        for (u in 0 until BLOCK_SIZE) {
            for (v in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (x in 0 until BLOCK_SIZE) {
                    for (y in 0 until BLOCK_SIZE) {
                        sum +=
                                input[x][y] *
                                        cos((2 * x + 1) * u * PI / (2 * N)) *
                                        cos((2 * y + 1) * v * PI / (2 * N))
                    }
                }

                val alphaU = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val alphaV = if (v == 0) 1.0 / sqrt(2.0) else 1.0

                output[u][v] = 0.25 * alphaU * alphaV * sum
            }
        }
    }

    private fun performIDCT(input: Array<DoubleArray>, output: Array<DoubleArray>) {
        val N = BLOCK_SIZE.toDouble()

        for (x in 0 until BLOCK_SIZE) {
            for (y in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (u in 0 until BLOCK_SIZE) {
                    for (v in 0 until BLOCK_SIZE) {
                        val alphaU = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                        val alphaV = if (v == 0) 1.0 / sqrt(2.0) else 1.0

                        sum +=
                                alphaU *
                                        alphaV *
                                        input[u][v] *
                                        cos((2 * x + 1) * u * PI / (2 * N)) *
                                        cos((2 * y + 1) * v * PI / (2 * N))
                    }
                }
                output[x][y] = 0.25 * sum
            }
        }
    }

    private fun totalBlocks(width: Int, height: Int) = (width / BLOCK_SIZE) * (height / BLOCK_SIZE)

    private fun intToBytes(i: Int): ByteArray {
        return byteArrayOf((i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte())
    }

    private fun bytesToInt(b: ByteArray): Int {
        return (b[0].toInt() and 0xFF shl 24) or
                (b[1].toInt() and 0xFF shl 16) or
                (b[2].toInt() and 0xFF shl 8) or
                (b[3].toInt() and 0xFF)
    }

    private fun bytesToBits(bytes: ByteArray): IntArray {
        val bits = IntArray(bytes.size * 8)
        for (i in bytes.indices) {
            val b = bytes[i].toInt()
            for (j in 0 until 8) {
                bits[i * 8 + j] = (b shr (7 - j)) and 1
            }
        }
        return bits
    }

    private fun bitsToBytes(bits: List<Int>): ByteArray {
        val bytes = ByteArray(bits.size / 8)
        for (i in bytes.indices) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + j]
            }
            bytes[i] = b.toByte()
        }
        return bytes
    }
}
