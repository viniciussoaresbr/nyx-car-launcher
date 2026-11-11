package com.premiumcar.launcher.player

import android.app.Application
import android.app.Notification
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.premiumcar.launcher.system.Background
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.util.concurrent.Executor

/**
 * Player próprio na API real da central (Android 8.1), com um "pendrive" em pasta temporária.
 * O MediaPlayer é o simulado do Robolectric: confere estados, não o som.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class MusicPlayerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var pendrive: File
    private lateinit var tracks: List<File>

    @Before
    fun setUp() {
        Background.executor = Executor { it.run() }
        MusicPlayer.resetForTests()
        pendrive = temp.newFolder("1234-ABCD")
        tracks = listOf("01 - Abertura.mp3", "02 - Estrada.mp3", "10 - Final.mp3").map(::track)
        MusicPlayer.init(app)
    }

    @After
    fun tearDown() = MusicPlayer.resetForTests()

    private fun track(name: String): File = File(pendrive, name).apply {
        writeText("x")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(path), ShadowMediaPlayer.MediaInfo(180_000, 0))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val audio: ShadowAudioManager get() = shadowOf(app.getSystemService(AudioManager::class.java))

    private val mediaPlayer: ShadowMediaPlayer get() = shadowOf(MusicPlayer.mediaPlayerForTests!!)

    private fun play(index: Int = 1) {
        MusicPlayer.playFrom(pendrive, "Pendrive", tracks, index)
        idle()
    }

    @Test
    fun `tocar uma musica pede o foco de audio, inicia o servico e guarda a escolha`() {
        play(1)
        val state = MusicPlayer.state
        assertTrue(state.playing)
        assertEquals(1, state.index)
        assertEquals(3, state.count)
        assertEquals("02 - Estrada", state.title) // sem etiquetas: nome do arquivo
        assertEquals(ShadowMediaPlayer.State.STARTED, mediaPlayer.state)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, audio.lastAudioFocusRequest.durationHint)
        assertEquals(PlaybackService::class.java.name, shadowOf(app).nextStartedService.component?.className)

        val saved = PlayerMemory(app).load()!!
        assertEquals(tracks[1], saved.track)
        assertEquals(pendrive, saved.folder)
        assertEquals("Pendrive", saved.folderLabel)
    }

    @Test
    fun `outro app assumindo o som pausa e nao retoma sozinho`() {
        play()
        val focus = audio.lastAudioFocusRequest.listener
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        idle()
        assertFalse(MusicPlayer.state.playing)
        assertEquals(ShadowMediaPlayer.State.PAUSED, mediaPlayer.state)

        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        idle()
        assertFalse("Perda definitiva do som: só volta com um toque", MusicPlayer.state.playing)
    }

    @Test
    fun `interrupcao curta pausa e retoma sozinha`() {
        play()
        val focus = audio.lastAudioFocusRequest.listener
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        idle()
        assertFalse(MusicPlayer.state.playing)
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        idle()
        assertTrue(MusicPlayer.state.playing)
        assertEquals(ShadowMediaPlayer.State.STARTED, mediaPlayer.state)
    }

    @Test
    fun `ao ligar a central a ultima musica volta pausada e so toca com um toque`() {
        play(2)
        MusicPlayer.seekTo(95_000)
        idle()
        MusicPlayer.pause()
        idle()

        // Central desligada e ligada: processo novo.
        MusicPlayer.resetForTests()
        shadowOf(app).clearStartedServices()
        MusicPlayer.init(app)
        idle()

        val restored = MusicPlayer.state
        assertTrue(restored.hasTrack)
        assertFalse("Nunca toca sozinho ao ligar", restored.playing)
        assertEquals("10 - Final", restored.title)
        assertEquals("Pendrive", restored.folderLabel)
        assertEquals(95_000, MusicPlayer.positionMs())
        assertNull("Nada é preparado antes do toque", MusicPlayer.mediaPlayerForTests)
        assertNull("Nenhum serviço antes do toque", shadowOf(app).nextStartedService)

        MusicPlayer.togglePlayPause()
        idle()
        val resumed = MusicPlayer.state
        assertTrue(resumed.playing)
        assertEquals(2, resumed.index)
        assertEquals(3, resumed.count)
        assertTrue(MusicPlayer.positionMs() >= 95_000)
    }

    @Test
    fun `pendrive removido para a musica, guarda a posicao e avisa`() {
        play()
        app.sendBroadcast(Intent(Intent.ACTION_MEDIA_EJECT, Uri.fromFile(pendrive)))
        idle()
        val state = MusicPlayer.state
        assertFalse(state.playing)
        assertTrue(state.hasTrack)
        assertEquals("Pendrive removido. A música parou.", state.message)
        assertEquals(-1, state.index) // a pasta é relida no próximo ▶

        // Outro pendrive (ou nenhum): a música salva não existe mais.
        tracks[1].delete()
        MusicPlayer.play()
        idle()
        assertFalse(MusicPlayer.state.playing)
        assertEquals("Última música não encontrada. Confira se o pendrive está conectado.", MusicPlayer.state.message)
    }

    @Test
    fun `fim da pasta volta a primeira musica e pausa`() {
        play(2)
        mediaPlayer.invokeCompletionListener()
        idle()
        assertFalse(MusicPlayer.state.playing)
        assertEquals(0, MusicPlayer.state.index)
    }

    @Test
    fun `musica que nao abre e pulada com aviso`() {
        val broken = File(pendrive, "00 - Quebrada.wma").apply { writeText("x") } // sem decodificador simulado
        MusicPlayer.playFrom(pendrive, "Pendrive", listOf(broken) + tracks, 0)
        idle()
        val state = MusicPlayer.state
        assertTrue(state.playing)
        assertEquals(1, state.index)
        assertEquals("Não foi possível tocar \"00 - Quebrada\".", state.message)
    }

    @Test
    fun `proxima e anterior percorrem a pasta`() {
        play(0)
        MusicPlayer.next()
        idle()
        assertEquals(1, MusicPlayer.state.index)
        MusicPlayer.next()
        MusicPlayer.next()
        idle()
        assertEquals("Depois da última, volta à primeira", 0, MusicPlayer.state.index)
        MusicPlayer.previous()
        idle()
        assertEquals("Na primeira, volta ao início dela", 0, MusicPlayer.state.index)
        assertTrue(MusicPlayer.state.playing)
    }

    @Test
    fun `lista de uma pasta do pendrive`() {
        File(pendrive, "Rock").mkdir()
        File(pendrive, "LOST.DIR").mkdir()
        File(pendrive, ".oculta").mkdir()
        File(pendrive, "capa.jpg").writeText("x")
        File(pendrive, "._02 - Estrada.mp3").writeText("x")
        val listing = MusicStorage.list(pendrive)
        assertEquals(listOf("Rock"), listing.folders.map { it.name })
        assertEquals(listOf("01 - Abertura.mp3", "02 - Estrada.mp3", "10 - Final.mp3"), listing.tracks.map { it.name })
    }

    @Test
    fun `servico mostra a notificacao enquanto toca e termina ao pausar`() {
        play(1)
        val service = Robolectric.buildService(PlaybackService::class.java).create().get()
        service.onStartCommand(Intent(), 0, 1)
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals("02 - Estrada", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(3, notification.actions.size)
        assertFalse(shadowOf(service).isStoppedBySelf)

        MusicPlayer.pause()
        idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `tempo formatado`() {
        assertEquals("0:00", MusicPlayer.formatTime(0))
        assertEquals("3:07", MusicPlayer.formatTime(187_000))
        assertEquals("1:02:03", MusicPlayer.formatTime(3_723_000))
    }
}
