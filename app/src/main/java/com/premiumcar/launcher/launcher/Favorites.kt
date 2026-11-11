package com.premiumcar.launcher.launcher

/** Um favorito da aba Aplicativos: um app conhecido do Nyx Car (pelo id) ou outro app da central. */
sealed interface FavoriteKey {

    /** Identidade usada para comparar favoritos (o nome de um app desconhecido pode mudar). */
    val id: String

    data class Known(val appId: String) : FavoriteKey {
        override val id get() = "k:$appId"
    }

    data class Other(val packageName: String, val className: String, val label: String) : FavoriteKey {
        override val id get() = "a:$packageName/$className"
    }
}

/**
 * Regras dos favoritos da aba Aplicativos (0.4.4). Lógica pura, testada na JVM.
 *
 * São sempre [COUNT] (a grade 3 × 4 fica completa) e nunca repetidos. Trocar um lugar por um app
 * que já é favorito troca os dois de lugar.
 */
object Favorites {

    const val COUNT = 12

    /** Põe [key] no lugar [slot]; se [key] já estava noutro lugar, os dois trocam de lugar. */
    fun replace(list: List<FavoriteKey>, slot: Int, key: FavoriteKey): List<FavoriteKey> {
        val result = list.toMutableList()
        val previous = result.indexOfFirst { it.id == key.id }
        if (previous >= 0) result[previous] = result[slot]
        result[slot] = key
        return result
    }

    /**
     * Lista guardada → lista válida: tira ids que esta versão não conhece e repetidos, e completa
     * com os padrões até [COUNT]. Sem nada guardado, vale o padrão.
     */
    fun normalize(stored: List<FavoriteKey>, defaults: List<FavoriteKey>, knownIds: Set<String>): List<FavoriteKey> {
        val result = mutableListOf<FavoriteKey>()
        fun offer(key: FavoriteKey) {
            val valid = key !is FavoriteKey.Known || key.appId in knownIds
            if (valid && result.size < COUNT && result.none { it.id == key.id }) result += key
        }
        stored.forEach(::offer)
        defaults.forEach(::offer)
        return result
    }

    /** Uma linha por favorito: `k:<id>` ou `a:<pacote>\t<classe>\t<nome>`. */
    fun encode(list: List<FavoriteKey>): String = list.joinToString("\n") { key ->
        when (key) {
            is FavoriteKey.Known -> "k:${key.appId}"
            is FavoriteKey.Other -> "a:" + listOf(key.packageName, key.className, key.label).joinToString("\t") { it.clean() }
        }
    }

    /** Ignora linhas que não entende (por exemplo, de uma versão futura). */
    fun decode(text: String?): List<FavoriteKey> = text.orEmpty().lineSequence().mapNotNull { line ->
        when {
            line.startsWith("k:") -> line.removePrefix("k:").takeIf { it.isNotBlank() }?.let { FavoriteKey.Known(it) }
            line.startsWith("a:") -> line.removePrefix("a:").split('\t').takeIf { it.size == 3 && it.none(String::isBlank) }
                ?.let { (pkg, cls, label) -> FavoriteKey.Other(pkg, cls, label) }
            else -> null
        }
    }.toList()

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ').trim()
}
