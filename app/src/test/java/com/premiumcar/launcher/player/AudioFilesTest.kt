package com.premiumcar.launcher.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regras puras do player: o que é música, o que é pasta de sistema e a ordem da lista. */
class AudioFilesTest {

    @Test
    fun `reconhece musicas pela extensao, sem diferenciar maiusculas`() {
        assertTrue(AudioFiles.isAudio("01 - Abertura.mp3"))
        assertTrue(AudioFiles.isAudio("FAIXA.MP3"))
        assertTrue(AudioFiles.isAudio("ao vivo.flac"))
        assertTrue(AudioFiles.isAudio("antiga.wma"))
        assertFalse(AudioFiles.isAudio("capa.jpg"))
        assertFalse(AudioFiles.isAudio("sem extensão"))
        // Arquivo oculto que o macOS cria ao copiar para o pendrive.
        assertFalse(AudioFiles.isAudio("._01 - Abertura.mp3"))
    }

    @Test
    fun `pastas de sistema e ocultas ficam fora da navegacao`() {
        assertTrue(AudioFiles.isBrowsableFolder("Rock"))
        assertTrue(AudioFiles.isBrowsableFolder("Anos 80"))
        listOf("LOST.DIR", "System Volume Information", "\$RECYCLE.BIN", ".Trashes", "Android", "FOUND.000", " ").forEach {
            assertFalse(it, AudioFiles.isBrowsableFolder(it))
        }
    }

    @Test
    fun `nome exibido tira so a extensao de musica`() {
        assertEquals("01 - Abertura", AudioFiles.displayName("01 - Abertura.mp3"))
        assertEquals("Faixa.Bonus", AudioFiles.displayName("Faixa.Bonus.M4A"))
        assertEquals("Pasta.com.ponto", AudioFiles.displayName("Pasta.com.ponto"))
    }

    @Test
    fun `ordem natural como num gerenciador de arquivos`() {
        val names = listOf("faixa 10.mp3", "Faixa 2.mp3", "Zebra.mp3", "álbum.mp3", "faixa 1.mp3", "Faixa 02b.mp3")
        assertEquals(
            listOf("álbum.mp3", "faixa 1.mp3", "Faixa 2.mp3", "Faixa 02b.mp3", "faixa 10.mp3", "Zebra.mp3"),
            names.sortedWith(AudioFiles.naturalOrder()),
        )
        val files = AudioFiles.sorted(listOf(File("/p/10 - c.mp3"), File("/p/9 - b.mp3"), File("/p/1 - a.mp3")))
        assertEquals(listOf("1 - a.mp3", "9 - b.mp3", "10 - c.mp3"), files.map { it.name })
    }

    @Test
    fun `pendrive contem so os proprios arquivos`() {
        val volume = MusicVolume(File("/storage/1234-ABCD"), "Pendrive")
        assertTrue(volume.contains(File("/storage/1234-ABCD")))
        assertTrue(volume.contains(File("/storage/1234-ABCD/Rock/a.mp3")))
        assertFalse(volume.contains(File("/storage/1234-ABCDE/a.mp3")))
        assertFalse(volume.contains(File("/storage/emulated/0/a.mp3")))
    }
}
