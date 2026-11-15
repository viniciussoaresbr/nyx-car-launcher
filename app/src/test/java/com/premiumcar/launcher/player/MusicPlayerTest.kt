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

    // ---------- Aleatório (0.4.24) ----------

    /** Pasta com [count] músicas: "01.mp3", "02.mp3"… */
    private fun folderWith(count: Int, name: String = "Viagem"): Pair<File, List<File>> {
        val dir = File(pendrive, name).apply { mkdirs() }
        return dir to (1..count).map { File(dir, "%02d.mp3".format(it)).apply { writeTrack() } }
    }

    private fun File.writeTrack() {
        writeText("x")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(path), ShadowMediaPlayer.MediaInfo(180_000, 0))
    }

    /** Toca [steps] vezes "próxima" e devolve as músicas tocadas, a atual primeiro. */
    private fun playedWithNext(steps: Int): List<File> = buildList {
        add(MusicPlayer.state.track!!)
        repeat(steps) {
            MusicPlayer.next()
            idle()
            add(MusicPlayer.state.track!!)
        }
    }

    @Test
    fun `aleatorio toca cada musica uma vez antes de repetir e anterior volta a que tocou`() {
        val (dir, songs) = folderWith(8)
        MusicPlayer.toggleShuffle()
        MusicPlayer.playFrom(dir, "Viagem", songs, 2)
        idle()
        assertTrue(MusicPlayer.state.shuffle)
        assertEquals("A música escolhida toca primeiro", songs[2], MusicPlayer.state.track)
        assertEquals(0, MusicPlayer.state.index)

        val played = playedWithNext(7)
        assertEquals(songs.toSet(), played.toSet())
        assertEquals(7, MusicPlayer.state.index)
        assertEquals(8, MusicPlayer.state.count)
        assertTrue(MusicPlayer.state.playing)

        MusicPlayer.previous()
        idle()
        assertEquals(played[6], MusicPlayer.state.track)
        assertEquals(6, MusicPlayer.state.index)
    }

    @Test
    fun `ligar o aleatorio mantem a musica e desligar volta a ordem da pasta`() {
        val (dir, songs) = folderWith(6)
        MusicPlayer.playFrom(dir, "Viagem", songs, 1)
        idle()
        assertFalse(MusicPlayer.state.shuffle)

        MusicPlayer.toggleShuffle()
        idle()
        assertTrue(MusicPlayer.state.shuffle)
        assertEquals("Ligar não troca a música", songs[1], MusicPlayer.state.track)
        assertTrue(MusicPlayer.state.playing)
        assertEquals(ShadowMediaPlayer.State.STARTED, mediaPlayer.state)
        assertEquals(0, MusicPlayer.state.index)
        assertTrue(PlayerMemory(app).loadMode().shuffle)

        MusicPlayer.next()
        idle()
        val current = MusicPlayer.state.track!!
        MusicPlayer.toggleShuffle()
        idle()
        assertFalse(MusicPlayer.state.shuffle)
        assertEquals(current, MusicPlayer.state.track)
        val at = songs.indexOf(current)
        assertEquals("Desligado, a posição volta a ser a da pasta", at, MusicPlayer.state.index)
        MusicPlayer.next()
        idle()
        assertEquals(songs[(at + 1) % songs.size], MusicPlayer.state.track)
        assertFalse(PlayerMemory(app).loadMode().shuffle)
    }

    @Test
    fun `aleatorio da lista toca a pasta e todas as subpastas`() {
        val (_, travel) = folderWith(2)
        val (_, rock) = folderWith(2, "Viagem/Rock")
        val (_, eighties) = folderWith(1, "Viagem/Rock/Anos 80")
        File(pendrive, "Viagem/.oculta").mkdir()
        val all = travel + rock + eighties

        MusicPlayer.playShuffled(File(pendrive, "Viagem"), "Viagem")
        idle()
        val state = MusicPlayer.state
        assertTrue(state.shuffle)
        assertTrue(state.playing)
        assertFalse(state.loading)
        assertEquals(5, state.count)
        assertEquals("Viagem", state.folderLabel)
        assertEquals(all.toSet(), playedWithNext(4).toSet())

        val mode = PlayerMemory(app).loadMode()
        assertTrue(mode.shuffle)
        assertTrue(mode.subfolders)
    }

    @Test
    fun `aleatorio de pasta sem musica avisa e nao toca`() {
        File(pendrive, "Vazia/Sub").mkdirs()
        MusicPlayer.playShuffled(File(pendrive, "Vazia"), "Vazia")
        idle()
        assertFalse(MusicPlayer.state.hasTrack)
        assertFalse(MusicPlayer.state.loading)
        assertEquals("Nenhuma música nesta pasta nem nas subpastas.", MusicPlayer.state.message)
    }

    @Test
    fun `ao ligar a central o aleatorio continua na mesma ordem`() {
        val (_, songs) = folderWith(8)
        File(pendrive, "Viagem/Extra").mkdir()
        val extra = File(pendrive, "Viagem/Extra/09.mp3").apply { writeTrack() }
        MusicPlayer.playShuffled(File(pendrive, "Viagem"), "Viagem")
        idle()
        val sequence = playedWithNext(8)
        assertEquals((songs + extra).toSet(), sequence.toSet())
        repeat(5) {
            MusicPlayer.previous()
            idle()
        }
        assertEquals(sequence[3], MusicPlayer.state.track)
        MusicPlayer.pause()
        idle()

        // Central desligada e ligada: processo novo.
        MusicPlayer.resetForTests()
        MusicPlayer.init(app)
        idle()
        assertTrue(MusicPlayer.state.shuffle)
        assertEquals(sequence[3], MusicPlayer.state.track)

        MusicPlayer.togglePlayPause()
        idle()
        assertTrue(MusicPlayer.state.playing)
        assertEquals(3, MusicPlayer.state.index)
        assertEquals("Fila relida com as subpastas", 9, MusicPlayer.state.count)
        assertEquals(sequence.drop(4), playedWithNext(5).drop(1))
    }

    @Test
    fun `fim da fila no aleatorio sorteia uma ordem nova e pausa`() {
        val (dir, songs) = folderWith(5)
        MusicPlayer.toggleShuffle()
        MusicPlayer.playFrom(dir, "Viagem", songs, 0)
        idle()
        val last = playedWithNext(4).last()
        mediaPlayer.invokeCompletionListener()
        idle()
        val state = MusicPlayer.state
        assertFalse(state.playing)
        assertTrue(state.shuffle)
        assertEquals(0, state.index)
        assertTrue("A ordem nova não começa pela música que acabou de tocar", state.track != last)
    }

    @Test
    fun `lista uma pasta e as subpastas em ordem, sem as pastas do sistema`() {
        File(pendrive, "B/Sub").mkdirs()
        File(pendrive, "A").mkdir()
        File(pendrive, "LOST.DIR").mkdir()
        File(pendrive, "LOST.DIR/perdida.mp3").writeText("x")
        File(pendrive, "B/Sub/z.mp3").writeText("x")
        File(pendrive, "B/b.mp3").writeText("x")
        File(pendrive, "A/a.mp3").writeText("x")
        val names = MusicStorage.allTracks(pendrive).map { it.relativeTo(pendrive).path }
        assertEquals(
            listOf("01 - Abertura.mp3", "02 - Estrada.mp3", "10 - Final.mp3", "A/a.mp3", "B/b.mp3", "B/Sub/z.mp3"),
            names,
        )
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
