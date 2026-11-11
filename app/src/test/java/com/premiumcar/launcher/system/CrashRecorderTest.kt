package com.premiumcar.launcher.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/** Erros que fecham o app vão para o diário sem nomes de músicas (etapa 0.4.1). */
class CrashRecorderTest {

    @Test
    fun `erro registrado sem o caminho do arquivo e com o local no codigo`() {
        val error = IllegalStateException("open failed: /storage/1234-ABCD/01 - Minha Música.mp3: ENOENT (No such file)")
        error.stackTrace = arrayOf(
            StackTraceElement("android.media.MediaPlayer", "setDataSource", "MediaPlayer.java", 1000),
            StackTraceElement("com.premiumcar.launcher.player.MusicPlayer", "prepare", "MusicPlayer.kt", 210),
            StackTraceElement("com.premiumcar.launcher.app.MainActivity", "onCreate", "MainActivity.kt", 75),
        )
        val text = CrashRecorder.describe("main", error)
        assertTrue(text, text.startsWith("Erro do app"))
        assertFalse(text, text.contains("Minha"))
        assertFalse(text, text.contains("1234-ABCD"))
        assertTrue(text, text.contains("IllegalStateException: open failed: <arquivo>: ENOENT (No such file)"))
        assertTrue(text, text.contains("em MusicPlayer.prepare(MusicPlayer.kt:210) ← MainActivity.onCreate(MainActivity.kt:75)"))
        assertFalse("Thread principal não é citada", text.contains("thread"))
    }

    @Test
    fun `causa, thread e caminho codificado em URI`() {
        val root = FileNotFoundException("content://media/external%2Fstorage%2F1234%2FMinha%20Musica.mp3")
        val text = CrashRecorder.describe("Background", RuntimeException("falhou", root))
        assertTrue(text, text.contains("RuntimeException: falhou"))
        assertTrue(text, text.contains("causa: FileNotFoundException: content://media/external<arquivo>"))
        assertFalse(text, text.contains("Minha"))
        assertTrue(text, text.endsWith("thread Background"))
    }

    @Test
    fun `mensagem longa e cortada`() {
        val text = CrashRecorder.describe("main", IllegalArgumentException("x".repeat(500)))
        assertTrue(text, text.contains("x".repeat(140) + "…"))
        assertFalse(text, text.contains("x".repeat(141)))
    }
}
