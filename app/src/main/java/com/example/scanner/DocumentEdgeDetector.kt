package com.example.scanner

import android.graphics.Bitmap
import com.example.data.model.CropQuad
import com.example.data.model.NormalizedPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class EdgeDetectionResult(
    val quad: CropQuad,
    val confidence: Float,
    val isDocumentDetected: Boolean
)

/**
 * Lightweight, allocation-conscious real-time document boundary detector.
 * Uses downsampled luminance gradients + extreme-corner estimation to find
 * page/receipt/card borders in <15ms without heavy native OpenCV libraries.
 */
object DocumentEdgeDetector {

    fun detectFromBitmap(bitmap: Bitmap): EdgeDetectionResult {
        val targetW = 160
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceIn(40, 240)
        val small = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small !== bitmap) small.recycle()

        val lum = IntArray(targetW * targetH)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (r * 77 + g * 150 + b * 29) shr 8
        }
        return detectFromLuminance(lum, targetW, targetH)
    }

    fun detectFromLuminance(lum: IntArray, width: Int, height: Int): EdgeDetectionResult {
        if (width < 24 || height < 24 || lum.size < width * height) {
            return EdgeDetectionResult(defaultInsetQuad(), 0f, false)
        }

        // Compute horizontal + vertical gradient magnitude and brightness contrast against border
        val margin = 3
        var sumGrad = 0L
        var count = 0
        val grad = IntArray(width * height)

        for (y in margin until height - margin) {
            val rowOff = y * width
            for (x in margin until width - margin) {
                val idx = rowOff + x
                val gx = abs(lum[idx + 1] - lum[idx - 1])
                val gy = abs(lum[idx + width] - lum[idx - width])
                val g = gx + gy
                grad[idx] = g
                sumGrad += g
                count++
            }
        }

        val meanGrad = if (count > 0) (sumGrad / count).toInt() else 12
        val threshold = max(24, meanGrad * 2)

        // Find strong edge pixels near the 4 quadrants to anchor corners
        var tlBestX = (width * 0.1f).toInt()
        var tlBestY = (height * 0.1f).toInt()
        var tlBestScore = Int.MAX_VALUE

        var trBestX = (width * 0.9f).toInt()
        var trBestY = (height * 0.1f).toInt()
        var trBestScore = Int.MIN_VALUE

        var brBestX = (width * 0.9f).toInt()
        var brBestY = (height * 0.9f).toInt()
        var brBestScore = Int.MIN_VALUE

        var blBestX = (width * 0.1f).toInt()
        var blBestY = (height * 0.9f).toInt()
        var blBestScore = Int.MIN_VALUE

        var edgeHits = 0
        val halfW = width / 2
        val halfH = height / 2

        for (y in margin until height - margin step 2) {
            val rowOff = y * width
            for (x in margin until width - margin step 2) {
                if (grad[rowOff + x] >= threshold) {
                    edgeHits++
                    // TL minimizes (x + y)
                    if (x < halfW && y < halfH) {
                        val s = x + y
                        if (s < tlBestScore) {
                            tlBestScore = s; tlBestX = x; tlBestY = y
                        }
                    }
                    // TR maximizes (x - y)
                    if (x >= halfW && y < halfH) {
                        val s = x - y
                        if (s > trBestScore) {
                            trBestScore = s; trBestX = x; trBestY = y
                        }
                    }
                    // BR maximizes (x + y)
                    if (x >= halfW && y >= halfH) {
                        val s = x + y
                        if (s > brBestScore) {
                            brBestScore = s; brBestX = x; brBestY = y
                        }
                    }
                    // BL maximizes (y - x)
                    if (x < halfW && y >= halfH) {
                        val s = y - x
                        if (s > blBestScore) {
                            blBestScore = s; blBestX = x; blBestY = y
                        }
                    }
                }
            }
        }

        val tl = NormalizedPoint((tlBestX.toFloat() / width).coerceIn(0.02f, 0.45f), (tlBestY.toFloat() / height).coerceIn(0.02f, 0.45f))
        val tr = NormalizedPoint((trBestX.toFloat() / width).coerceIn(0.55f, 0.98f), (trBestY.toFloat() / height).coerceIn(0.02f, 0.45f))
        val br = NormalizedPoint((brBestX.toFloat() / width).coerceIn(0.55f, 0.98f), (brBestY.toFloat() / height).coerceIn(0.55f, 0.98f))
        val bl = NormalizedPoint((blBestX.toFloat() / width).coerceIn(0.02f, 0.45f), (blBestY.toFloat() / height).coerceIn(0.55f, 0.98f))

        val quad = CropQuad(tl, tr, br, bl)
        val area = computeQuadArea(quad)
        val detected = edgeHits > 25 && area in 0.20f..0.95f &&
            tlBestScore != Int.MAX_VALUE && trBestScore != Int.MIN_VALUE &&
            brBestScore != Int.MIN_VALUE && blBestScore != Int.MIN_VALUE

        return if (detected) {
            EdgeDetectionResult(quad, min(1f, area * 1.25f), true)
        } else {
            EdgeDetectionResult(defaultInsetQuad(), 0.1f, false)
        }
    }

    fun areQuadsClose(a: CropQuad, b: CropQuad, maxDelta: Float = 0.035f): Boolean {
        return dist(a.topLeft, b.topLeft) < maxDelta &&
            dist(a.topRight, b.topRight) < maxDelta &&
            dist(a.bottomRight, b.bottomRight) < maxDelta &&
            dist(a.bottomLeft, b.bottomLeft) < maxDelta
    }

    private fun dist(p1: NormalizedPoint, p2: NormalizedPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun computeQuadArea(q: CropQuad): Float {
        val x1 = q.topLeft.x; val y1 = q.topLeft.y
        val x2 = q.topRight.x; val y2 = q.topRight.y
        val x3 = q.bottomRight.x; val y3 = q.bottomRight.y
        val x4 = q.bottomLeft.x; val y4 = q.bottomLeft.y
        val cross = (x1 * y2 - y1 * x2) + (x2 * y3 - y2 * x3) + (x3 * y4 - y3 * x4) + (x4 * y1 - y4 * x1)
        return abs(cross) * 0.5f
    }

    fun defaultInsetQuad(): CropQuad = CropQuad(
        topLeft = NormalizedPoint(0.08f, 0.08f),
        topRight = NormalizedPoint(0.92f, 0.08f),
        bottomRight = NormalizedPoint(0.92f, 0.92f),
        bottomLeft = NormalizedPoint(0.08f, 0.92f)
    )
}
