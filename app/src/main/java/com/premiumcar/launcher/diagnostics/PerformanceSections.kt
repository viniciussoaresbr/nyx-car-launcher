package com.premiumcar.launcher.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import com.premiumcar.launcher.perf.BenchmarkResult
import com.premiumcar.launcher.perf.HomeLaunch
import com.premiumcar.launcher.perf.HomeMetrics
import com.premiumcar.launcher.perf.MemorySnapshot
import com.premiumcar.launcher.perf.StartupTimeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Seções da aba Desempenho (também usadas no relatório exportado). */
object PerformanceSections {

    fun build(context: Context, memory: MemorySnapshot?, results: List<BenchmarkResult>): List<InfoSection> = buildList {
        add(homeLaunches(HomeMetrics.load(context)))
        add(startup(context))
        if (memory != null) add(memorySection(memory))
        if (results.isNotEmpty()) {
            add(summary(results))
            results.forEachIndexed { index, run -> add(runSection(index + 1, run)) }
        }
    }

    /** Aberturas a frio da Home (Views), registradas pela própria Home. */
    internal fun homeLaunches(launches: List<HomeLaunch>) = section("Home em Views: aberturas a frio") {
        if (launches.isEmpty()) {
            add("Registros", "nenhum ainda")
            note = "Feche o Nyx Car pela tela de Recentes e abra de novo: cada abertura a frio fica registrada aqui."
            return@section
        }
        val (reopened, cold) = launches.partition { it.processAlreadyOpen }
        if (cold.isNotEmpty()) {
            highlight(
                "Mediana (${cold.size} aberturas)",
                "${median(cold.map { it.startupMs.toDouble() }).roundToLong()} ms · PSS ${Fmt.kb(median(cold.map { it.pssKb.toDouble() }).roundToInt())}",
            )
        }
        if (reopened.isNotEmpty()) {
            add(
                "Mediana com o processo já aberto (${reopened.size})",
                "${median(reopened.map { it.startupMs.toDouble() }).roundToLong()} ms · PSS ${Fmt.kb(median(reopened.map { it.pssKb.toDouble() }).roundToInt())}",
            )
        }
        val format = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR"))
        launches.asReversed().forEach { launch ->
            add(
                format.format(Date(launch.timestampMillis)),
                "${launch.startupMs} ms · PSS ${Fmt.kb(launch.pssKb)} " +
                    "(Java ${Fmt.kb(launch.javaHeapKb)} · nativo ${Fmt.kb(launch.nativeHeapKb)})" +
                    if (launch.processAlreadyOpen) " · processo já aberto" else "",
            )
        }
        note = "Tempo do início do processo até o primeiro quadro; memória medida 3 s depois. " +
            "\"Processo já aberto\": o Android tinha reaberto o app em segundo plano (por exemplo, depois de " +
            "a central fechá-lo ao desligar o carro); o tempo conta da criação da tela. " +
            "A primeira abertura após instalar é mais lenta."
    }

    private fun startup(context: Context) = section("Inicialização deste processo") {
        add("Primeira tela aberta", StartupTimeline.firstScreen)
        val start = StartupTimeline.processStart
        if (start == null) {
            warn("Início do processo", "indisponível (requer Android 7 ou superior)")
        } else {
            add("Processo → Application.onCreate", "${StartupTimeline.applicationCreated - start} ms")
            add("Processo → Activity.onCreate", "${StartupTimeline.activityCreated - start} ms")
            highlight("Processo → primeiro quadro", StartupTimeline.startupMillis?.let { "$it ms" })
            if (StartupTimeline.startedWithoutScreen) {
                add("Processo aberto antes da tela", "sim: o Android reabriu o app em segundo plano")
                add("Tela → primeiro quadro", StartupTimeline.screenToFirstFrameMillis?.let { "$it ms" })
            }
        }
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            warn("Tipo de build", "DEPURÁVEL — números de desempenho não representativos")
        } else {
            add("Tipo de build", "release otimizado (R8)")
        }
        note = "A primeira abertura após instalar é mais lenta (código ainda não compilado). " +
            "Feche o app pela tela de Recentes e abra novamente 2 ou 3 vezes para obter números estáveis."
    }

    private fun memorySection(snapshot: MemorySnapshot) = section("Memória deste processo (agora)") {
        highlight("Total (PSS)", Fmt.kb(snapshot.totalPssKb))
        add("Heap Java", Fmt.kb(snapshot.javaHeapKb))
        add("Heap nativo", Fmt.kb(snapshot.nativeHeapKb))
        add("Gráficos", Fmt.kb(snapshot.graphicsKb))
    }

    private fun summary(results: List<BenchmarkResult>) = section("Comparativo (medianas)") {
        results.groupBy { it.engine }.forEach { (engine, runs) ->
            val coldStarts = runs.filter { it.coldStart && it.startupMs >= 0 }.map { it.startupMs.toDouble() }
            val startup = if (coldStarts.isEmpty()) "—" else "${median(coldStarts).roundToLong()} ms"
            highlight(
                engine,
                "${runs.size} execução(ões) · início $startup · " +
                    "${Fmt.decimal(median(runs.map { it.stats.averageFps }))} fps · " +
                    "atrasados ${Fmt.decimal(median(runs.map { it.stats.jankPercent }))}% · " +
                    "p90 ${Fmt.decimal(median(runs.map { it.stats.p90Ms }))} ms · " +
                    "PSS ${Fmt.kb(median(runs.map { it.memory.totalPssKb.toDouble() }).roundToInt())}",
            )
        }
        note = "Compare os dois motores com o mesmo número de execuções. A primeira execução após instalar tende a ser mais lenta."
    }

    private fun runSection(index: Int, r: BenchmarkResult) = section("Execução #$index · ${r.engine}") {
        add("Partida", if (r.coldStart) "a frio (processo novo)" else "processo reaproveitado")
        highlight("Processo → primeiro quadro", if (r.startupMs >= 0) "${r.startupMs} ms" else "indisponível")
        add("Ícones carregados", "${r.distinctIcons} em ${r.iconLoadMs} ms")
        val s = r.stats
        highlight(
            "Quadros",
            "${s.frames} em ${Fmt.decimal(s.durationMs / 1000.0)} s · ${Fmt.decimal(s.averageFps)} fps " +
                "(tela de ${Fmt.decimal(s.refreshRateHz, 0)} Hz)",
        )
        highlight("Quadros atrasados", "${s.jankyFrames} (${Fmt.decimal(s.jankPercent)}%)")
        add(
            "Intervalo p50 / p90 / p99 / máx.",
            "${Fmt.decimal(s.p50Ms)} / ${Fmt.decimal(s.p90Ms)} / ${Fmt.decimal(s.p99Ms)} / ${Fmt.decimal(s.maxMs)} ms",
        )
        val m = r.memory
        add(
            "Memória ao final (PSS)",
            "${Fmt.kb(m.totalPssKb)} (Java ${Fmt.kb(m.javaHeapKb)} · nativo ${Fmt.kb(m.nativeHeapKb)} · gráficos ${Fmt.kb(m.graphicsKb)})",
        )
    }

    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
