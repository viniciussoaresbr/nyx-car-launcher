package com.premiumcar.launcher.diagnostics.cartest

import android.net.TrafficStats
import java.io.File
import java.util.Locale

/**
 * Tráfego de rede durante a gravação dos testes no carro (0.4.5): só nomes de interface e
 * contadores de bytes, nada do conteúdo. Em busca do momento "CarPlay pronto": o vídeo do
 * CarPlay chega pelo cabo (ou pelo Wi-Fi, no sem fio), e o começo desse fluxo deve coincidir
 * com a imagem aparecendo (ARCHITECTURE.md, D20).
 *
 * Lê `/sys/class/net/<interface>/statistics`; se o firmware não deixar, usa os totais do
 * Android ([TrafficStats]), somando todas as interfaces.
 */
internal object NetTraffic {

    /** Nome usado quando só os totais do Android estão disponíveis. */
    const val TOTAL = "total"

    data class Bytes(val received: Long, val sent: Long)

    private val dir = File("/sys/class/net")

    /** Bytes recebidos e enviados por interface (sem a interna "lo"); vazio se nada puder ser lido. */
    fun read(): Map<String, Bytes> {
        val perInterface = dir.list().orEmpty()
            .filter { it != "lo" }
            .mapNotNull { name -> counters(name)?.let { name to it } }
            .toMap()
        if (perInterface.isNotEmpty()) return perInterface.toSortedMap()
        val received = TrafficStats.getTotalRxBytes()
        val sent = TrafficStats.getTotalTxBytes()
        return if (received < 0 || sent < 0) emptyMap() else mapOf(TOTAL to Bytes(received, sent))
    }

    private fun counters(name: String): Bytes? = runCatching {
        Bytes(
            File(dir, "$name/statistics/rx_bytes").readText().trim().toLong(),
            File(dir, "$name/statistics/tx_bytes").readText().trim().toLong(),
        )
    }.getOrNull()

    /** Faixas de tráfego por segundo: parado, baixo, controle e alto (vídeo). */
    fun level(bytesPerSecond: Long): Int = when {
        bytesPerSecond < 16 * 1024 -> 0
        bytesPerSecond < 128 * 1024 -> 1
        bytesPerSecond < 1024 * 1024 -> 2
        else -> 3
    }

    /**
     * O que mudou entre duas leituras feitas com [seconds] de intervalo: interfaces novas, que
     * sumiram e mudanças de faixa de tráfego. [levels] guarda a última faixa de cada interface.
     * Lógica pura, testada na JVM.
     */
    fun changes(
        previous: Map<String, Bytes>,
        current: Map<String, Bytes>,
        seconds: Double,
        levels: MutableMap<String, Int>,
    ): List<String> = buildList {
        (current.keys - previous.keys).sorted().forEach { add("Rede: interface nova $it") }
        (previous.keys - current.keys).sorted().forEach { name ->
            add("Rede: interface $name sumiu")
            levels.remove(name)
        }
        if (seconds <= 0.0) return@buildList
        current.toSortedMap().forEach { (name, now) ->
            val before = previous[name] ?: return@forEach
            val received = ((now.received - before.received).coerceAtLeast(0) / seconds).toLong()
            val sent = ((now.sent - before.sent).coerceAtLeast(0) / seconds).toLong()
            val level = level(received + sent)
            val old = levels[name] ?: 0
            if (level != old) {
                val direction = if (level > old) "subiu" else "caiu"
                add("Rede $name: recebendo ${rate(received)}, enviando ${rate(sent)} ($direction)")
                levels[name] = level
            }
        }
    }

    fun rate(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024 * 1024 -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB/s", bytesPerSecond / 1_048_576.0)
        else -> "${bytesPerSecond / 1024} KB/s"
    }
}
