package com.premiumcar.launcher.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notificações do ZLINK (0.4.13): só a forma, nunca o texto. */
class ZlinkNotificationsTest {

    @Test
    fun `o resumo muda quando o texto muda e nao mostra o texto`() {
        val connected = ZlinkNotifications.hash(listOf("ZLINK", "Conectado ao iPhone de Fulano", null))
        val waiting = ZlinkNotifications.hash(listOf("ZLINK", "Aguardando conexão", null))
        assertNotEquals(connected, waiting)
        assertEquals(8, connected.length)
        assertFalse(connected.contains("Fulano"))
        assertEquals("Mesmo texto, mesmo resumo", connected, ZlinkNotifications.hash(listOf("ZLINK", "Conectado ao iPhone de Fulano", null)))
        assertEquals("vazio", ZlinkNotifications.hash(listOf(null, "", null)))
    }

    @Test
    fun `descricao so com a forma`() {
        val shape = ZlinkNotifications.Shape(id = 7, ongoing = true, channel = "zlink", category = "service", actions = 1, textHash = "1a2b3c4d")
        val text = shape.describe()
        assertEquals("nº 7, contínua, canal zlink, categoria service, 1 ação, texto 1a2b3c4d", text)
        assertTrue(ZlinkNotifications.Shape(1, false, null, null, 0, "vazio").describe().startsWith("nº 1, comum, 0 ações"))
    }
}
