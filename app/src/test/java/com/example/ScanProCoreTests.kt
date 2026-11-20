package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.ImageAdjustments
import com.example.data.model.NormalizedPoint
import com.example.localization.BengaliStrings
import com.example.localization.EnglishStrings
import com.example.scanner.DocumentEdgeDetector
import com.example.scanner.ImageProcessor
import com.example.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanProCoreTests {

    @Test
    fun testLocalizationBilingualCompleteness() {
        assertEquals("ScanPro PDF", EnglishStrings.appName)
        assertEquals("ScanPro PDF", BengaliStrings.appName)
        assertTrue(BengaliStrings.navHome.any { it.code in 0x0980..0x09FF })
        assertTrue(BengaliStrings.homeScanNow.any { it.code in 0x0980..0x09FF })
        FilterType.entries.forEach { ft ->
            assertTrue(EnglishStrings.filterName(ft).isNotBlank())
            assertTrue(BengaliStrings.filterName(ft).isNotBlank())
        }
    }

    @Test
    fun testSecurityPinHashingAndVerification() {
        val sec = SecurityManager()
        val (hash, salt) = sec.hashPin("4829")
        assertTrue(sec.verifyPin("4829", hash, salt))
        assertFalse(sec.verifyPin("0000", hash, salt))
    }

    @Test
    fun testDocumentEdgeDetectionAndPerspectiveWarp() {
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.DKGRAY)
        for (y in 30 until 250) {
            for (x in 25 until 175) {
                bmp.setPixel(x, y, Color.WHITE)
            }
        }
        val edgeRes = DocumentEdgeDetector.detectFromBitmap(bmp)
        assertTrue(edgeRes.isDocumentDetected)

        val warped = ImageProcessor.perspectiveWarp(
            bmp,
            CropQuad(
                NormalizedPoint(0.12f, 0.10f),
                NormalizedPoint(0.88f, 0.10f),
                NormalizedPoint(0.88f, 0.90f),
                NormalizedPoint(0.12f, 0.90f)
            )
        )
        assertTrue(warped.width > 100)
        assertTrue(warped.height > 100)
    }

    @Test
    fun testAll16ImageFiltersExecuteCleanly() {
        val src = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.LTGRAY)
        FilterType.entries.forEach { filter ->
            val out = ImageProcessor.applyAdjustments(
                src,
                ImageAdjustments(filterType = filter, intensity = 1f, rotationDegrees = 90)
            )
            assertNotNull(out)
            assertEquals(160, out.width)
            assertEquals(120, out.height)
            out.recycle()
        }
        src.recycle()
    }
}
