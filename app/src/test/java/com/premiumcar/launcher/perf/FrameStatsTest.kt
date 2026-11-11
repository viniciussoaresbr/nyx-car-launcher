package com.premiumcar.launcher.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameStatsTest {

    private val period60Hz = 16_666_667L

    private fun steadyFrames(count: Int, period: Long = period60Hz) = LongArray(count) { it * period }

    @Test
    fun `quadros regulares a 60 Hz nao tem atraso`() {
        val stats = FrameStats.from(steadyFrames(61), 60f)
        assertEquals(60, stats.frames)
        assertEquals(0, stats.jankyFrames)
        assertEquals(60.0, stats.averageFps, 0.1)
        assertEquals(16.67, stats.p50Ms, 0.01)
        assertEquals(1000L, stats.durationMs)
    }

    @Test
    fun `um intervalo longo conta como quadro atrasado`() {
        val times = steadyFrames(10).toMutableList()
        times += times.last() + 50_000_000L // 50 ms: três vsyncs perdidos
        val stats = FrameStats.from(times.toLongArray(), 60f)
        assertEquals(10, stats.frames)
        assertEquals(1, stats.jankyFrames)
        assertEquals(10.0, stats.jankPercent, 0.001)
        assertEquals(50.0, stats.maxMs, 0.001)
    }

    @Test
    fun `menos de dois quadros gera estatistica vazia`() {
        val stats = FrameStats.from(longArrayOf(123L), 60f)
        assertEquals(0, stats.frames)
        assertEquals(0.0, stats.averageFps, 0.0)
    }

    @Test
    fun `taxa de atualizacao invalida assume 60 Hz`() {
        assertEquals(60.0, FrameStats.from(steadyFrames(5), 0f).refreshRateHz, 0.0)
    }

    @Test
    fun `percentil pelo posto mais proximo`() {
        val values = (1..10).map { it.toDouble() }
        assertEquals(5.0, FrameStats.percentile(values, 50.0), 0.0)
        assertEquals(9.0, FrameStats.percentile(values, 90.0), 0.0)
        assertEquals(10.0, FrameStats.percentile(values, 99.0), 0.0)
    }
}
