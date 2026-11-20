package com.example.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Non-destructive image processing engine supporting:
 * - Perspective warp via Matrix.setPolyToPoly
 * - Rotation (0/90/180/270)
 * - All 16 scanner filters with adjustable intensity & fine sliders
 */
object ImageProcessor {

    fun decodeSampledBitmap(path: String, maxLongEdge: Int = 2200): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        val longEdge = max(opts.outWidth, opts.outHeight)
        var sample = 1
        while (longEdge / sample > maxLongEdge) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOpts)
    }

    fun applyAdjustments(
        source: Bitmap,
        adj: ImageAdjustments,
        maxOutputLongEdge: Int = 2200
    ): Bitmap {
        // 1. Perspective warp + crop
        val warped = if (adj.cropQuad.isFullFrame()) {
            scaleIfNeeded(source, maxOutputLongEdge)
        } else {
            perspectiveWarp(source, adj.cropQuad, maxOutputLongEdge)
        }

        // 2. Rotation
        val rotated = if (adj.rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate((adj.rotationDegrees % 360).toFloat()) }
            val r = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, m, true)
            if (warped !== source) warped.recycle()
            r
        } else {
            warped
        }

        // 3. Pixel + ColorMatrix filters
        val result = applyFilterPipeline(rotated, adj)
        if (rotated !== source && rotated !== result) {
            rotated.recycle()
        }
        return result
    }

    fun perspectiveWarp(source: Bitmap, quad: CropQuad, maxLongEdge: Int = 2200): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tlX = quad.topLeft.x * w
        val tlY = quad.topLeft.y * h
        val trX = quad.topRight.x * w
        val trY = quad.topRight.y * h
        val brX = quad.bottomRight.x * w
        val brY = quad.bottomRight.y * h
        val blX = quad.bottomLeft.x * w
        val blY = quad.bottomLeft.y * h

        val topW = hypot((trX - tlX).toDouble(), (trY - tlY).toDouble()).toFloat()
        val botW = hypot((brX - blX).toDouble(), (brY - blY).toDouble()).toFloat()
        val leftH = hypot((blX - tlX).toDouble(), (blY - tlY).toDouble()).toFloat()
        val rightH = hypot((brX - trX).toDouble(), (brY - trY).toDouble()).toFloat()

        var outW = max(topW, botW).roundToInt().coerceAtLeast(64)
        var outH = max(leftH, rightH).roundToInt().coerceAtLeast(64)

        val longest = max(outW, outH)
        if (longest > maxLongEdge) {
            val s = maxLongEdge.toFloat() / longest
            outW = (outW * s).roundToInt().coerceAtLeast(64)
            outH = (outH * s).roundToInt().coerceAtLeast(64)
        }

        val srcPts = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
        val dstPts = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!ok) return scaleIfNeeded(source, maxLongEdge)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    private fun scaleIfNeeded(source: Bitmap, maxLongEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxLongEdge) return source.copy(Bitmap.Config.ARGB_8888, true)
        val s = maxLongEdge.toFloat() / longest
        val w = (source.width * s).roundToInt().coerceAtLeast(1)
        val h = (source.height * s).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun applyFilterPipeline(input: Bitmap, adj: ImageAdjustments): Bitmap {
        val intensity = adj.intensity.coerceIn(0f, 1.5f)
        val cm = ColorMatrix()

        // Base filter presets
        when (adj.filterType) {
            FilterType.ORIGINAL -> Unit
            FilterType.AUTO_ENHANCE -> {
                cm.postConcat(contrastBrightnessMatrix(1.22f, 10f, intensity))
                val sat = ColorMatrix().apply { setSaturation(1.0f + 0.18f * intensity) }
                cm.postConcat(sat)
            }
            FilterType.MAGIC_COLOR -> {
                cm.postConcat(contrastBrightnessMatrix(1.38f, 14f, intensity))
                val sat = ColorMatrix().apply { setSaturation(1.0f + 0.45f * intensity) }
                cm.postConcat(sat)
            }
            FilterType.BLACK_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(gray)
            }
            FilterType.GRAYSCALE -> {
                val gray = ColorMatrix().apply { setSaturation(1f - intensity.coerceIn(0f, 1f)) }
                cm.postConcat(gray)
                cm.postConcat(contrastBrightnessMatrix(1.12f, 6f, intensity))
            }
            FilterType.DOCUMENT_CLEAR -> {
                cm.postConcat(contrastBrightnessMatrix(1.42f, 22f, intensity))
                val sat = ColorMatrix().apply { setSaturation(0.75f) }
                cm.postConcat(sat)
            }
            FilterType.PHOTO_MODE -> {
                val sat = ColorMatrix().apply { setSaturation(1.0f + 0.15f * intensity) }
                cm.postConcat(sat)
                cm.postConcat(contrastBrightnessMatrix(1.08f, 4f, intensity))
            }
            FilterType.HIGH_CONTRAST -> {
                cm.postConcat(contrastBrightnessMatrix(1.75f, -18f, intensity))
            }
            FilterType.BRIGHTNESS -> {
                cm.postConcat(contrastBrightnessMatrix(1.0f, 38f, intensity))
            }
            FilterType.CONTRAST -> {
                cm.postConcat(contrastBrightnessMatrix(1.45f, 0f, intensity))
            }
            FilterType.SHARPNESS -> {
                cm.postConcat(contrastBrightnessMatrix(1.1f, 4f, intensity))
            }
            FilterType.SATURATION -> {
                val sat = ColorMatrix().apply { setSaturation(1.0f + 0.65f * intensity) }
                cm.postConcat(sat)
            }
            FilterType.EXPOSURE -> {
                val gain = 1f + 0.35f * intensity
                cm.postConcat(ColorMatrix(floatArrayOf(
                    gain, 0f, 0f, 0f, 8f,
                    0f, gain, 0f, 0f, 8f,
                    0f, 0f, gain, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterType.NOISE_REDUCTION -> {
                cm.postConcat(contrastBrightnessMatrix(1.05f, 4f, intensity))
            }
            FilterType.SHADOW_REDUCTION -> {
                cm.postConcat(contrastBrightnessMatrix(1.15f, 26f, intensity))
            }
            FilterType.BACKGROUND_WHITENING -> {
                cm.postConcat(contrastBrightnessMatrix(1.48f, 30f, intensity))
            }
        }

        // Apply custom user adjustments (brightness, contrast, saturation, exposure)
        val expGain = (1f + adj.exposure * 0.45f).coerceIn(0.5f, 1.8f)
        val totalContrast = (adj.contrast * expGain).coerceIn(0.4f, 2.5f)
        val totalBrightness = adj.brightness + (adj.exposure * 18f)
        if (totalContrast != 1f || totalBrightness != 0f) {
            cm.postConcat(contrastBrightnessMatrix(totalContrast, totalBrightness, 1f))
        }
        if (adj.saturation != 1f && adj.filterType != FilterType.BLACK_WHITE && adj.filterType != FilterType.GRAYSCALE) {
            val userSat = ColorMatrix().apply { setSaturation(adj.saturation.coerceIn(0f, 2f)) }
            cm.postConcat(userSat)
        }

        var working = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(working)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(input, 0f, 0f, paint)

        // Pixel-level passes when requested or active in filter preset
        val doAdaptiveBw = adj.filterType == FilterType.BLACK_WHITE
        val doShadowLift = adj.shadowReduction > 0.05f || adj.filterType == FilterType.SHADOW_REDUCTION || adj.filterType == FilterType.DOCUMENT_CLEAR
        val doWhitenBg = adj.backgroundWhitening > 0.05f || adj.filterType == FilterType.BACKGROUND_WHITENING || adj.filterType == FilterType.DOCUMENT_CLEAR
        val doDenoise = adj.noiseReduction > 0.1f || adj.filterType == FilterType.NOISE_REDUCTION
        val doSharpen = adj.sharpness > 0.1f || adj.filterType == FilterType.SHARPNESS || adj.filterType == FilterType.DOCUMENT_CLEAR

        if (doAdaptiveBw || doShadowLift || doWhitenBg) {
            applyToneAndThresholdPass(
                bitmap = working,
                adaptiveBw = doAdaptiveBw,
                bwStrength = intensity.coerceIn(0.3f, 1f),
                shadowLift = if (adj.filterType == FilterType.SHADOW_REDUCTION) max(0.75f, adj.shadowReduction) else adj.shadowReduction,
                whitenBg = if (adj.filterType == FilterType.BACKGROUND_WHITENING) max(0.8f, adj.backgroundWhitening) else adj.backgroundWhitening
            )
        }

        if (doDenoise) {
            val strength = if (adj.filterType == FilterType.NOISE_REDUCTION) max(0.65f, adj.noiseReduction) else adj.noiseReduction
            working = applyFastBoxBlurMix(working, strength.coerceIn(0f, 1f))
        }

        if (doSharpen && !doAdaptiveBw) {
            val amount = if (adj.filterType == FilterType.SHARPNESS) max(0.7f, adj.sharpness) else adj.sharpness
            working = applyFastUnsharpMask(working, amount.coerceIn(0f, 1f))
        }

        return working
    }

    private fun contrastBrightnessMatrix(contrast: Float, brightness: Float, intensity: Float): ColorMatrix {
        val c = 1f + (contrast - 1f) * intensity
        val b = brightness * intensity
        val t = (1f - c) * 128f + b
        return ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun applyToneAndThresholdPass(
        bitmap: Bitmap,
        adaptiveBw: Boolean,
        bwStrength: Float,
        shadowLift: Float,
        whitenBg: Float
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        if (adaptiveBw) {
            // Fast 2-pass integral-image adaptive threshold for razor-sharp black & white text
            val lum = IntArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                lum[i] = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            }
            val integral = LongArray(w * h)
            for (y in 0 until h) {
                var rowSum = 0L
                val rowOff = y * w
                for (x in 0 until w) {
                    rowSum += lum[rowOff + x]
                    integral[rowOff + x] = rowSum + if (y > 0) integral[rowOff - w + x] else 0L
                }
            }
            val radius = max(12, min(w, h) / 28)
            val bias = (12f + 10f * bwStrength).toInt()
            for (y in 0 until h) {
                val y1 = max(0, y - radius)
                val y2 = min(h - 1, y + radius)
                val rowOff = y * w
                for (x in 0 until w) {
                    val x1 = max(0, x - radius)
                    val x2 = min(w - 1, x + radius)
                    val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                    var sum = integral[y2 * w + x2]
                    if (x1 > 0) sum -= integral[y2 * w + (x1 - 1)]
                    if (y1 > 0) sum -= integral[(y1 - 1) * w + x2]
                    if (x1 > 0 && y1 > 0) sum += integral[(y1 - 1) * w + (x1 - 1)]
                    val localMean = (sum / area).toInt()
                    val v = if (lum[rowOff + x] < localMean - bias) 12 else 255
                    pixels[rowOff + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        } else {
            val whiteThresh = (228 - whitenBg * 46f).toInt().coerceIn(160, 245)
            val liftFactor = shadowLift.coerceIn(0f, 1f) * 0.45f
            for (i in pixels.indices) {
                val p = pixels[i]
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                val l = (r * 77 + g * 150 + b * 29) shr 8

                if (liftFactor > 0f && l < 130) {
                    val boost = ((130 - l) * liftFactor).toInt()
                    r = min(255, r + boost)
                    g = min(255, g + boost)
                    b = min(255, b + boost)
                }
                if (whitenBg > 0.05f && l >= whiteThresh) {
                    val blend = ((l - whiteThresh).toFloat() / (255 - whiteThresh).coerceAtLeast(1)) * whitenBg
                    r = (r + (255 - r) * blend).toInt().coerceIn(0, 255)
                    g = (g + (255 - g) * blend).toInt().coerceIn(0, 255)
                    b = (b + (255 - b) * blend).toInt().coerceIn(0, 255)
                }
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun applyFastUnsharpMask(bitmap: Bitmap, amount: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        val k = amount * 0.65f

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = src[idx]
                val up = src[idx - w]
                val dn = src[idx + w]
                val lf = src[idx - 1]
                val rt = src[idx + 1]

                val rC = (c shr 16) and 0xFF
                val gC = (c shr 8) and 0xFF
                val bC = c and 0xFF

                val rBlur = (((up shr 16) and 0xFF) + ((dn shr 16) and 0xFF) + ((lf shr 16) and 0xFF) + ((rt shr 16) and 0xFF)) shr 2
                val gBlur = (((up shr 8) and 0xFF) + ((dn shr 8) and 0xFF) + ((lf shr 8) and 0xFF) + ((rt shr 8) and 0xFF)) shr 2
                val bBlur = ((up and 0xFF) + (dn and 0xFF) + (lf and 0xFF) + (rt and 0xFF)) shr 2

                val r = (rC + ((rC - rBlur) * k)).toInt().coerceIn(0, 255)
                val g = (gC + ((gC - gBlur) * k)).toInt().coerceIn(0, 255)
                val b = (bC + ((bC - bBlur) * k)).toInt().coerceIn(0, 255)
                dst[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(dst, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun applyFastBoxBlurMix(bitmap: Bitmap, strength: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        val mix = strength * 0.6f

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = src[idx]
                val up = src[idx - w]
                val dn = src[idx + w]
                val lf = src[idx - 1]
                val rt = src[idx + 1]

                val rC = (c shr 16) and 0xFF
                val gC = (c shr 8) and 0xFF
                val bC = c and 0xFF

                val rAvg = (((up shr 16) and 0xFF) + ((dn shr 16) and 0xFF) + ((lf shr 16) and 0xFF) + ((rt shr 16) and 0xFF)) shr 2
                val gAvg = (((up shr 8) and 0xFF) + ((dn shr 8) and 0xFF) + ((lf shr 8) and 0xFF) + ((rt shr 8) and 0xFF)) shr 2
                val bAvg = ((up and 0xFF) + (dn and 0xFF) + (lf and 0xFF) + (rt and 0xFF)) shr 2

                val r = (rC + ((rAvg - rC) * mix)).toInt().coerceIn(0, 255)
                val g = (gC + ((gAvg - gC) * mix)).toInt().coerceIn(0, 255)
                val b = (bC + ((bAvg - bC) * mix)).toInt().coerceIn(0, 255)
                dst[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(dst, 0, w, 0, 0, w, h)
        return bitmap
    }
}
