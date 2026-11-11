package com.premiumcar.launcher.perf

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Uma abertura a frio da Home: tempo até o primeiro quadro e memória logo depois.
 * [processAlreadyOpen]: o Android tinha reaberto o processo em segundo plano antes da tela;
 * nesse caso, [startupMs] conta da criação da tela (o início do processo foi bem antes).
 */
data class HomeLaunch(
    val timestampMillis: Long,
    val startupMs: Long,
    val pssKb: Int,
    val javaHeapKb: Int,
    val nativeHeapKb: Int,
    val processAlreadyOpen: Boolean = false,
)

/**
 * Registra o custo real da Home na central, sem ADB: a cada abertura a frio, guarda
 * tempo de abertura e memória. O painel de Diagnóstico exibe e exporta esses dados.
 */
object HomeMetrics {
    private const val PREFS = "home_metrics"

    /**
     * Chave nova na 0.4.1: os registros da 0.4.0 misturavam processos reabertos em segundo plano
     * (16 a 24 s "de abertura") com aberturas reais e foram descartados.
     */
    private const val KEY = "launches_v2"
    const val MAX_ENTRIES = 10

    /** Espera a tela assentar (fontes, ícones, primeiro minuto do relógio) antes de medir a memória. */
    private const val MEMORY_DELAY_MS = 3_000L

    /** Agenda a medição. Chame depois do primeiro quadro de uma abertura a frio. */
    fun scheduleCapture(context: Context, startupMs: Long, processAlreadyOpen: Boolean = false) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                val memory = MemorySnapshot.capture()
                record(
                    app,
                    HomeLaunch(
                        System.currentTimeMillis(), startupMs, memory.totalPssKb, memory.javaHeapKb, memory.nativeHeapKb,
                        processAlreadyOpen,
                    ),
                )
            }.start()
        }, MEMORY_DELAY_MS)
    }

    fun load(context: Context): List<HomeLaunch> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty())

    @Synchronized
    fun record(context: Context, launch: HomeLaunch) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (decode(prefs.getString(KEY, null).orEmpty()) + launch).takeLast(MAX_ENTRIES)
        prefs.edit().putString(KEY, encode(updated)).apply()
    }

    /** Uma linha por abertura: `timestamp;startup;pss;java;native;processoJáAberto(0/1)`. */
    internal fun encode(launches: List<HomeLaunch>): String = launches.joinToString("\n") {
        "${it.timestampMillis};${it.startupMs};${it.pssKb};${it.javaHeapKb};${it.nativeHeapKb};${if (it.processAlreadyOpen) 1 else 0}"
    }

    internal fun decode(text: String): List<HomeLaunch> = text.lineSequence().mapNotNull { line ->
        val parts = line.split(';').map { it.trim().toLongOrNull() }
        if (parts.size !in 5..6 || parts.any { it == null }) return@mapNotNull null
        HomeLaunch(parts[0]!!, parts[1]!!, parts[2]!!.toInt(), parts[3]!!.toInt(), parts[4]!!.toInt(), parts.getOrNull(5) == 1L)
    }.toList()
}
