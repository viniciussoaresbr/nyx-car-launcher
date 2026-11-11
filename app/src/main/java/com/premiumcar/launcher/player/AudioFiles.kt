package com.premiumcar.launcher.player

import java.io.File
import java.text.Collator
import java.util.Locale

/** Regras sobre nomes de arquivos e pastas de música. Lógica pura, testada na JVM. */
object AudioFiles {

    /**
     * Extensões que o player lista. Se a central não tiver decodificador para alguma (por
     * exemplo, WMA ou APE), a faixa falha ao tocar, o player pula para a próxima e o PC
     * Diagnóstico registra o formato que falhou.
     */
    val EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "ape", "amr", "mka", "mp2")

    /** Pastas criadas por sistemas operacionais, sem música do usuário. */
    private val SYSTEM_FOLDERS = setOf("android", "lost.dir", "system volume information", "found.000")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** Arquivos ocultos ficam de fora (ex.: "._musica.mp3", que o macOS cria em pendrives). */
    fun isAudio(name: String): Boolean = !name.startsWith(".") && extension(name) in EXTENSIONS

    fun isBrowsableFolder(name: String): Boolean =
        name.isNotBlank() && !name.startsWith(".") && !name.startsWith("$") &&
            name.lowercase(Locale.ROOT) !in SYSTEM_FOLDERS

    /** Nome exibido na lista: o nome do arquivo sem a extensão. */
    fun displayName(name: String): String =
        if (isAudio(name)) name.substring(0, name.length - extension(name).length - 1) else name

    /**
     * Ordem "natural", como num gerenciador de arquivos: "2 - b" antes de "10 - a",
     * maiúsculas e acentos sem peso na ordem. Um novo comparador por ordenação, porque o
     * Collator não pode ser usado por duas threads ao mesmo tempo.
     */
    fun naturalOrder(): Comparator<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }
        return Comparator { a, b ->
            val left = chunks(a)
            val right = chunks(b)
            for (i in 0 until minOf(left.size, right.size)) {
                val x = left[i]
                val y = right[i]
                val result = if (x[0].isDigit() && y[0].isDigit()) compareNumbers(x, y) else collator.compare(x, y)
                if (result != 0) return@Comparator result
            }
            left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
        }
    }

    fun sorted(files: List<File>): List<File> {
        val order = naturalOrder()
        return files.sortedWith { a, b -> order.compare(a.name, b.name) }
    }

    /** Divide em trechos só de dígitos ou só de não dígitos: "Faixa 10b" → ["Faixa ", "10", "b"]. */
    private fun chunks(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = ArrayList<String>()
        var start = 0
        for (i in 1..text.length) {
            if (i == text.length || text[i].isDigit() != text[start].isDigit()) {
                result += text.substring(start, i)
                start = i
            }
        }
        return result
    }

    private fun compareNumbers(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }
}
