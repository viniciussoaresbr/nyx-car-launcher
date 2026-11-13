package com.premiumcar.launcher.diagnostics.cartest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.premiumcar.launcher.diagnostics.InfoSection
import com.premiumcar.launcher.diagnostics.section
import com.premiumcar.launcher.system.Background
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Linha do tempo dos testes no carro (gravação e comandos de mídia). Vai para a aba
 * "Testes no carro" e para o relatório.
 *
 * Cada linha também é gravada num arquivo privado do app: se o Android encerrar o
 * Nyx Diagnóstico durante um teste (ex.: falta de memória com o CarPlay aberto), o que foi
 * registrado até ali volta na próxima abertura, com o aviso de que a gravação foi interrompida.
 */
object CarTestLog {

    private const val MAX_LINES = 600
    private const val FILE_NAME = "cartest-timeline.txt"
    internal const val STARTED = "Gravação iniciada"
    internal const val ENDED = "Gravação encerrada"
    internal const val INTERRUPTED =
        "Gravação interrompida: o Android encerrou o Nyx Diagnóstico antes do fim (provável falta de memória)"

    internal class Line(val clock: String, val sinceStart: Long?, val text: String)

    private val lines = ArrayList<Line>()
    private var dropped = 0

    /** Arquivo da linha do tempo; nulo até [attach] (ex.: testes que não precisam dele). */
    @Volatile
    private var file: File? = null

    /** Início da gravação atual (uptime), para mostrar "t+12,3 s". */
    @Volatile
    private var origin = 0L
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Chamado na thread principal a cada mudança (a interface se atualiza). */
    @Volatile
    var listener: (() -> Unit)? = null

    /**
     * Liga a linha do tempo ao arquivo do app. Na primeira vez em cada processo, recupera o
     * que o processo anterior registrou. Arquivo pequeno (até [MAX_LINES] linhas curtas).
     */
    @Synchronized
    fun attach(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        val restored = runCatching { if (target.exists()) target.readLines().mapNotNull(::decode) else emptyList() }
            .getOrDefault(emptyList())
        if (restored.isEmpty()) return
        synchronized(lines) {
            val kept = restored.takeLast(MAX_LINES)
            dropped += restored.size - kept.size
            lines.addAll(0, kept)
        }
        if (restored.size > MAX_LINES) rewrite()
        if (wasInterrupted(restored.map { it.text })) add(INTERRUPTED)
        changed()
    }

    fun markOrigin(uptime: Long) {
        origin = uptime
    }

    fun add(text: String) {
        val now = SystemClock.uptimeMillis()
        val line = Line(
            clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
            sinceStart = if (origin != 0L) now - origin else null,
            text = text,
        )
        synchronized(lines) {
            if (lines.size >= MAX_LINES) {
                lines.removeAt(0)
                dropped++
            }
            lines += line
        }
        file?.let { target -> Background.executor.execute { runCatching { target.appendText(encode(line) + "\n") } } }
        changed()
    }

    fun changed() {
        main.post { listener?.invoke() }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
            dropped = 0
        }
        file?.let { target -> Background.executor.execute { runCatching { target.delete() } } }
        changed()
    }

    fun section(): InfoSection = section("Linha do tempo dos testes") {
        val snapshot = synchronized(lines) { lines.toList() }
        if (snapshot.isEmpty()) add("Registros", "nenhum ainda: inicie uma gravação ou envie um comando")
        snapshot.forEach { line ->
            val label = line.sinceStart?.let { "${line.clock} (t+${formatSeconds(it)})" } ?: line.clock
            add(label, line.text)
        }
        if (dropped > 0) note = "$dropped registros mais antigos foram descartados (limite de $MAX_LINES)."
    }

    /** Regrava o arquivo só com as linhas mantidas em memória. */
    private fun rewrite() {
        val target = file ?: return
        val snapshot = synchronized(lines) { lines.toList() }
        Background.executor.execute {
            runCatching { target.writeText(snapshot.joinToString("") { encode(it) + "\n" }) }
        }
    }

    internal fun formatSeconds(millis: Long): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", millis / 1000.0)

    /** Uma linha por registro: horário, tempo desde o início (ou vazio) e texto, separados por tabulação. */
    internal fun encode(line: Line): String =
        listOf(line.clock, line.sinceStart?.toString().orEmpty(), line.text.replace('\n', ' ').replace('\t', ' '))
            .joinToString("\t")

    internal fun decode(raw: String): Line? {
        val parts = raw.split('\t', limit = 3)
        if (parts.size != 3) return null
        return Line(parts[0], parts[1].toLongOrNull(), parts[2])
    }

    /** A última gravação começou e não terminou: o processo anterior morreu no meio dela. */
    internal fun wasInterrupted(texts: List<String>): Boolean {
        val lastStart = texts.indexOfLast { it.startsWith(STARTED) }
        if (lastStart < 0) return false
        return texts.subList(lastStart, texts.size).none { it.startsWith(ENDED) || it.startsWith(INTERRUPTED) }
    }
}
