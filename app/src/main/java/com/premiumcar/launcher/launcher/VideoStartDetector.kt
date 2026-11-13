package com.premiumcar.launcher.launcher

/**
 * Começo do vídeo do CarPlay pelo tráfego de rede total (B1, 0.4.7). Nas gravações de 12/11/2026,
 * o vídeo leva o tráfego recebido de quase zero para ~1,2 MB/s pelo cabo e para ~250 a 430 KB/s
 * sem fio, ~1,3 s depois de a central desligar o Bluetooth e logo antes de a imagem aparecer
 * (HARDWARE.md, seção 5). Lógica pura, testada na JVM.
 *
 * Recebe leituras a cada ~250 ms e compara o que chegou na última janela de [WINDOW_MS] com o
 * limite, em bytes por segundo.
 */
class VideoStartDetector(private val thresholdBytesPerSecond: Long) {

    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** Nova leitura (relógio em ms, total recebido em bytes). Devolve `true` quando o vídeo começou. */
    fun add(timeMillis: Long, receivedBytes: Long): Boolean {
        if (receivedBytes < 0) return false // contador indisponível
        val last = samples.lastOrNull()
        // Contador zerado ou relógio fora de ordem: recomeça a janela.
        if (last != null && (receivedBytes < last.second || timeMillis <= last.first)) samples.clear()
        samples.addLast(timeMillis to receivedBytes)
        while (samples.size > 2 && timeMillis - samples[1].first >= WINDOW_MS) samples.removeFirst()
        val (firstTime, firstBytes) = samples.first()
        val span = timeMillis - firstTime
        if (span < MIN_SPAN_MS) return false
        return (receivedBytes - firstBytes) * 1000 / span >= thresholdBytesPerSecond
    }

    companion object {
        const val WINDOW_MS = 500L
        private const val MIN_SPAN_MS = 200L
    }
}
