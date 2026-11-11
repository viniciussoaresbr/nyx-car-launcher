package com.premiumcar.launcher.diagnostics.cartest

/** Diferenças entre duas leituras das propriedades do sistema. Lógica pura, testada na JVM. */
object PropertyDiff {

    /** Contadores que mudam o tempo todo e só poluiriam a linha do tempo. */
    private val noisy = Regex("statistics|^log\\.|^debug\\.", RegexOption.IGNORE_CASE)

    fun isNoisy(key: String) = noisy.containsMatchIn(key)

    fun diff(old: Map<String, String>, new: Map<String, String>): List<String> = buildList {
        (old.keys + new.keys).toSortedSet().forEach { key ->
            if (isNoisy(key)) return@forEach
            val before = old[key]
            val after = new[key]
            when {
                before == after -> Unit
                before == null -> add("nova: $key = ${after.orEmpty().ifEmpty { "(vazio)" }}")
                after == null -> add("removida: $key (era ${before.ifEmpty { "(vazio)" }})")
                else -> add("$key: ${before.ifEmpty { "(vazio)" }} → ${after.ifEmpty { "(vazio)" }}")
            }
        }
    }
}
