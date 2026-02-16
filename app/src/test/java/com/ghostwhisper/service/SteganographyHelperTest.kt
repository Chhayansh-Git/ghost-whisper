package com.ghostwhisper.service

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SteganographyHelperTest {

    @Test
    fun testEncodeAndDecode() {
        // Create a 100x100 bitmap
        val width = 100
        val height = 100
        val originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Fill with some color/noise
        for (x in 0 until width) {
            for (y in 0 until height) {
                originalBitmap.setPixel(x, y, Color.argb(255, x * 2, y * 2, 100))
            }
        }

        val secretMessage = "This is a secret message hidden in the pixels! 👻"

        val encodedBitmap = SteganographyHelper.encode(originalBitmap, secretMessage)
        assertNotNull("Encoded bitmap should not be null", encodedBitmap)

        val decodedMessage = SteganographyHelper.decode(encodedBitmap!!)
        assertEquals("Decoded message should match original", secretMessage, decodedMessage)
    }

    @Test
    fun testMessageTooLong() {
        val width = 10
        val height = 10
        val smallBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Capacity = 10 * 10 * 3 bits = 300 bits
        // Header = 16 bits (sig) + 32 bits (len) = 48 bits
        // Remaining = 252 bits = 31 bytes

        val longMessage =
                "This message is definitely longer than 31 bytes and should fail to encode."

        val result = SteganographyHelper.encode(smallBitmap, longMessage)
        assertNull("Should return null for message too long", result)
    }
}
