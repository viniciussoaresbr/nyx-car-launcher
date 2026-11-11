package com.premiumcar.launcher.diagnostics

/** Propriedades do sistema já filtradas (sem identificadores do aparelho ou da rede). */
class PropertySnapshot(val visible: Map<String, String>, val omittedCount: Int) {
    operator fun get(key: String): String? = visible[key]?.takeIf { it.isNotBlank() }
}

/**
 * Lê as propriedades com o utilitário `getprop`, disponível a qualquer app
 * (somente leitura, não altera nada no sistema).
 */
object SystemProperties {
    private val linePattern = Regex("""^\[(.+?)]: \[(.*)]$""")

    /** Chaves que podem identificar o aparelho, o usuário ou a rede: nunca vão para o relatório. */
    private val sensitiveKey = Regex(
        "serial|imei|meid|iccid|imsi|msisdn|mac|addr|ssid|uuid|android_?id|hostname|" +
            "password|passwd|secret|token|account|phone|device_?id|qr|location|latitude|" +
            "longitude|dhcp|dns|gateway|(^|[._])sn($|[._])|(^|[._])cid($|[._])|[._]key($|[._])",
        RegexOption.IGNORE_CASE,
    )
    private val macValue = Regex("""([0-9a-f]{2}[:-]){5}[0-9a-f]{2}""", RegexOption.IGNORE_CASE)

    fun read(): PropertySnapshot = parse(runGetprop())

    internal fun parse(output: String): PropertySnapshot {
        val visible = sortedMapOf<String, String>()
        var omitted = 0
        output.lineSequence().forEach { line ->
            val match = linePattern.matchEntire(line.trim()) ?: return@forEach
            val (key, value) = match.destructured
            if (isSensitive(key, value)) omitted++ else visible[key] = value
        }
        return PropertySnapshot(visible, omitted)
    }

    internal fun isSensitive(key: String, value: String): Boolean =
        sensitiveKey.containsMatchIn(key) || macValue.containsMatchIn(value)

    fun toSection(snapshot: PropertySnapshot) = section("Propriedades do sistema (${snapshot.visible.size})") {
        if (snapshot.visible.isEmpty()) warn("getprop", "sem acesso às propriedades neste firmware")
        snapshot.visible.forEach { (key, value) -> add(key, value.ifEmpty { "(vazio)" }) }
        note = "${snapshot.omittedCount} propriedades com possíveis identificadores " +
            "(serial, MAC, IMEI, rede etc.) foram omitidas."
    }

    private fun runGetprop(): String = try {
        val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    } catch (e: Exception) {
        ""
    }
}
