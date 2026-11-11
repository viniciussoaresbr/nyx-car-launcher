package com.premiumcar.launcher.system

import android.content.Context
import com.premiumcar.launcher.player.PlayerLog

/**
 * Registra no diário do app (o mesmo do player) os erros que fecham o Nyx Car (etapa
 * 0.4.1). A central não tem ADB: é a única forma de saber se um reinício do app foi erro
 * nosso ou a central fechando o app.
 *
 * Privacidade: a mensagem do erro pode trazer o caminho de um arquivo do pendrive (nome de
 * música); caminhos são trocados por "<arquivo>". Só entram o tipo do erro, a mensagem limpa
 * e onde ele aconteceu no código.
 */
object CrashRecorder {

    private const val MAX_MESSAGE = 140
    private const val OWN_FRAMES = 3
    private const val OWN_PACKAGE = "com.premiumcar."

    /**
     * Caminhos de armazenamento (pendrive, cartão, memória compartilhada), também codificados em
     * URI (`%2F`). Nomes de arquivo podem ter espaços: o caminho vai até ":" ou aspas (que o FAT
     * do pendrive não aceita em nomes), mesmo que leve junto o resto da mensagem.
     */
    private val PATH = Regex("""(/|%2F)(storage|mnt|sdcard|data/media)[^:'"]*""", RegexOption.IGNORE_CASE)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Grava na hora: o processo termina logo depois.
            runCatching { PlayerLog.addNow(app, describe(thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun describe(threadName: String, error: Throwable): String = buildString {
        append("Erro do app (o Android fecha o Nyx Car): ")
        append(summary(error))
        val frames = error.stackTrace.filter { it.className.startsWith(OWN_PACKAGE) }.take(OWN_FRAMES)
            .ifEmpty { error.stackTrace.take(2) }
        if (frames.isNotEmpty()) append(" · em ").append(frames.joinToString(" ← ", transform = ::frame))
        val root = generateSequence(error.cause) { it.cause }.lastOrNull()
        if (root != null) append(" · causa: ").append(summary(root))
        if (threadName != "main") append(" · thread ").append(threadName.take(40))
    }

    private fun summary(error: Throwable): String {
        val message = error.message?.let(::clean)?.takeIf { it.isNotBlank() }
        return error.javaClass.simpleName.ifEmpty { error.javaClass.name } + (message?.let { ": $it" } ?: "")
    }

    private fun clean(message: String): String {
        val text = PATH.replace(message.replace('\n', ' '), "<arquivo>")
        return if (text.length > MAX_MESSAGE) text.take(MAX_MESSAGE) + "…" else text
    }

    private fun frame(element: StackTraceElement): String {
        val className = element.className.substringAfterLast('.')
        val line = if (element.lineNumber > 0) ":${element.lineNumber}" else ""
        return "$className.${element.methodName}(${element.fileName ?: "?"}$line)"
    }
}
