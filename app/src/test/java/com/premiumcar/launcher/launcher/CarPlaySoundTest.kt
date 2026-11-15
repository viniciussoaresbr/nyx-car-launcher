package com.premiumcar.launcher.launcher

import android.media.AudioAttributes
import com.premiumcar.launcher.launcher.CarPlaySound.Change
import com.premiumcar.launcher.launcher.CarPlaySound.Kind.MEDIA
import com.premiumcar.launcher.launcher.CarPlaySound.Kind.OTHER
import com.premiumcar.launcher.launcher.CarPlaySound.Kind.VOICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fim do CarPlay pelo som (0.4.21), com as sequências dos testes no carro de 13/11/2026
 * (relatórios `nyx-car-diagnostico-20261113-180907.txt` e `-193943.txt`). Horários em ms.
 */
class CarPlaySoundTest {

    private val sound = CarPlaySound()

    private fun at(time: Long, vararg kinds: CarPlaySound.Kind, inCall: Boolean = false) =
        sound.update(kinds.toList(), time, inCall)

    @Test
    fun `fim com musica - todo o som para junto com a tela do ZLINK`() {
        assertEquals(Change.NONE, at(0, MEDIA)) // 18:04:02, música tocando
        assertTrue(sound.mediaPlaying)
        assertEquals(Change.ENDED, at(60_343)) // 18:05:02.689, fim pelo cabo
        assertFalse(sound.mediaPlaying)
    }

    @Test
    fun `fim com dois sons de midia e com um aviso tocando`() {
        // 18:01:57: mídia ×2; 18:02:23.212: mídia ×1; .214: nenhum.
        at(0, MEDIA)
        at(16_370, MEDIA, MEDIA)
        assertEquals(Change.NONE, at(42_193, MEDIA))
        assertEquals(Change.ENDED, at(42_195))
        // 18:03:18: mídia + toque de chamada/aviso; 18:03:31.580: mídia; .590: nenhum.
        val other = CarPlaySound()
        other.update(listOf(MEDIA), 0, false)
        other.update(listOf(MEDIA, OTHER), 29_233, false)
        other.update(listOf(MEDIA), 41_926, false)
        assertEquals(Change.ENDED, other.update(emptyList(), 41_936, false))
    }

    @Test
    fun `fim de uma ligacao com a musica parando junto nao e fim`() {
        // 18:07:00.748 mídia; 18:07:18.806 chamada + mídia; 18:07:25.399 mídia; .406 nenhum.
        at(0, MEDIA)
        at(18_058, VOICE, MEDIA)
        at(24_651, MEDIA)
        assertEquals(Change.AFTER_CALL, at(24_658))
        // 18:07:26.794 a mídia volta; 18:07:29.235 o fim de verdade, 3,8 s depois da ligação.
        at(26_046, MEDIA)
        assertEquals(Change.ENDED, at(28_487))
    }

    @Test
    fun `com a central marcando uma chamada nao e fim`() {
        at(0, MEDIA)
        assertEquals(Change.AFTER_CALL, at(30_000, inCall = true))
    }

    @Test
    fun `so a fala ou so um aviso parando nao e fim`() {
        // 18:06:52.481 chamada; 18:06:57.138 nenhum (sem música no CarPlay).
        at(0, VOICE)
        assertEquals(Change.NONE, at(4_657))
        // Um aviso da navegação sem música.
        at(10_000, OTHER)
        assertEquals(Change.NONE, at(12_000))
    }

    @Test
    fun `musica que mal comecou nao conta`() {
        at(0, MEDIA)
        assertEquals(Change.TOO_SHORT, at(500))
    }

    @Test
    fun `musica que parou antes, com outro som ainda tocando, nao conta quando ele para`() {
        at(0, MEDIA)
        at(30_000, OTHER) // a música parou e ficou um aviso
        assertEquals(Change.NONE, at(32_000))
    }

    @Test
    fun `tipos de som pelo uso`() {
        assertEquals(MEDIA, CarPlaySound.kindOf(AudioAttributes.USAGE_MEDIA))
        assertEquals(VOICE, CarPlaySound.kindOf(AudioAttributes.USAGE_VOICE_COMMUNICATION))
        assertEquals(VOICE, CarPlaySound.kindOf(AudioAttributes.USAGE_ASSISTANT))
        assertEquals(OTHER, CarPlaySound.kindOf(AudioAttributes.USAGE_NOTIFICATION_RINGTONE))
        assertEquals(OTHER, CarPlaySound.kindOf(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
    }
}
