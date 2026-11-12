package com.premiumcar.launcher.diagnostics.cartest

import com.premiumcar.launcher.diagnostics.cartest.NetTraffic.Bytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tráfego de rede na gravação (0.4.5): faixas, interfaces novas e que somem. */
class NetTrafficTest {

    private val levels = mutableMapOf<String, Int>()

    @Test
    fun `faixas de trafego`() {
        assertEquals(0, NetTraffic.level(10 * 1024))
        assertEquals(1, NetTraffic.level(100 * 1024))
        assertEquals(2, NetTraffic.level(600 * 1024))
        assertEquals(3, NetTraffic.level(2 * 1024 * 1024))
        assertEquals("2,0 MB/s", NetTraffic.rate(2L * 1024 * 1024))
        assertEquals("600 KB/s", NetTraffic.rate(600L * 1024))
    }

    @Test
    fun `interface nova e video comecando`() {
        val before = mapOf("wlan0" to Bytes(1_000, 1_000))
        val after = mapOf("wlan0" to Bytes(1_000, 1_000), "ncm0" to Bytes(0, 0))
        assertEquals(listOf("Rede: interface nova ncm0"), NetTraffic.changes(before, after, 1.0, levels))

        val video = mapOf("wlan0" to Bytes(1_000, 1_000), "ncm0" to Bytes(600L * 1024, 20L * 1024))
        assertEquals(
            listOf("Rede ncm0: recebendo 600 KB/s, enviando 20 KB/s (subiu)"),
            NetTraffic.changes(after, video, 1.0, levels),
        )
        // Variação dentro da mesma faixa não gera linha.
        val more = mapOf("wlan0" to Bytes(1_000, 1_000), "ncm0" to Bytes(1_300L * 1024, 40L * 1024))
        assertTrue(NetTraffic.changes(video, more, 1.0, levels).isEmpty())
    }

    @Test
    fun `trafego parando e interface sumindo`() {
        levels["ncm0"] = 2
        val before = mapOf("ncm0" to Bytes(5_000_000, 100_000))
        val still = mapOf("ncm0" to Bytes(5_000_000, 100_000))
        assertEquals(
            listOf("Rede ncm0: recebendo 0 KB/s, enviando 0 KB/s (caiu)"),
            NetTraffic.changes(before, still, 2.0, levels),
        )
        assertEquals(listOf("Rede: interface ncm0 sumiu"), NetTraffic.changes(still, emptyMap(), 1.0, levels))
        assertTrue(levels.isEmpty())
    }

    @Test
    fun `contador zerado ou intervalo invalido nao gera taxa negativa`() {
        val before = mapOf("ncm0" to Bytes(5_000_000, 100_000))
        val reset = mapOf("ncm0" to Bytes(10, 10))
        assertTrue(NetTraffic.changes(before, reset, 1.0, levels).isEmpty())
        assertTrue(NetTraffic.changes(before, reset, 0.0, levels).isEmpty())
    }
}
