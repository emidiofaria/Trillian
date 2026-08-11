package com.drivingcoach.brand

import java.io.File
import java.util.zip.Inflater

/**
 * Minimal PNG reader for brand-asset tests.
 *
 * Android unit tests run against `android.jar`, which contains neither `java.awt`
 * nor `javax.imageio`, so [javax.imageio.ImageIO] is unavailable here. Rather than
 * pull in an image library purely for a regression gate, this decodes the single
 * PNG flavour the brand fixtures are stored in: 8-bit truecolour-with-alpha
 * (colour type 6), non-interlaced. `java.util.zip.Inflater` *is* part of
 * `android.jar`, so no new dependency is required.
 *
 * Anything outside that flavour throws rather than silently mis-decoding.
 */
internal class ArgbBitmap(val width: Int, val height: Int, private val px: IntArray) {

    /** Alpha channel of the pixel at ([x], [y]), 0..255. */
    fun alpha(x: Int, y: Int): Int = px[y * width + x] ushr 24 and 0xFF

    /**
     * Box-downsamples to [size]x[size] and returns the fraction of destination
     * pixels whose averaged alpha exceeds [threshold]. Used to prove the artwork
     * still reads as an emblem at the smallest render site.
     */
    fun opaqueRatioAt(size: Int, threshold: Int = 8): Double {
        var visible = 0
        for (dy in 0 until size) {
            for (dx in 0 until size) {
                val x0 = dx * width / size
                val x1 = maxOf(x0 + 1, (dx + 1) * width / size)
                val y0 = dy * height / size
                val y1 = maxOf(y0 + 1, (dy + 1) * height / size)
                var sum = 0L
                var n = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        sum += alpha(x, y)
                        n++
                    }
                }
                if (n > 0 && sum / n > threshold) visible++
            }
        }
        return visible.toDouble() / (size * size)
    }

    companion object {
        private val SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        fun decode(file: File): ArgbBitmap {
            val b = file.readBytes()
            require(b.size > 8 && b.copyOfRange(0, 8).contentEquals(SIGNATURE)) {
                "not a PNG: ${file.path}"
            }

            var width = 0
            var height = 0
            val idat = java.io.ByteArrayOutputStream()
            var pos = 8
            while (pos + 8 <= b.size) {
                val len = be32(b, pos)
                val type = String(b, pos + 4, 4, Charsets.US_ASCII)
                val data = pos + 8
                when (type) {
                    "IHDR" -> {
                        width = be32(b, data)
                        height = be32(b, data + 4)
                        val depth = b[data + 8].toInt()
                        val colourType = b[data + 9].toInt()
                        val interlace = b[data + 12].toInt()
                        require(depth == 8 && colourType == 6 && interlace == 0) {
                            "${file.name}: expected 8-bit RGBA non-interlaced PNG, " +
                                "got depth=$depth colourType=$colourType interlace=$interlace"
                        }
                    }
                    "IDAT" -> idat.write(b, data, len)
                    "IEND" -> pos = b.size
                }
                pos = data + len + 4
            }
            require(width > 0 && height > 0) { "${file.name}: no IHDR chunk" }

            val bpp = 4
            val stride = width * bpp
            val raw = inflate(idat.toByteArray(), (stride + 1) * height)
            require(raw.size >= (stride + 1) * height) {
                "${file.name}: truncated image data"
            }

            val out = ByteArray(stride * height)
            for (y in 0 until height) {
                val filter = raw[y * (stride + 1)].toInt() and 0xFF
                val src = y * (stride + 1) + 1
                val dst = y * stride
                val up = dst - stride
                for (i in 0 until stride) {
                    val x = raw[src + i].toInt() and 0xFF
                    val a = if (i >= bpp) out[dst + i - bpp].toInt() and 0xFF else 0
                    val bb = if (y > 0) out[up + i].toInt() and 0xFF else 0
                    val c = if (y > 0 && i >= bpp) out[up + i - bpp].toInt() and 0xFF else 0
                    val v = when (filter) {
                        0 -> x
                        1 -> x + a
                        2 -> x + bb
                        3 -> x + (a + bb) / 2
                        4 -> x + paeth(a, bb, c)
                        else -> throw IllegalArgumentException(
                            "${file.name}: unknown PNG filter $filter on row $y"
                        )
                    }
                    out[dst + i] = (v and 0xFF).toByte()
                }
            }

            val px = IntArray(width * height)
            for (i in px.indices) {
                val o = i * 4
                px[i] = ((out[o + 3].toInt() and 0xFF) shl 24) or
                    ((out[o].toInt() and 0xFF) shl 16) or
                    ((out[o + 1].toInt() and 0xFF) shl 8) or
                    (out[o + 2].toInt() and 0xFF)
            }
            return ArgbBitmap(width, height, px)
        }

        private fun be32(b: ByteArray, i: Int): Int =
            ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
                ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

        private fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = Math.abs(p - a)
            val pb = Math.abs(p - b)
            val pc = Math.abs(p - c)
            return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
        }

        private fun inflate(src: ByteArray, expected: Int): ByteArray {
            val inf = Inflater()
            inf.setInput(src)
            val out = ByteArray(expected)
            var n = 0
            while (n < expected && !inf.finished()) {
                val r = inf.inflate(out, n, expected - n)
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break
                n += r
            }
            inf.end()
            return if (n == expected) out else out.copyOf(n)
        }
    }
}
