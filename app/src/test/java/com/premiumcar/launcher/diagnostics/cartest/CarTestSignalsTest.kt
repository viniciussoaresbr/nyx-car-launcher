package com.premiumcar.launcher.diagnostics.cartest

import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sinais novos da gravação (0.4.0): Bluetooth por perfil, tipos de áudio e modo de áudio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class CarTestSignalsTest {

    @Test
    fun `bluetooth do lado carro aparece por perfil, sem nome nem endereco`() {
        val intent = Intent("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED")
            .putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTING)
            .putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED)
            .putExtra("android.bluetooth.device.extra.NAME", "iPhone de Alguém")
        val text = Broadcasts.describe(intent)
        assertEquals("Bluetooth A2DP sink (música do celular): conectando → conectado", text)
        assertFalse(text.contains("iPhone"))
        assertTrue(intent.action in Broadcasts.ACTIONS)
        assertTrue("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED" in Broadcasts.ACTIONS)
    }

    @Test
    fun `tipos de audio tocando em resumo estavel`() {
        assertEquals("nenhum", AudioUsage.summary(emptyList()))
        val media = AudioAttributes.USAGE_MEDIA to AudioAttributes.CONTENT_TYPE_MUSIC
        val guidance = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE to AudioAttributes.CONTENT_TYPE_SPEECH
        assertEquals("mídia/música ×2, navegação/fala ×1", AudioUsage.summary(listOf(guidance, media, media)))
        assertEquals(AudioUsage.summary(listOf(media, guidance)), AudioUsage.summary(listOf(guidance, media)))
        assertEquals("em comunicação (voz pela internet ou projeção)", AudioUsage.mode(AudioManager.MODE_IN_COMMUNICATION))
    }
}
