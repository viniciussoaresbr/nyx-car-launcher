package com.premiumcar.launcher.diagnostics

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Relatórios de 0 bytes na central (10/11/2026): gravação conferida e linha do tempo em disco. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27]) // API real da central
class ReportSavingTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `grava o relatorio inteiro no local escolhido e confirma o tamanho`() {
        val text = "NYX CAR — relatório ✓\n".repeat(2_000)
        // O seletor cria o arquivo vazio antes de o app gravar.
        val target = File(context.cacheDir, "pendrive.txt").apply { writeText("") }

        val bytes = ReportExporter.writeTo(context, Uri.fromFile(target), text)

        assertEquals(text.toByteArray().size.toLong(), bytes)
        assertEquals(text, target.readText())
    }

    @Test
    fun `relatorio preparado antes do seletor sobrevive ate a gravacao`() {
        ReportExporter.storePending(context, "conteúdo")
        assertEquals("conteúdo", ReportExporter.takePending(context))
        ReportExporter.clearPending(context)
        assertNull(ReportExporter.takePending(context))
    }

    @Test
    fun `tamanho legivel`() {
        assertEquals("512 bytes", ReportExporter.formatSize(512))
        assertEquals("30 KB", ReportExporter.formatSize(31_212)) // 31.212 bytes = 30,5 KiB
    }

    @Test
    fun `linha do tempo vai e volta do arquivo sem perder campos`() {
        val line = CarTestLog.Line("09:20:41.3", 12_345L, "Propriedade sys.usb.config: mtp → none\tcom tab\ne quebra")
        val restored = CarTestLog.decode(CarTestLog.encode(line))!!
        assertEquals("09:20:41.3", restored.clock)
        assertEquals(12_345L, restored.sinceStart)
        assertEquals("Propriedade sys.usb.config: mtp → none com tab e quebra", restored.text)

        val noOrigin = CarTestLog.decode(CarTestLog.encode(CarTestLog.Line("09:21:00.0", null, "Comando Próxima")))!!
        assertNull(noOrigin.sinceStart)
        assertNull(CarTestLog.decode("linha corrompida"))
    }

    @Test
    fun `detecta gravacao interrompida pelo sistema`() {
        val started = "${CarTestLog.STARTED} (até 180 s)"
        assertTrue(CarTestLog.wasInterrupted(listOf(started, "Música tocando: SIM")))
        assertFalse(CarTestLog.wasInterrupted(listOf(started, "${CarTestLog.ENDED} (tempo esgotado)")))
        assertFalse(CarTestLog.wasInterrupted(listOf(started, CarTestLog.INTERRUPTED)))
        assertFalse(CarTestLog.wasInterrupted(listOf("Comando Play/Pause via sistema: enviado")))
        // Uma gravação antiga encerrada não esconde uma nova que ficou pela metade.
        assertTrue(CarTestLog.wasInterrupted(listOf(started, "${CarTestLog.ENDED} (pelo usuário)", started)))
    }
}
