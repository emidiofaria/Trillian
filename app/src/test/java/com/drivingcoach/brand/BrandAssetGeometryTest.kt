package com.drivingcoach.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * L1 (SWE.4) regression gate for Incident 11 — deformed helmet emblem.
 *
 * The deformed emblem shipped because the only assertions touching it were
 * `isDisplayed()` checks, which pass for *any* drawable — including a blank one.
 * This gate measures the artwork itself, so geometrically broken brand assets
 * cannot reach a build again.
 *
 * Geometry is measured on a PNG master committed to test resources. That master is
 * the exact source the shipped `drawable-*` WebP buckets are derived from, and
 * [emblemDensityBucketsAreCompleteAndCorrectlySized] proves the shipped buckets
 * still match it. Neither `javax.imageio` nor a WebP decoder exists on the Android
 * unit-test classpath, so the PNG is read via [ArgbBitmap] and the WebP files are
 * validated by parsing their container headers.
 */
class BrandAssetGeometryTest {

    private val resDir = File("src/main/res")
    private val brandDir = File("src/test/resources/brand")

    private fun load(name: String): ArgbBitmap {
        val f = File(brandDir, name)
        assertTrue("missing brand fixture: ${f.path}", f.isFile)
        return ArgbBitmap.decode(f)
    }

    /** Bounding box of every pixel with alpha > 0, as (left, top, width, height). */
    private fun contentBounds(img: ArgbBitmap): IntArray {
        var minX = img.width
        var minY = img.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                if (img.alpha(x, y) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        assertTrue("asset has no visible pixels at all", maxX >= 0)
        return intArrayOf(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /**
     * Throws [AssertionError] if [img] is not a well-formed emblem.
     *
     * Extracted so [gateRejectsTheLegacyDeformedEmblem] can prove the gate really
     * does fail on known-bad artwork — an assertion that cannot fail is not
     * coverage, which is precisely how Incident 11 escaped review.
     */
    private fun assertEmblemGeometry(img: ArgbBitmap) {
        assertEquals("emblem canvas must be square", img.width, img.height)

        val b = contentBounds(img)
        val left = b[0]
        val top = b[1]
        val w = b[2]
        val h = b[3]

        // 1. Aspect. The legacy emblem measured 388x336 (0.866) — visibly squashed.
        val aspect = h.toDouble() / w.toDouble()
        assertTrue(
            "emblem content must be square within 5%%: %dx%d -> aspect %.3f".format(w, h, aspect),
            aspect in 0.95..1.05
        )

        // 2. Centring. The legacy emblem's shell sat 6/120 viewport units high.
        val offX = Math.abs((left + w / 2.0) - img.width / 2.0) / img.width
        val offY = Math.abs((top + h / 2.0) - img.height / 2.0) / img.height
        assertTrue("content off-centre horizontally by %.1f%%".format(offX * 100), offX <= 0.03)
        assertTrue("content off-centre vertically by %.1f%%".format(offY * 100), offY <= 0.03)

        // 3. Transparent border, so nothing is clipped inside the 180dp hero ring.
        assertTrue("artwork touches the canvas edge and may be clipped", borderIsTransparent(img))

        // 4. Must still read as an emblem at the smallest render site (36dp).
        val ratio = img.opaqueRatioAt(36)
        assertTrue("artwork all but disappears at 36dp (%.3f)".format(ratio), ratio > 0.15)
    }

    private fun borderIsTransparent(img: ArgbBitmap): Boolean {
        for (x in 0 until img.width) {
            if (img.alpha(x, 0) != 0 || img.alpha(x, img.height - 1) != 0) return false
        }
        for (y in 0 until img.height) {
            if (img.alpha(0, y) != 0 || img.alpha(img.width - 1, y) != 0) return false
        }
        return true
    }

    @Test
    fun emblemMasterSatisfiesBrandGeometry() {
        assertEmblemGeometry(load("ic_helmet_emblem_master.png"))
    }

    /**
     * Falsification. Runs the identical gate against the emblem that shipped in
     * `7350eb3` and was reported as Incident 11. If this test ever stops observing
     * a failure, the gate has been weakened into a no-op.
     */
    @Test
    fun gateRejectsTheLegacyDeformedEmblem() {
        try {
            assertEmblemGeometry(load("legacy_deformed_emblem.png"))
        } catch (expected: AssertionError) {
            return
        }
        fail("gate accepted the known-deformed legacy emblem — the gate is not falsifiable")
    }

    @Test
    fun emblemDensityBucketsAreCompleteAndCorrectlySized() {
        val expected = linkedMapOf(
            "mdpi" to 132, "hdpi" to 198, "xhdpi" to 264, "xxhdpi" to 396, "xxxhdpi" to 528
        )
        expected.forEach { (bucket, px) ->
            val f = File(resDir, "drawable-$bucket/ic_helmet_emblem.webp")
            assertTrue("missing density bucket: drawable-$bucket/ic_helmet_emblem.webp", f.isFile)
            val (w, h) = webpDimensions(f)
            assertEquals("drawable-$bucket emblem width", px, w)
            assertEquals("drawable-$bucket emblem height", px, h)
        }
    }

    /**
     * A same-named vector alongside the bitmaps is a resource-merger conflict and
     * could non-deterministically resurrect the deformed artwork.
     */
    @Test
    fun noConflictingVectorEmblemRemains() {
        assertTrue(
            "drawable/ic_helmet_emblem.xml must not coexist with the WebP buckets",
            !File(resDir, "drawable/ic_helmet_emblem.xml").exists()
        )
    }

    /** Reads width/height straight out of a VP8L or VP8X WebP container header. */
    private fun webpDimensions(f: File): Pair<Int, Int> {
        val b = f.readBytes()
        assertTrue(
            "not a WebP file: ${f.path}",
            b.size > 30 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP"
        )
        fun u(i: Int) = b[i].toInt() and 0xFF
        return when (val chunk = String(b, 12, 4)) {
            "VP8L" -> {
                // byte 20 is the VP8L signature; 14 bits of width then 14 of height follow.
                assertEquals("bad VP8L signature in ${f.path}", 0x2F, u(20))
                val bits = (u(24) shl 24) or (u(23) shl 16) or (u(22) shl 8) or u(21)
                Pair((bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
            }
            "VP8X" -> Pair(
                ((u(26) shl 16) or (u(25) shl 8) or u(24)) + 1,
                ((u(29) shl 16) or (u(28) shl 8) or u(27)) + 1
            )
            else -> throw IllegalArgumentException("unsupported WebP chunk '$chunk' in ${f.path}")
        }
    }
}
