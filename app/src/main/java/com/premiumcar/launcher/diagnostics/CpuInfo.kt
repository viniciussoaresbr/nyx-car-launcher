package com.premiumcar.launcher.diagnostics

/** Dados extraídos de /proc/cpuinfo. */
data class CpuInfo(
    val hardware: String?,
    val modelName: String?,
    val architecture: String?,
    val implementer: String?,
    val parts: List<String>,
    val features: String?,
) {
    /** Ex.: "4× Cortex-A7 (0xc07)". Nulo quando o kernel não informa os núcleos. */
    fun coreSummary(): String? {
        if (parts.isEmpty()) return null
        return parts.groupingBy { it }.eachCount().entries.joinToString(" + ") { (part, count) ->
            val name = if (implementer.equals("0x41", ignoreCase = true)) armCoreName(part) else null
            "$count× ${name ?: "núcleo"} ($part)"
        }
    }

    companion object {
        fun parse(text: String): CpuInfo {
            var hardware: String? = null
            var modelName: String? = null
            var architecture: String? = null
            var implementer: String? = null
            var features: String? = null
            val parts = mutableListOf<String>()
            text.lineSequence().forEach { line ->
                val separator = line.indexOf(':')
                if (separator < 0) return@forEach
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                when (key) {
                    "Hardware" -> hardware = value
                    "model name", "Processor" -> if (modelName == null) modelName = value
                    "CPU architecture" -> if (architecture == null) architecture = value
                    "CPU implementer" -> if (implementer == null) implementer = value
                    "CPU part" -> parts += value.lowercase()
                    "Features", "flags" -> if (features == null) features = value
                }
            }
            return CpuInfo(hardware, modelName, architecture, implementer, parts, features)
        }

        /** Códigos "CPU part" dos núcleos ARM (fabricante 0x41). */
        private val armCores = mapOf(
            "0xc05" to "Cortex-A5", "0xc07" to "Cortex-A7", "0xc08" to "Cortex-A8",
            "0xc09" to "Cortex-A9", "0xc0d" to "Cortex-A12", "0xc0e" to "Cortex-A17",
            "0xc0f" to "Cortex-A15", "0xd01" to "Cortex-A32", "0xd03" to "Cortex-A53",
            "0xd04" to "Cortex-A35", "0xd05" to "Cortex-A55", "0xd07" to "Cortex-A57",
            "0xd08" to "Cortex-A72", "0xd09" to "Cortex-A73", "0xd0a" to "Cortex-A75",
            "0xd0b" to "Cortex-A76", "0xd0d" to "Cortex-A77", "0xd41" to "Cortex-A78",
            "0xd46" to "Cortex-A510", "0xd47" to "Cortex-A710", "0xd80" to "Cortex-A520",
        )

        fun armCoreName(part: String): String? = armCores[part.lowercase()]
    }
}
