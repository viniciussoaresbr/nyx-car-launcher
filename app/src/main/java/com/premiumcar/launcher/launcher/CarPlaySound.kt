package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.media.AudioAttributes

/**
 * Fim do CarPlay pelo som (0.4.21), sem acessibilidade nem captura da tela. O Android avisa a
 * qualquer app, sem permissão, que tipos de som estão tocando (mídia, chamada, avisos), sem dizer
 * qual app nem o conteúdo. Lógica pura, testada na JVM; quem ouve o Android é o [CarPlayReturn].
 *
 * Nos testes de 13/11/2026 (HARDWARE.md, seção 5), com música no CarPlay:
 * - **no fim**, o som de mídia do ZLINK parou junto com a troca da tela dele para a espera (8 a 17
 *   ms antes do aviso da acessibilidade; sem fio, uma vez 0,7 s antes), 0,8 a 1,1 s antes de o
 *   cabo cair e 0,6 a 1,8 s antes de o Bluetooth religar;
 * - **com a música pausada**, o som continuou tocando (1 min 49 s numa sessão);
 * - **no fim de uma ligação ou da Siri**, o som parou com o CarPlay continuando: a fala ("chamada")
 *   acabou no mesmo instante, e a central ainda marcava a chamada (`sys.nonsystem.callstate`);
 * - no fim falso pelo Bluetooth (a central o religa no meio do CarPlay sem fio), a música
 *   continuava tocando ([mediaPlaying]).
 *
 * Sem música desde que o CarPlay conectou, não há som: o fim continua vindo pelo Bluetooth ou
 * pelo cabo.
 */
class CarPlaySound {

    /** O tipo de um som tocando. */
    enum class Kind { MEDIA, VOICE, OTHER }

    /** O que uma mudança no som quer dizer. */
    enum class Change {
        /** Nada que indique o fim. */
        NONE,

        /** Todo o som parou com a música tocando: o CarPlay provavelmente terminou. */
        ENDED,

        /** O som parou no fim de uma ligação ou da Siri: o CarPlay continua. */
        AFTER_CALL,

        /** O som parou, mas a música tinha acabado de começar: não dá para saber. */
        TOO_SHORT,
    }

    private var playing: List<Kind> = emptyList()
    private var mediaSince: Long? = null
    private var mediaStoppedAt: Long? = null
    private var mediaRan = 0L
    private var voiceActive = false
    private var voiceEndedAt: Long? = null

    /** A música (som de mídia) está tocando agora. */
    val mediaPlaying: Boolean get() = mediaSince != null

    /** Novos sons tocando em [now] (relógio em ms); [inCall]: a central marca uma chamada. */
    fun update(kinds: List<Kind>, now: Long, inCall: Boolean): Change {
        val wasPlaying = playing.isNotEmpty()
        playing = kinds
        val voice = Kind.VOICE in kinds
        if (voiceActive && !voice) voiceEndedAt = now
        voiceActive = voice
        val media = Kind.MEDIA in kinds
        val since = mediaSince
        if (media && since == null) mediaSince = now
        if (!media && since != null) {
            mediaRan = now - since
            mediaStoppedAt = now
            mediaSince = null
        }
        if (!wasPlaying || kinds.isNotEmpty()) return Change.NONE
        // Todo o som parou agora. Só interessa se a música estava tocando até há pouco.
        val stoppedAt = mediaStoppedAt ?: return Change.NONE
        if (now - stoppedAt > MEDIA_RECENT_MS) return Change.NONE
        val voiceEnded = voiceEndedAt
        if (inCall || (voiceEnded != null && now - voiceEnded <= VOICE_GRACE_MS)) return Change.AFTER_CALL
        if (mediaRan < MEDIA_MIN_MS) return Change.TOO_SHORT
        return Change.ENDED
    }

    companion object {
        /** O tipo de um som pelo uso que o app declarou ao Android. */
        @SuppressLint("InlinedApi") // USAGE_ASSISTANT: os avisos de som só existem a partir do Android 8
        fun kindOf(usage: Int): Kind = when (usage) {
            AudioAttributes.USAGE_MEDIA -> Kind.MEDIA
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
            AudioAttributes.USAGE_ASSISTANT,
            -> Kind.VOICE
            else -> Kind.OTHER
        }

        /** A música precisa ter tocado pelo menos isto para o silêncio valer como fim. */
        const val MEDIA_MIN_MS = 1_000L

        /** A música parou até isto antes de todo o som parar (no fim, tudo para junto). */
        const val MEDIA_RECENT_MS = 500L

        /**
         * A fala parou até isto antes: é o fim de uma ligação ou da Siri. No teste, 7 ms antes; no
         * fim de verdade mais perto de uma ligação, 3,8 s depois.
         */
        const val VOICE_GRACE_MS = 2_000L
    }
}
