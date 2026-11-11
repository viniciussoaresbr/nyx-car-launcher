package com.premiumcar.launcher.diagnostics.cartest

import android.media.AudioAttributes
import android.media.AudioManager

/** Nomes em português dos tipos de áudio e modos do Android, para a linha do tempo. */
internal object AudioUsage {

    fun usage(value: Int): String = when (value) {
        AudioAttributes.USAGE_MEDIA -> "mídia"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "chamada"
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> "sinal de chamada"
        AudioAttributes.USAGE_ALARM -> "alarme"
        AudioAttributes.USAGE_NOTIFICATION -> "notificação"
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "toque de chamada"
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY -> "acessibilidade"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "navegação"
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "som do sistema"
        AudioAttributes.USAGE_GAME -> "jogo"
        AudioAttributes.USAGE_ASSISTANT -> "assistente de voz"
        AudioAttributes.USAGE_UNKNOWN -> "desconhecido"
        else -> "uso $value"
    }

    fun content(value: Int): String = when (value) {
        AudioAttributes.CONTENT_TYPE_SPEECH -> "fala"
        AudioAttributes.CONTENT_TYPE_MUSIC -> "música"
        AudioAttributes.CONTENT_TYPE_MOVIE -> "filme"
        AudioAttributes.CONTENT_TYPE_SONIFICATION -> "aviso"
        else -> "?"
    }

    /** Ex.: "mídia/música ×1, navegação/fala ×1"; "nenhum" se nada toca. Em ordem fixa. */
    fun summary(players: List<Pair<Int, Int>>): String {
        if (players.isEmpty()) return "nenhum"
        return players.map { (usage, content) -> "${usage(usage)}/${content(content)}" }
            .groupingBy { it }.eachCount()
            .toSortedMap()
            .entries.joinToString(", ") { "${it.key} ×${it.value}" }
    }

    fun mode(value: Int): String = when (value) {
        AudioManager.MODE_NORMAL -> "normal"
        AudioManager.MODE_RINGTONE -> "tocando chamada"
        AudioManager.MODE_IN_CALL -> "em chamada"
        AudioManager.MODE_IN_COMMUNICATION -> "em comunicação (voz pela internet ou projeção)"
        else -> "modo $value"
    }
}
