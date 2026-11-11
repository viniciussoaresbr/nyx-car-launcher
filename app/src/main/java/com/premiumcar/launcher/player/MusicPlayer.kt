package com.premiumcar.launcher.player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.system.Background
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Player das músicas do pendrive (etapa 0.4.0, ARCHITECTURE.md D17). Um só por processo;
 * todas as funções públicas devem ser chamadas na thread principal.
 *
 * - Reprodução com o `MediaPlayer` do Android; fila = músicas da pasta escolhida.
 * - Foco de áudio: pausa quando outro app assume o som (CarPlay, BT Música, ligação) e só
 *   retoma sozinho depois de interrupções curtas.
 * - Sessão de mídia padrão (o Android e outros apps enxergam o que está tocando).
 * - Guarda música, pasta e posição. Ao ligar a central, a última música aparece **pausada**:
 *   o player nunca começa a tocar sem um toque do usuário (decisão do usuário, 10/11/2026).
 * - Pendrive removido: a música para e a posição fica guardada.
 * - Enquanto toca, [PlaybackService] mantém o processo em primeiro plano; ao pausar, ele sai.
 */
@SuppressLint("StaticFieldLeak") // Guarda só o contexto da aplicação, que vive tanto quanto o processo.
object MusicPlayer {

    /** O que a interface mostra. */
    data class State(
        val hasTrack: Boolean,
        /** Tocando ou prestes a tocar (carregando). */
        val playing: Boolean,
        val title: String?,
        val artist: String?,
        val album: String?,
        val folderLabel: String?,
        val track: File?,
        /** Posição na fila, a partir de 0; -1 enquanto a fila não foi lida (depois de ligar). */
        val index: Int,
        val count: Int,
        /** 0 se ainda desconhecida. */
        val durationMs: Int,
        val cover: Bitmap?,
        /** Aviso para o usuário (pendrive removido, formato não suportado…). */
        val message: String?,
    )

    private enum class PauseReason(val log: String) {
        USER("pelo usuário"),
        FOCUS_LOST("outro app assumiu o som"),
        FOCUS_TRANSIENT("interrupção curta, retoma depois"),
    }

    private const val SAVE_EVERY_MS = 10_000L
    private const val RESTART_THRESHOLD_MS = 3_000
    private const val DUCK_VOLUME = 0.25f

    private var main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var app: Context? = null
    private var memory: PlayerMemory? = null
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private var queue: List<File> = emptyList()
    private var index = -1
    private var track: File? = null
    private var folder: File? = null
    private var folderLabel: String? = null
    private var info: TrackInfo? = null
    private var readingInfo: File? = null
    private var savedTitle: String? = null
    private var savedArtist: String? = null

    /** Onde a música atual começa quando for preparada (posição salva ou 0). */
    private var startPosition = 0
    private var prepared = false
    private var preparing = false
    private var playWhenReady = false
    private var resumeOnFocusGain = false
    private var consecutiveErrors = 0
    private var message: String? = null

    /**
     * Prepara o player e restaura a última música, **pausada**. Leve: só lê as preferências;
     * o pendrive só é acessado quando o usuário pede para tocar.
     */
    fun init(context: Context) {
        if (app != null) return
        val appContext = context.applicationContext
        app = appContext
        PlayerLog.attach(appContext)
        val store = PlayerMemory(appContext).also { memory = it }
        val saved = store.load()
        if (saved != null) {
            track = saved.track
            folder = saved.folder
            folderLabel = saved.folderLabel
            savedTitle = saved.title
            savedArtist = saved.artist
            startPosition = saved.positionMs
        }
        registerReceivers(appContext)
        val since = "sistema ligado há ${minutes(SystemClock.elapsedRealtime())}, acordado há ${minutes(SystemClock.uptimeMillis())}"
        PlayerLog.add(
            "Player iniciado ($since): " +
                if (saved != null) "última música pausada em ${formatTime(saved.positionMs)}, aguardando toque" else "nenhuma música salva",
        )
    }

    val state: State
        get() {
            val current = info
            return State(
                hasTrack = track != null,
                playing = playWhenReady,
                title = current?.title ?: savedTitle ?: track?.let { AudioFiles.displayName(it.name) },
                artist = if (current != null) current.artist else savedArtist,
                album = current?.album,
                folderLabel = folderLabel,
                track = track,
                index = if (queue.isEmpty()) -1 else index,
                count = queue.size,
                durationMs = current?.durationMs?.takeIf { it > 0 } ?: preparedDuration(),
                cover = current?.cover,
                message = message,
            )
        }

    val sessionToken: MediaSession.Token? get() = session?.sessionToken

    fun positionMs(): Int =
        if (prepared) runCatching { player?.currentPosition }.getOrNull() ?: startPosition else startPosition

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    // ---------- Comandos ----------

    /** O usuário tocou numa música da lista: a fila passa a ser a pasta dela. */
    fun playFrom(folder: File, label: String, tracks: List<File>, startIndex: Int) {
        if (tracks.isEmpty()) return
        this.folder = folder
        folderLabel = label
        queue = tracks
        consecutiveErrors = 0
        message = null
        load(startIndex.coerceIn(0, tracks.lastIndex), play = true, startAt = 0)
    }

    fun togglePlayPause() {
        if (playWhenReady) pause() else play()
    }

    /** Toca ou retoma. Depois de ligar a central, lê a pasta e volta ao ponto salvo. */
    fun play(): Boolean {
        if (track == null) return false
        message = null
        consecutiveErrors = 0
        withQueue {
            when {
                prepared -> startPlayback()
                preparing -> {
                    playWhenReady = true
                    changed()
                }
                else -> load(index, play = true, startAt = startPosition)
            }
        }
        return true
    }

    fun pause() = pause(PauseReason.USER)

    /** Próxima música; na última, volta à primeira. Mantém tocando ou pausado. */
    fun next() {
        if (track == null) return
        message = null
        withQueue { load(if (index + 1 < queue.size) index + 1 else 0, play = playWhenReady, startAt = 0) }
    }

    /** Volta ao início da música; se ela acabou de começar, vai para a anterior. */
    fun previous() {
        if (track == null) return
        message = null
        withQueue {
            if (index == 0 || positionMs() > RESTART_THRESHOLD_MS) {
                seekTo(0)
            } else {
                load(index - 1, play = playWhenReady, startAt = 0)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        if (prepared) runCatching { player?.seekTo(positionMs) } else startPosition = positionMs
        if (!prepared) memory?.savePosition(positionMs)
        updateSession()
        changed()
    }

    /**
     * Lê título, artista e capa da música salva (para a Home depois de ligar a central), se
     * ainda não lidos. Não toca nada. Sem pendrive ou sem permissão, mantém o que foi salvo.
     */
    fun loadSavedInfo() {
        val file = track ?: return
        if (info == null) readInfo(file)
    }

    // ---------- Reprodução ----------

    private fun load(target: Int, play: Boolean, startAt: Int) {
        val file = queue.getOrNull(target) ?: return
        val changedTrack = file != track
        index = target
        track = file
        if (changedTrack) {
            info = null
            savedTitle = null
            savedArtist = null
        }
        startPosition = startAt
        prepared = false
        preparing = true
        playWhenReady = play
        val mediaPlayer = player ?: createPlayer().also { player = it }
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.path)
            mediaPlayer.prepareAsync()
        } catch (e: Exception) { // IOException, IllegalArgumentException, IllegalStateException, SecurityException
            preparing = false
            trackFailed(file, e.javaClass.simpleName)
            return
        }
        val dir = folder ?: file.parentFile ?: file
        memory?.saveTrack(file, dir, folderLabel, startAt, info?.title ?: savedTitle, info?.artist ?: savedArtist)
        if (info == null) readInfo(file)
        updateSession()
        changed()
    }

    private fun createPlayer(): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(attributes)
        setOnPreparedListener { onPrepared() }
        setOnCompletionListener { onCompleted() }
        setOnErrorListener { _, what, extra ->
            prepared = false
            preparing = false
            track?.let { trackFailed(it, "erro $what/$extra") }
            true
        }
    }

    private fun onPrepared() {
        preparing = false
        prepared = true
        if (startPosition > 0) runCatching { player?.seekTo(startPosition) }
        if (playWhenReady) startPlayback() else changed()
        updateSession()
    }

    private fun startPlayback() {
        val mediaPlayer = player
        if (mediaPlayer == null || !prepared) {
            playWhenReady = true
            changed()
            return
        }
        if (!requestFocus()) {
            playWhenReady = false
            message = text(R.string.player_focus_denied)
            PlayerLog.add("Foco de áudio negado: outro app está usando o som")
            updateSession()
            changed()
            return
        }
        val started = runCatching {
            mediaPlayer.setVolume(1f, 1f)
            mediaPlayer.start()
        }.isSuccess
        if (!started) {
            track?.let { trackFailed(it, "falha ao iniciar") }
            return
        }
        playWhenReady = true
        consecutiveErrors = 0
        ensureSession()?.let { runCatching { it.isActive = true } }
        app?.let { PlaybackService.start(it) }
        main.removeCallbacks(saveTick)
        main.postDelayed(saveTick, SAVE_EVERY_MS)
        PlayerLog.add("Tocando: faixa ${index + 1} de ${queue.size} (${formatOf(track)}) a partir de ${formatTime(positionMs())}")
        updateSession()
        changed()
    }

    private fun pause(reason: PauseReason) {
        val wasPlaying = playWhenReady
        playWhenReady = false
        if (prepared) runCatching { player?.takeIf { it.isPlaying }?.pause() }
        main.removeCallbacks(saveTick)
        savePosition()
        when (reason) {
            PauseReason.FOCUS_TRANSIENT -> resumeOnFocusGain = wasPlaying
            else -> {
                resumeOnFocusGain = false
                abandonFocus()
            }
        }
        if (wasPlaying) PlayerLog.add("Pausado (${reason.log}) em ${formatTime(positionMs())}")
        updateSession()
        changed()
    }

    private fun onCompleted() {
        if (index + 1 < queue.size) {
            load(index + 1, play = true, startAt = 0)
        } else {
            // Sem "repetir" na primeira versão: volta à primeira música e pausa.
            PlayerLog.add("Fim da pasta: volta à primeira música, pausado")
            main.removeCallbacks(saveTick)
            abandonFocus()
            load(0, play = false, startAt = 0)
        }
    }

    /** Uma música não abriu: pula para a próxima ou, se o pendrive sumiu, para tudo. */
    private fun trackFailed(file: File, detail: String) {
        PlayerLog.add("Erro ao tocar faixa ${index + 1} de ${queue.size} (${formatOf(file)}): $detail")
        Background.executor.execute {
            val exists = file.exists()
            main.post {
                if (track != file) return@post
                if (!exists) {
                    stopForMissingStorage()
                    return@post
                }
                consecutiveErrors++
                message = text(R.string.player_cannot_play, AudioFiles.displayName(file.name))
                if (playWhenReady && queue.size > 1 && consecutiveErrors < queue.size) {
                    load((index + 1) % queue.size, play = true, startAt = 0)
                } else {
                    if (consecutiveErrors >= queue.size && queue.size > 1) message = text(R.string.player_nothing_playable)
                    playWhenReady = false
                    main.removeCallbacks(saveTick)
                    abandonFocus()
                    updateSession()
                    changed()
                }
            }
        }
    }

    /** Pendrive removido (ou arquivo sumiu): para, guarda a posição e avisa. */
    private fun stopForMissingStorage() {
        val position = positionMs()
        startPosition = position
        memory?.savePosition(position)
        runCatching { player?.reset() }
        prepared = false
        preparing = false
        playWhenReady = false
        resumeOnFocusGain = false
        queue = emptyList() // relida (e conferida) no próximo ▶
        main.removeCallbacks(saveTick)
        abandonFocus()
        message = text(R.string.player_storage_removed)
        PlayerLog.add("Pendrive removido: música parada em ${formatTime(position)}")
        updateSession()
        changed()
    }

    /** Garante a fila (depois de ligar a central ela ainda não foi lida) e então executa [action]. */
    private fun withQueue(action: () -> Unit) {
        if (queue.isNotEmpty()) {
            action()
            return
        }
        val file = track ?: return
        val dir = folder ?: file.parentFile ?: return
        Background.executor.execute {
            val exists = file.exists()
            val tracks = if (exists) runCatching { MusicStorage.tracks(dir) }.getOrDefault(emptyList()) else emptyList()
            main.post {
                if (track != file || queue.isNotEmpty()) return@post
                if (!exists) {
                    message = text(R.string.player_track_missing)
                    PlayerLog.add("Última música não encontrada (pendrive ausente ou trocado)")
                    changed()
                    return@post
                }
                queue = tracks.ifEmpty { listOf(file) }
                index = queue.indexOf(file).takeIf { it >= 0 } ?: 0
                action()
            }
        }
    }

    private fun readInfo(file: File) {
        if (readingInfo == file) return
        readingInfo = file
        Background.executor.execute {
            val read = TrackReader.read(file)
            main.post {
                if (readingInfo == file) readingInfo = null
                if (track != file || read == null) return@post
                info = read
                savedTitle = null
                savedArtist = null
                memory?.saveInfo(read.title, read.artist)
                updateSession()
                changed()
            }
        }
    }

    private val saveTick = object : Runnable {
        override fun run() {
            savePosition()
            if (playWhenReady) main.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    private fun savePosition(now: Boolean = false) {
        if (track == null) return
        val position = positionMs()
        if (!prepared) startPosition = position
        memory?.savePosition(position, now)
    }

    private fun preparedDuration(): Int =
        if (prepared) runCatching { player?.duration }.getOrNull()?.takeIf { it > 0 } ?: 0 else 0

    // ---------- Foco de áudio ----------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val audio = app?.getSystemService(AudioManager::class.java) ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
                .also { focusRequest = it }
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        val audio = app?.getSystemService(AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                PlayerLog.add("Foco de áudio perdido: outro app assumiu o som")
                if (playWhenReady) pause(PauseReason.FOCUS_LOST) else abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                PlayerLog.add("Foco de áudio perdido por instantes")
                if (playWhenReady) pause(PauseReason.FOCUS_TRANSIENT)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                PlayerLog.add("Foco de áudio: volume reduzido para outro som")
                runCatching { player?.setVolume(DUCK_VOLUME, DUCK_VOLUME) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                PlayerLog.add("Foco de áudio recuperado")
                runCatching { player?.setVolume(1f, 1f) }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    startPlayback()
                }
            }
        }
    }

    // ---------- Sessão de mídia ----------

    private fun ensureSession(): MediaSession? {
        session?.let { return it }
        val context = app ?: return null
        return runCatching {
            MediaSession(context, "NyxCarPlayer").apply {
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            play()
                        }

                        override fun onPause() = pause()

                        override fun onStop() = pause()

                        override fun onSkipToNext() = next()

                        override fun onSkipToPrevious() = previous()

                        override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
                    },
                    main,
                )
                @Suppress("DEPRECATION") // Obrigatório até o Android 7.1; ignorado depois.
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setSessionActivity(openPlayerIntent(context))
            }
        }.onFailure {
            PlayerLog.add("Sessão de mídia indisponível: ${it.javaClass.simpleName}")
        }.getOrNull().also { session = it }
    }

    private fun updateSession() {
        val mediaSession = session ?: return
        val current = state
        runCatching {
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, current.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, current.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, current.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, current.durationMs.toLong())
                    .apply { current.cover?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                    .build(),
            )
            mediaSession.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO,
                    )
                    .setState(
                        if (current.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        positionMs().toLong(),
                        if (current.playing) 1f else 0f,
                    )
                    .build(),
            )
        }
    }

    /** Toque na notificação ou na sessão: abre o launcher na aba Música. */
    fun openPlayerIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ---------- Pendrive e sistema ----------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val action = intent.action) {
                Intent.ACTION_SCREEN_OFF -> PlayerLog.add("Tela apagada" + if (playWhenReady) " (música tocando)" else "")
                Intent.ACTION_SCREEN_ON -> PlayerLog.add("Tela acesa")
                Intent.ACTION_SHUTDOWN -> {
                    PlayerLog.add("Sistema desligando")
                    savePosition(now = true)
                }
                else -> {
                    // O caminho do volume tem o número de série do pendrive: não vai para o diário.
                    PlayerLog.add("Armazenamento: ${action?.substringAfterLast('.')?.lowercase(Locale.ROOT)}")
                    val mount = intent.data?.path ?: return
                    val current = track ?: return
                    val removed = action != Intent.ACTION_MEDIA_MOUNTED
                    val active = prepared || preparing || queue.isNotEmpty()
                    if (removed && active && MusicVolume(File(mount), "").contains(current)) stopForMissingStorage()
                }
            }
        }
    }

    private fun registerReceivers(context: Context) {
        val storage = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        val system = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        listOf(storage, system).forEach { filter -> register(context, receiver, filter) }
    }

    // ---------- Auxiliares ----------

    /** Só avisos do sistema (pendrive, tela, desligamento): nenhum outro app precisa alcançar o receptor. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Antes do Android 13 a opção não existe.
    internal fun register(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
    }

    private fun changed() {
        listeners.forEach { it() }
    }

    private fun text(@StringRes id: Int, vararg args: Any): String? = app?.getString(id, *args)

    private fun formatOf(file: File?): String = file?.let { ".${AudioFiles.extension(it.name)}" } ?: "?"

    private fun minutes(millis: Long): String = "${millis / 60_000} min"

    /** "3:07" ou "1:02:03". */
    fun formatTime(millis: Int): String {
        val total = (millis.coerceAtLeast(0) / 1000)
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    internal val mediaPlayerForTests: MediaPlayer? get() = player

    /** Testes: volta ao estado de um processo novo. */
    internal fun resetForTests() {
        main.removeCallbacksAndMessages(null)
        main = Handler(Looper.getMainLooper())
        app?.let { context -> runCatching { context.unregisterReceiver(receiver) } }
        runCatching { player?.release() }
        runCatching { session?.release() }
        listeners.clear()
        app = null
        memory = null
        player = null
        session = null
        focusRequest = null
        hasFocus = false
        queue = emptyList()
        index = -1
        track = null
        folder = null
        folderLabel = null
        info = null
        readingInfo = null
        savedTitle = null
        savedArtist = null
        startPosition = 0
        prepared = false
        preparing = false
        playWhenReady = false
        resumeOnFocusGain = false
        consecutiveErrors = 0
        message = null
        PlayerLog.resetForTests()
    }
}
