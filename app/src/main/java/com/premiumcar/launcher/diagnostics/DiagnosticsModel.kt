package com.premiumcar.launcher.diagnostics

/** Abas do painel de diagnóstico, na ordem de exibição e do relatório. */
enum class DiagnosticTab(val title: String) {
    CAR_TESTS("Testes no carro"),
    SYSTEM("Sistema"),
    DISPLAY("Tela e toque"),
    APPS("Apps e atalhos"),
    FEATURES("Recursos"),
    PROPERTIES("Propriedades"),
    PERFORMANCE("Desempenho"),
    CALIBRATION("Calibração"),
}

enum class EntryLevel { NORMAL, HIGHLIGHT, WARNING }

data class InfoEntry(val label: String, val value: String, val level: EntryLevel = EntryLevel.NORMAL)

data class InfoSection(val title: String, val entries: List<InfoEntry>, val note: String? = null)

/** Resultado da coleta automática, agrupado por aba. */
class DiagnosticReport(val sections: Map<DiagnosticTab, List<InfoSection>>)

/** Construtor que mantém os coletores curtos e legíveis. */
class SectionBuilder(private val title: String) {
    private val entries = mutableListOf<InfoEntry>()
    var note: String? = null

    fun add(label: String, value: Any?, level: EntryLevel = EntryLevel.NORMAL) {
        val text = value?.toString()?.takeIf { it.isNotBlank() } ?: "indisponível"
        entries += InfoEntry(label, text, level)
    }

    fun highlight(label: String, value: Any?) = add(label, value, EntryLevel.HIGHLIGHT)

    fun warn(label: String, value: Any?) = add(label, value, EntryLevel.WARNING)

    fun build() = InfoSection(title, entries.toList(), note)
}

/**
 * Monta uma seção. Qualquer falha vira uma linha de aviso em vez de derrubar o app:
 * cada firmware expõe as informações de um jeito, e o diagnóstico precisa sobreviver a isso.
 */
inline fun section(title: String, block: SectionBuilder.() -> Unit): InfoSection =
    try {
        SectionBuilder(title).apply(block).build()
    } catch (t: Throwable) {
        errorSection(title, t)
    }

fun errorSection(title: String, error: Throwable) =
    InfoSection(title, listOf(InfoEntry("Falha na coleta", error.toString(), EntryLevel.WARNING)))
