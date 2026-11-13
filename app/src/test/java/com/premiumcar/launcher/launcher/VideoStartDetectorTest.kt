package com.premiumcar.launcher.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Começo do vídeo do CarPlay pelo tráfego (0.4.7), com os números das gravações de 12/11/2026. */
class VideoStartDetectorTest {

    private val cable = VideoStartDetector(thresholdBytesPerSecond = 512L * 1024)

    /** Alimenta [seconds] de tráfego a [bytesPerSecond], em leituras a cada 250 ms. */
    private fun VideoStartDetector.stream(start: Long, from: Long, bytesPerSecond: Long, seconds: Double): Pair<Boolean, Long> {
        var bytes = from
        var detected = false
        var time = start
        repeat((seconds * 4).toInt()) {
            time += 250
            bytes += bytesPerSecond / 4
            if (add(time, bytes)) detected = true
        }
        return detected to bytes
    }

    @Test
    fun `controle antes do video nao conta, o video de 1,2 MB por s conta`() {
        cable.add(0, 0)
        val (beforeVideo, bytes) = cable.stream(0, 0, 294L * 1024, 1.0) // visto 0,3 s depois do Bluetooth
        assertFalse(beforeVideo)
        val (video, _) = cable.stream(1_000, bytes, 1_200L * 1024, 0.75)
        assertTrue(video)
    }

    @Test
    fun `sem fio basta um limite menor`() {
        val wireless = VideoStartDetector(thresholdBytesPerSecond = 160L * 1024)
        wireless.add(0, 0)
        assertFalse(wireless.stream(0, 0, 8L * 1024, 1.0).first)
        assertTrue(wireless.stream(1_000, 8L * 1024, 247L * 1024, 0.75).first)
    }

    @Test
    fun `um pico isolado curto demais nao conta`() {
        cable.add(0, 0)
        assertFalse("Uma leitura só, 100 ms depois", cable.add(100, 300L * 1024))
    }

    @Test
    fun `contador indisponivel ou zerado nao gera deteccao`() {
        assertFalse(cable.add(0, -1))
        cable.add(250, 10_000_000)
        assertFalse("Contador voltou para trás", cable.add(500, 100))
        assertFalse(cable.add(750, 200))
    }
}
