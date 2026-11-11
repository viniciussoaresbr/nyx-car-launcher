package com.premiumcar.launcher.perf

import com.premiumcar.launcher.diagnostics.EntryLevel
import com.premiumcar.launcher.diagnostics.PerformanceSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetricsTest {

    private val sample = listOf(
        HomeLaunch(1_000L, 420L, 14_000, 2_000, 6_000),
        HomeLaunch(2_000L, 380L, 15_000, 2_100, 6_100),
    )

    @Test
    fun `registros sobrevivem a ida e volta pelo texto salvo`() {
        assertEquals(sample, HomeMetrics.decode(HomeMetrics.encode(sample)))
    }

    @Test
    fun `linhas corrompidas sao ignoradas`() {
        val text = HomeMetrics.encode(sample) + "\nlixo;1;2\n;;;;\n"
        assertEquals(sample, HomeMetrics.decode(text))
        assertEquals(emptyList<HomeLaunch>(), HomeMetrics.decode(""))
    }

    @Test
    fun `secao do diagnostico mostra a mediana das aberturas`() {
        val section = PerformanceSections.homeLaunches(sample + HomeLaunch(3_000L, 500L, 16_000, 2_200, 6_200))
        val summary = section.entries.first()
        assertEquals(EntryLevel.HIGHLIGHT, summary.level)
        assertTrue(summary.value, summary.value.startsWith("420 ms"))
        assertEquals(1 + 3, section.entries.size)
    }

    @Test
    fun `linha antiga com cinco campos continua valida`() {
        assertEquals(listOf(HomeLaunch(1L, 2L, 3, 4, 5)), HomeMetrics.decode("1;2;3;4;5"))
    }

    @Test
    fun `processo reaberto em segundo plano fica fora da mediana das aberturas`() {
        val reopened = HomeLaunch(3_000L, 9_000L, 13_000, 1_500, 5_000, processAlreadyOpen = true)
        assertEquals(sample + reopened, HomeMetrics.decode(HomeMetrics.encode(sample + reopened)))
        val section = PerformanceSections.homeLaunches(sample + reopened)
        assertEquals("Mediana (2 aberturas)", section.entries[0].label)
        assertTrue(section.entries[0].value, section.entries[0].value.startsWith("400 ms"))
        assertEquals("Mediana com o processo já aberto (1)", section.entries[1].label)
        assertTrue(section.entries[1].value, section.entries[1].value.startsWith("9000 ms"))
        assertEquals(2 + 3, section.entries.size)
        assertTrue(section.entries[2].value.endsWith("processo já aberto"))
    }

    @Test
    fun `secao vazia explica como registrar`() {
        val section = PerformanceSections.homeLaunches(emptyList())
        assertEquals("nenhum ainda", section.entries.single().value)
    }
}
