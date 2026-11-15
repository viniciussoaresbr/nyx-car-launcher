package com.premiumcar.launcher.player

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Capas pequena (cartões, sessão de mídia, notificação) e grande (player grande, 0.4.24). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27])
class TrackReaderTest {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun `capa grande do arquivo vira 256 px para os cartoes e 440 px para o player grande`() {
        val (small, large) = TrackReader.decodeCovers(jpeg(1000, 1000))!!
        assertEquals(256, small.width)
        assertEquals(256, small.height)
        assertEquals(440, large.width)
        assertEquals(440, large.height)
        assertEquals("16 bits: metade da memória", Bitmap.Config.RGB_565, large.config)
    }

    @Test
    fun `capa retangular mantem a proporcao`() {
        val (small, large) = TrackReader.decodeCovers(jpeg(1200, 600))!!
        assertEquals(440, large.width)
        assertEquals(220, large.height)
        assertEquals(256, small.width)
        assertEquals(128, small.height)
    }

    @Test
    fun `capa menor que os dois tamanhos nao e ampliada`() {
        val (small, large) = TrackReader.decodeCovers(jpeg(300, 300))!!
        assertEquals(300, large.width)
        assertEquals(256, small.width)

        val (tinySmall, tinyLarge) = TrackReader.decodeCovers(jpeg(200, 200))!!
        assertEquals(200, tinyLarge.width)
        assertSame("Uma só imagem na memória", tinyLarge, tinySmall)
    }

    @Test
    fun `imagem invalida fica sem capa`() {
        assertNull(TrackReader.decodeCovers(byteArrayOf(1, 2, 3)))
    }
}
