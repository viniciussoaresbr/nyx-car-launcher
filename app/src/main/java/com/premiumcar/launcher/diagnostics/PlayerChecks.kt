package com.premiumcar.launcher.diagnostics

import android.content.Context
import com.premiumcar.launcher.player.AudioFiles
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.player.MusicStorage
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.player.StoragePermission

/**
 * Player próprio (etapa 0.4.0) visto de dentro da central: permissão, pendrives encontrados,
 * formatos e o diário de eventos. Somente leitura; sem nomes de músicas nem números de série.
 */
object PlayerChecks {

    fun sections(context: Context): List<InfoSection> = listOf(state(context), events(context))

    private fun state(context: Context) = section("Player próprio (pendrive)") {
        val granted = StoragePermission.isGranted(context)
        if (granted) {
            add("Permissão de leitura das músicas", "concedida")
        } else {
            warn("Permissão de leitura das músicas", "não concedida (pedida na aba Música)")
        }
        val player = MusicPlayer.state
        add(
            "Estado do player",
            when {
                player.playing -> "tocando"
                player.hasTrack -> "pausado"
                else -> "nenhuma música escolhida"
            },
        )
        player.track?.let { track ->
            val position = if (player.count > 0) "faixa ${player.index + 1} de ${player.count}" else "fila ainda não lida"
            add("Música atual", "$position (.${AudioFiles.extension(track.name)})")
        }
        if (!granted) return@section
        val volumes = MusicStorage.volumes(context)
        add("Pendrives encontrados", if (volumes.isEmpty()) "nenhum" else volumes.size, if (volumes.isEmpty()) EntryLevel.WARNING else EntryLevel.HIGHLIGHT)
        add("Como foram encontrados", MusicStorage.lastMethod)
        volumes.forEachIndexed { i, volume ->
            val root = runCatching { MusicStorage.list(volume.root) }.getOrNull()
            if (root == null) {
                warn("Pendrive ${i + 1} (${volume.label})", "não foi possível ler a raiz")
                return@forEachIndexed
            }
            val formats = root.tracks.groupingBy { AudioFiles.extension(it.name) }.eachCount().entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key} ×${it.value}" }
            add(
                "Pendrive ${i + 1} (${volume.label})",
                "raiz: ${root.folders.size} pastas, ${root.tracks.size} músicas" + if (formats.isNotEmpty()) " ($formats)" else "",
            )
        }
    }

    private fun events(context: Context) = section("Diário de eventos (player e erros do app)") {
        val lines = PlayerLog.read(context)
        if (lines.isEmpty()) add("Registros", "nenhum ainda: abra o Nyx Car e toque uma música")
        lines.forEach { (time, text) ->
            if (text.startsWith(CRASH_PREFIX)) warn(time, text) else add(time, text)
        }
        note = "Sem nomes de músicas. \"Player iniciado\" marca cada vez que o Nyx Car foi aberto do zero e " +
            "mostra há quanto tempo o sistema está ligado (se a central reiniciou ou só acordou). Sem um " +
            "\"Erro do app\" logo antes, quem fechou o Nyx Car foi a central, não um erro do app."
    }

    private const val CRASH_PREFIX = "Erro do app"

}
