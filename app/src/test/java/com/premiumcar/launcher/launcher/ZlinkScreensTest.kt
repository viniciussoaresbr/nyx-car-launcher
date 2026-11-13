package com.premiumcar.launcher.launcher

import com.premiumcar.launcher.launcher.ZlinkScreens.Result.END
import com.premiumcar.launcher.launcher.ZlinkScreens.Result.MAYBE_END
import com.premiumcar.launcher.launcher.ZlinkScreens.Result.NONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telas do ZLINK pelo serviço de acessibilidade (0.4.11): aprendizado e fim do CarPlay. */
class ZlinkScreensTest {

    private val screens = ZlinkScreens()
    private var now = 0L

    private fun at(millis: Long) = apply { now = millis }

    /** Conexão comum: espera do ZLINK com o Bluetooth ligado, Bluetooth desliga, CarPlay na tela. */
    private fun connect() {
        assertEquals(NONE, screens.update(WAIT, at(0).now, ZlinkScreens.Mode.NO_CARPLAY))
        screens.carPlayStarted(at(6_000).now)
        assertEquals(NONE, screens.update(PROJECTION, at(7_000).now, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `tela vista com o Bluetooth ligado e de espera`() {
        screens.update(WAIT, 0, ZlinkScreens.Mode.NO_CARPLAY)
        assertEquals(ZlinkScreens.Kind.WAIT, screens.kind(WAIT))
        assertTrue(screens.changed)
        screens.saved()
        assertFalse(screens.changed)
    }

    @Test
    fun `o ZLINK voltando para a espera durante o CarPlay e o fim`() {
        connect()
        assertEquals(END, screens.update(WAIT, at(30_000).now, ZlinkScreens.Mode.CARPLAY))
        assertEquals(ZlinkScreens.Kind.CARPLAY, screens.kind(PROJECTION))
    }

    @Test
    fun `a espera ainda na tela logo depois de o Bluetooth desligar nao e fim`() {
        screens.update(WAIT, 0, ZlinkScreens.Mode.NO_CARPLAY)
        screens.carPlayStarted(6_000)
        assertEquals(NONE, screens.update(OTHER, 6_200, ZlinkScreens.Mode.CARPLAY))
        assertEquals("A tela do CarPlay não ficou 1,5 s", NONE, screens.update(WAIT, 6_500, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `telas intermediarias no fim nao atrapalham`() {
        connect()
        assertEquals(NONE, screens.update(OTHER, at(30_000).now, ZlinkScreens.Mode.CARPLAY))
        assertEquals(END, screens.update(WAIT, at(30_100).now, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `tela de espera nunca vista nao e fim`() {
        connect()
        assertEquals(NONE, screens.update(OTHER, at(30_000).now, ZlinkScreens.Mode.CARPLAY))
        assertEquals(NONE, screens.update("nunca-vista", at(32_000).now, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `um fim por sessao`() {
        connect()
        assertEquals(END, screens.update(WAIT, at(30_000).now, ZlinkScreens.Mode.CARPLAY))
        assertEquals(NONE, screens.update(OTHER, at(30_200).now, ZlinkScreens.Mode.CARPLAY))
        assertEquals(NONE, screens.update(WAIT, at(30_400).now, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `a imagem do CarPlay ainda na tela no fim nunca vira espera`() {
        connect()
        screens.sessionEnded(at(40_000).now) // o Bluetooth religou sem o ZLINK trocar de tela antes
        assertEquals(ZlinkScreens.Kind.CARPLAY, screens.kind(PROJECTION))
        screens.update(WAIT, at(41_000).now, ZlinkScreens.Mode.NO_CARPLAY)
        assertEquals("Pausa depois do fim", NONE, screens.update(PROJECTION, at(41_500).now, ZlinkScreens.Mode.NO_CARPLAY))
        assertEquals(ZlinkScreens.Kind.CARPLAY, screens.kind(PROJECTION))
        screens.update(PROJECTION, at(50_000).now, ZlinkScreens.Mode.NO_CARPLAY)
        assertEquals("Tela do CarPlay nunca vira espera", ZlinkScreens.Kind.CARPLAY, screens.kind(PROJECTION))
    }

    @Test
    fun `com o Bluetooth desligado sem sessao nada e aprendido nem avisado`() {
        screens.update(WAIT, 0, ZlinkScreens.Mode.NO_CARPLAY)
        screens.update(PROJECTION, 7_000, ZlinkScreens.Mode.UNSURE) // CarPlay começou sem o Nyx Car registrar
        assertEquals(NONE, screens.update(WAIT, 30_000, ZlinkScreens.Mode.UNSURE))
        assertEquals("A tela do CarPlay não vira espera", ZlinkScreens.Kind.UNKNOWN, screens.kind(PROJECTION))
    }

    // ---------- Imagem do CarPlay (0.4.12) ----------

    /** Como no teste de 12/11: a tela do CarPlay tem a imagem; as de espera, não. */
    private fun connectWithPicture() {
        screens.update(WAIT, at(0).now, ZlinkScreens.Mode.NO_CARPLAY)
        screens.update(STARTING, at(6_000).now, ZlinkScreens.Mode.NO_CARPLAY, projection = true) // logo antes do Bluetooth
        screens.carPlayStarted(at(6_020).now)
        assertEquals(NONE, screens.update(PROJECTION, at(7_800).now, ZlinkScreens.Mode.CARPLAY, projection = true))
    }

    @Test
    fun `a imagem do CarPlay sumindo numa tela nunca vista e fim provavel ja na primeira vez`() {
        connectWithPicture()
        assertEquals(MAYBE_END, screens.update("espera-do-fim", at(30_000).now, ZlinkScreens.Mode.CARPLAY))
        assertTrue(screens.confirmEnd("espera-do-fim"))
        assertEquals("Aprendida como espera", ZlinkScreens.Kind.WAIT, screens.kind("espera-do-fim"))
        assertFalse("Um fim por sessão", screens.confirmEnd("espera-do-fim"))
    }

    @Test
    fun `piscadas da tela do CarPlay com a imagem nao sao fim`() {
        connectWithPicture()
        assertEquals(NONE, screens.update("piscada", at(20_000).now, ZlinkScreens.Mode.CARPLAY, projection = true))
        assertEquals(NONE, screens.update(PROJECTION, at(20_300).now, ZlinkScreens.Mode.CARPLAY, projection = true))
    }

    @Test
    fun `a imagem do CarPlay nunca e aprendida como espera`() {
        screens.update(STARTING, 0, ZlinkScreens.Mode.NO_CARPLAY, projection = true)
        assertEquals(ZlinkScreens.Kind.UNKNOWN, screens.kind(STARTING))
    }

    @Test
    fun `tela sem a imagem parada antes do fim nao vira tela do CarPlay`() {
        connectWithPicture()
        screens.update("sem-imagem", at(30_000).now, ZlinkScreens.Mode.CARPLAY)
        screens.sessionEnded(at(40_000).now) // ficou 10 s sem a imagem, o fim veio pelo Bluetooth
        assertEquals(ZlinkScreens.Kind.UNKNOWN, screens.kind("sem-imagem"))
    }

    @Test
    fun `aprendido continua valendo depois de reabrir o app`() {
        connect()
        val reopened = ZlinkScreens(screens.waits, screens.carPlays)
        reopened.update(WAIT, 0, ZlinkScreens.Mode.NO_CARPLAY)
        reopened.carPlayStarted(6_000)
        reopened.update(PROJECTION, 7_000, ZlinkScreens.Mode.CARPLAY)
        assertEquals(END, reopened.update(WAIT, 20_000, ZlinkScreens.Mode.CARPLAY))
    }

    @Test
    fun `guarda no maximo 40 telas de cada tipo`() {
        repeat(50) { screens.update("tela$it", it * 10L, ZlinkScreens.Mode.NO_CARPLAY) }
        assertEquals(ZlinkScreens.MAX_SCREENS, screens.waits.size)
        assertTrue("As mais recentes ficam", "tela49" in screens.waits)
    }

    @Test
    fun `resumo da tela nao depende da ordem nem do texto escrito`() {
        val a = ZlinkScreen.of(
            listOf(
                ZlinkScreen.Element("TextView", "tv_tip", hasText = true, touchable = false),
                ZlinkScreen.Element("ImageView", "iv_back", hasText = false, touchable = true),
            ),
        )
        val b = ZlinkScreen.of(
            listOf(
                ZlinkScreen.Element("ImageView", "iv_back", hasText = false, touchable = true),
                ZlinkScreen.Element("TextView", "tv_tip", hasText = true, touchable = false),
            ),
        )
        assertEquals(a, b)
        assertEquals(8, a.signature.length)
        assertEquals(listOf("iv_back", "tv_tip"), a.ids)
        val empty = ZlinkScreen.of(listOf(ZlinkScreen.Element("TextView", "tv_tip", hasText = false, touchable = false)))
        assertNotEquals("Texto vazio muda a estrutura", a.signature, empty.signature)
    }

    private companion object {
        const val WAIT = "espera"
        const val PROJECTION = "carplay"
        const val OTHER = "intermediaria"
        const val STARTING = "carplay-comecando"
    }
}
