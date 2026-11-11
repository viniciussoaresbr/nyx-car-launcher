package com.premiumcar.launcher.ui.music

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.player.FolderListing
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.player.MusicStorage
import com.premiumcar.launcher.player.MusicVolume
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.player.PlayerMemory
import com.premiumcar.launcher.player.StoragePermission
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.ui.screens.Shortcut
import java.io.File

/**
 * Aba Música: pastas do pendrive à esquerda; música atual, progresso e controles à direita;
 * atalhos para BT Música, Rádio e o player original embaixo.
 *
 * Só trabalha enquanto a aba está visível ([setActive]): escuta pendrive inserido/removido e
 * atualiza o progresso uma vez por segundo (só com música tocando). Leituras do pendrive em
 * segundo plano.
 */
class MusicScreen(
    private val activity: Activity,
    sources: List<Shortcut>,
    onSource: (View, Shortcut) -> Unit,
) {
    val view: View = LayoutInflater.from(activity).inflate(R.layout.screen_music, null, false)

    private val memory = PlayerMemory(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = BrowserAdapter(activity)

    private val upButton: ImageButton = view.findViewById(R.id.music_up)
    private val pathView: TextView = view.findViewById(R.id.music_path)
    private val list: ListView = view.findViewById(R.id.music_list)
    private val emptyBox: View = view.findViewById(R.id.music_empty)
    private val emptyIcon: ImageView = view.findViewById(R.id.music_empty_icon)
    private val emptyText: TextView = view.findViewById(R.id.music_empty_text)
    private val emptyAction: Button = view.findViewById(R.id.music_empty_action)

    private val cover: ImageView = view.findViewById(R.id.player_cover)
    private val placeholder: View = view.findViewById(R.id.player_placeholder)
    private val title: TextView = view.findViewById(R.id.player_title)
    private val artist: TextView = view.findViewById(R.id.player_artist)
    private val details: TextView = view.findViewById(R.id.player_details)
    private val message: TextView = view.findViewById(R.id.player_message)
    private val progress: ProgressBar = view.findViewById(R.id.player_progress)
    private val elapsed: TextView = view.findViewById(R.id.player_elapsed)
    private val total: TextView = view.findViewById(R.id.player_total)
    private val previous: ImageButton = view.findViewById(R.id.player_previous)
    private val play: ImageButton = view.findViewById(R.id.player_play)
    private val next: ImageButton = view.findViewById(R.id.player_next)

    private var volumes: List<MusicVolume> = emptyList()

    /** Pasta aberta; `null` = lista de pendrives. */
    private var location: File? = null
    private var listing: FolderListing? = null
    private var searched = false
    private var active = false
    private var destroyed = false
    private var lastState: MusicPlayer.State = MusicPlayer.state
    private var storageReceiver: BroadcastReceiver? = null

    /** Atualiza o progresso uma vez por segundo, só com a aba visível e música tocando. */
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (active && lastState.playing) handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onItemClick(adapter.getItem(position)) }
        upButton.setOnClickListener { goUp() }
        previous.setOnClickListener { MusicPlayer.previous() }
        play.setOnClickListener { MusicPlayer.togglePlayPause() }
        next.setOnClickListener { MusicPlayer.next() }
        view.findViewById<View>(R.id.player_artwork).clipToOutline = true

        listOf(R.id.source_1, R.id.source_2, R.id.source_3).forEachIndexed { index, id ->
            val button = view.findViewById<View>(id)
            val shortcut = sources.getOrNull(index)
            if (shortcut == null) {
                button.visibility = View.GONE // lugar sem atalho: os outros dividem a linha
                return@forEachIndexed
            }
            button.findViewById<ImageView>(R.id.source_icon).setImageResource(shortcut.icon)
            button.findViewById<TextView>(R.id.source_label).setText(shortcut.label)
            button.contentDescription = activity.getString(shortcut.label)
            onSource(button, shortcut)
        }
        render(MusicPlayer.state)
    }

    /** Aba visível com a janela em primeiro plano. */
    fun setActive(value: Boolean) {
        if (value == active || destroyed) return
        active = value
        if (value) {
            registerStorageReceiver()
            refresh()
            restartTicker()
        } else {
            unregisterStorageReceiver()
            handler.removeCallbacks(tick)
        }
    }

    fun destroy() {
        setActive(false)
        destroyed = true
        handler.removeCallbacksAndMessages(null)
    }

    fun onPermissionResult(granted: Boolean) {
        PlayerLog.add("Permissão de leitura do armazenamento: ${if (granted) "concedida" else "negada"}")
        if (granted) MusicPlayer.loadSavedInfo()
        if (active) refresh()
    }

    // ---------- Música atual ----------

    fun render(state: MusicPlayer.State) {
        lastState = state
        title.text = state.title ?: activity.getString(R.string.player_nothing_title)
        val artistText = state.artist ?: if (state.hasTrack) null else activity.getString(R.string.player_nothing_hint)
        artist.text = artistText
        artist.visibility = if (artistText.isNullOrEmpty()) View.GONE else View.VISIBLE
        val label = state.folderLabel ?: activity.getString(R.string.player_volume_default)
        details.text = when {
            !state.hasTrack -> null
            state.count > 0 -> activity.getString(R.string.player_position, label, state.index + 1, state.count)
            else -> label
        }
        details.visibility = if (details.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (state.cover != null) {
            cover.setImageBitmap(state.cover)
            cover.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } else {
            cover.setImageDrawable(null)
            cover.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
        }
        message.text = state.message
        message.visibility = if (state.message == null) View.GONE else View.VISIBLE
        play.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        play.contentDescription = activity.getString(if (state.playing) R.string.music_pause else R.string.music_play)
        listOf(previous, play, next).forEach { it.isEnabled = state.hasTrack }
        adapter.current = state.track
        updateProgress()
        restartTicker()
    }

    private fun updateProgress() {
        val state = lastState
        val position = if (state.hasTrack) MusicPlayer.positionMs() else 0
        val duration = state.durationMs
        progress.max = duration.coerceAtLeast(1)
        progress.progress = position.coerceIn(0, duration.coerceAtLeast(0))
        elapsed.text = if (state.hasTrack) MusicPlayer.formatTime(position) else null
        total.text = if (duration > 0) MusicPlayer.formatTime(duration) else null
    }

    private fun restartTicker() {
        handler.removeCallbacks(tick)
        if (active && lastState.playing) handler.postDelayed(tick, TICK_MS)
    }

    // ---------- Pastas ----------

    private fun refresh() {
        if (!StoragePermission.isGranted(activity)) {
            showPermissionRequest()
            return
        }
        if (!searched) showEmpty(R.drawable.ic_usb, R.string.player_loading)
        val app = activity.applicationContext
        Background.executor.execute {
            val found = runCatching { MusicStorage.volumes(app) }.getOrDefault(emptyList())
            handler.post { if (!destroyed) onVolumes(found) }
        }
    }

    private fun onVolumes(found: List<MusicVolume>) {
        val first = !searched
        searched = true
        volumes = found
        if (found.isEmpty()) {
            location = null
            listing = null
            updateHeader()
            showEmpty(R.drawable.ic_usb, R.string.player_no_volume)
            return
        }
        // Na primeira visita, abre a pasta da música atual; depois, mantém onde o usuário estava.
        val trackFolder = lastState.track?.parentFile?.takeIf { folder -> found.any { it.contains(folder) } }
        val current = location?.takeIf { folder -> found.any { it.contains(folder) } }
        open(
            when {
                first && trackFolder != null -> trackFolder
                current != null -> current
                found.size == 1 -> found.single().root
                else -> null
            },
        )
    }

    private fun open(target: File?) {
        location = target
        updateHeader()
        if (target == null) {
            listing = null
            showItems(volumes.map { BrowserItem.Volume(it) })
            return
        }
        Background.executor.execute {
            val result = runCatching { MusicStorage.list(target) }.getOrNull()
            handler.post {
                if (destroyed || location != target) return@post
                listing = result
                val items = result?.let { listed ->
                    listed.folders.map { BrowserItem.Folder(it) } + listed.tracks.mapIndexed { i, file -> BrowserItem.Track(file, i) }
                }.orEmpty()
                if (items.isEmpty()) {
                    showEmpty(R.drawable.ic_music, R.string.player_empty_folder)
                } else {
                    showItems(items)
                    val playing = items.indexOfFirst { it is BrowserItem.Track && it.file == lastState.track }
                    if (playing > 0) list.setSelection((playing - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun onItemClick(item: BrowserItem) {
        when (item) {
            is BrowserItem.Volume -> open(item.volume.root)
            is BrowserItem.Folder -> open(item.file)
            is BrowserItem.Track -> {
                val folder = listing ?: return
                MusicPlayer.playFrom(folder.folder, labelFor(folder.folder), folder.tracks, item.index)
            }
        }
    }

    private fun goUp() {
        val current = location ?: return
        val volume = volumeOf(current)
        open(if (volume == null || current.path == volume.root.path) null else current.parentFile)
    }

    private fun volumeOf(file: File): MusicVolume? = volumes.firstOrNull { it.contains(file) }

    /** Nome da pasta para o player: a raiz do pendrive aparece como "Pendrive". */
    private fun labelFor(folder: File): String {
        val volume = volumeOf(folder)
        return if (volume != null && volume.root.path == folder.path) volume.label else folder.name
    }

    private fun updateHeader() {
        val current = location
        val volume = current?.let(::volumeOf)
        pathView.text = when {
            current != null && volume != null -> {
                val relative = current.path.removePrefix(volume.root.path).split('/').filter { it.isNotEmpty() }
                (listOf(volume.label) + relative).joinToString(" › ")
            }
            volumes.size > 1 -> activity.getString(R.string.player_volumes_title)
            else -> activity.getString(R.string.player_volume_default)
        }
        val canGoUp = current != null && volume != null && (current.path != volume.root.path || volumes.size > 1)
        upButton.visibility = if (canGoUp) View.VISIBLE else View.GONE
    }

    private fun showItems(items: List<BrowserItem>) {
        adapter.items = items
        list.visibility = View.VISIBLE
        emptyBox.visibility = View.GONE
    }

    private fun showEmpty(@DrawableRes icon: Int, @StringRes text: Int, @StringRes action: Int? = null, onAction: (() -> Unit)? = null) {
        adapter.items = emptyList()
        list.visibility = View.GONE
        emptyBox.visibility = View.VISIBLE
        emptyIcon.setImageResource(icon)
        emptyText.setText(text)
        if (action != null && onAction != null) {
            emptyAction.setText(action)
            emptyAction.setOnClickListener { onAction() }
            emptyAction.visibility = View.VISIBLE
        } else {
            emptyAction.visibility = View.GONE
        }
    }

    // ---------- Permissão ----------

    private fun showPermissionRequest() {
        location = null
        volumes = emptyList()
        updateHeader()
        val blocked = memory.permissionAsked && !activity.shouldShowRequestPermissionRationale(StoragePermission.name)
        if (blocked) {
            showEmpty(R.drawable.ic_folder, R.string.player_permission_denied, R.string.player_permission_settings) { openAppSettings() }
        } else {
            showEmpty(R.drawable.ic_folder, R.string.player_permission_needed, R.string.player_permission_action) {
                memory.permissionAsked = true
                activity.requestPermissions(arrayOf(StoragePermission.name), REQUEST_STORAGE)
            }
        }
    }

    private fun openAppSettings() {
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ---------- Pendrive inserido ou removido ----------

    private fun registerStorageReceiver() {
        if (storageReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        MusicPlayer.register(activity, receiver, filter)
        storageReceiver = receiver
    }

    private fun unregisterStorageReceiver() {
        storageReceiver?.let { runCatching { activity.unregisterReceiver(it) } }
        storageReceiver = null
    }

    companion object {
        const val REQUEST_STORAGE = 41
        private const val TICK_MS = 1_000L
    }
}
