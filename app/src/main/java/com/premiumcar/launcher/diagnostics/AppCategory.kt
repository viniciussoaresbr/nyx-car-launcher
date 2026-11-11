package com.premiumcar.launcher.diagnostics

/**
 * Categorias usadas para sugerir atalhos a partir do nome e do pacote dos apps.
 * [fragments] casam em qualquer parte do texto; [tokens] (siglas curtas como "bt")
 * só casam como palavra inteira, para evitar falsos positivos.
 */
enum class AppCategory(
    val title: String,
    private val fragments: List<String>,
    private val tokens: List<String> = emptyList(),
) {
    PROJECTION(
        "Projeção (CarPlay / Android Auto)",
        listOf("zlink", "carplay", "androidauto", "android auto", "autolink", "carlink", "easyconnect", "phonelink", "tlink", "mirror"),
    ),
    BLUETOOTH("Bluetooth / telefone", listOf("bluetooth", "dialer", "telefone", "phone"), listOf("bt")),
    MUSIC(
        "Música / mídia",
        listOf("music", "música", "musica", "player", "mp3", "audio", "áudio", "spotify", "deezer"),
        listOf("media", "mídia"),
    ),
    RADIO("Rádio", listOf("radio", "rádio"), listOf("fm")),
    NAVIGATION("Navegação", listOf("navi", "maps", "mapa", "waze", "sygic", "gps"), listOf("nav", "map", "igo")),
    VIDEO("Vídeo", listOf("video", "vídeo", "youtube")),
    VEHICLE(
        "Veículo / câmera",
        listOf("canbus", "carinfo", "vehicle", "camera", "câmera", "reverse", "steer", "volante", "avin", "dvr", "tpms"),
        listOf("can", "car"),
    ),
    SETTINGS("Configurações", listOf("settings", "setting", "configura", "ajustes")),
    FILES("Arquivos", listOf("filemanager", "file manager", "arquivos", "explorer", "documents"), listOf("files")),
    ;

    fun matches(label: String, packageName: String): Boolean {
        val text = "$label $packageName".lowercase()
        if (fragments.any { it in text }) return true
        if (tokens.isEmpty()) return false
        val words = text.split(NON_WORD)
        return tokens.any { it in words }
    }

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    }
}
