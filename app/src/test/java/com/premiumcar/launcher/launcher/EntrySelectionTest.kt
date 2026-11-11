package com.premiumcar.launcher.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Escolha da entrada certa quando um pacote tem mais de um ícone (dados reais da central). */
class EntrySelectionTest {

    private val installed = listOf(
        LauncherEntry("com.autochips.bluetooth", "a.Bluetooth", "Bluetooth"),
        LauncherEntry("com.autochips.bluetooth", "a.BtMusic", "BT Música"),
        LauncherEntry("com.hcn.AutoMediaPlayer", "h.Video", "Vídeo"),
        LauncherEntry("com.hcn.AutoMediaPlayer", "h.Music", "Música"),
        LauncherEntry("com.zjinnova.zlink", "z.Main", "ZLINK5"),
    )

    private fun choose(target: AppTarget.App) = EntrySelection.choose(installed, target)?.className

    @Test
    fun `bluetooth e bt musica saem do mesmo pacote sem se confundir`() {
        assertEquals("a.Bluetooth", choose(CarApps.BLUETOOTH))
        assertEquals("a.BtMusic", choose(CarApps.BT_MUSIC))
    }

    @Test
    fun `player usb abre musica e nao video, em qualquer ordem`() {
        assertEquals("h.Music", choose(CarApps.USB_MUSIC))
        assertEquals("h.Video", choose(CarApps.VIDEO_PLAYER))
        assertEquals("h.Music", EntrySelection.choose(installed.reversed(), CarApps.USB_MUSIC)?.className)
    }

    @Test
    fun `nomes em ingles tambem sao reconhecidos`() {
        val english = listOf(
            LauncherEntry("com.hcn.AutoMediaPlayer", "h.Video", "Video"),
            LauncherEntry("com.hcn.AutoMediaPlayer", "h.Music", "Music"),
        )
        assertEquals("h.Music", EntrySelection.choose(english, CarApps.USB_MUSIC)?.className)
    }

    @Test
    fun `pacote com uma entrada so abre essa entrada`() {
        assertEquals("z.Main", choose(CarApps.CARPLAY))
    }

    @Test
    fun `sem nome reconhecido usa a primeira entrada permitida`() {
        val unknown = listOf(
            LauncherEntry("com.autochips.bluetooth", "a.Phone", "Telefone"),
            LauncherEntry("com.autochips.bluetooth", "a.Other", "Outro"),
        )
        assertEquals("a.Phone", EntrySelection.choose(unknown, CarApps.BT_MUSIC)?.className)
    }

    @Test
    fun `app ausente nao e escolhido`() {
        assertNull(choose(CarApps.RADIO))
    }
}
