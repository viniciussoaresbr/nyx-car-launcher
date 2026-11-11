package com.premiumcar.launcher.player

import android.content.Context
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.system.CrashRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diário do app num arquivo privado, para o Nyx Diagnóstico (a central não tem ADB): início do
 * processo, comandos do player, foco de áudio, erros de formato, pendrive inserido ou removido,
 * tela acesa, desligamento e, desde a 0.4.1, erros que fecham o app ([CrashRecorder]).
 * Sem nomes de músicas: só "faixa 3 de 12 (.mp3)".
 */
object PlayerLog {

    private const val FILE_NAME = "player-events.txt"
    private const val MAX_LINES = 300
    private const val TRIM_EVERY = 50

    @Volatile
    private var file: File? = null
    private var appended = 0

    fun attach(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        Background.executor.execute { trim() }
    }

    fun add(text: String) {
        val target = file ?: return
        val line = format(text)
        Background.executor.execute {
            runCatching { target.appendText(line + "\n") }
            if (++appended % TRIM_EVERY == 0) trim()
        }
    }

    /** Grava já, nesta thread: para quando o processo vai terminar (erro que fecha o app). */
    fun addNow(context: Context, text: String) {
        File(context.applicationContext.filesDir, FILE_NAME).appendText(format(text) + "\n")
    }

    private fun format(text: String): String =
        "${SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())}\t${text.replace('\n', ' ').replace('\t', ' ')}"

    /** Últimos [limit] registros, do mais antigo ao mais novo, como (horário, texto). */
    fun read(context: Context, limit: Int = 80): List<Pair<String, String>> =
        runCatching {
            File(context.applicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readLines().orEmpty()
        }.getOrDefault(emptyList())
            .takeLast(limit)
            .map { line -> line.substringBefore('\t') to line.substringAfter('\t', "") }

    /** Mantém só os registros mais recentes. */
    private fun trim() {
        val target = file ?: return
        runCatching {
            if (!target.exists()) return
            val lines = target.readLines()
            if (lines.size > MAX_LINES) target.writeText(lines.takeLast(MAX_LINES).joinToString("") { it + "\n" })
        }
    }

    internal fun resetForTests() {
        file = null
        appended = 0
    }
}
