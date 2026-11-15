package com.premiumcar.launcher.player

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Última música, pasta e posição, em `SharedPreferences` (nativo, sem biblioteca). Gravação
 * assíncrona (`apply`), exceto no desligamento do sistema (`commit`). Desde a 0.4.24, também o
 * modo da fila ([Mode]): aleatório, subpastas e o sorteio, para a ordem continuar depois de
 * ligar a central.
 */
class PlayerMemory(context: Context) {

    class Saved(
        val track: File,
        val folder: File,
        val folderLabel: String?,
        val positionMs: Int,
        val title: String?,
        val artist: String?,
    )

    /**
     * Modo da fila (0.4.24): [shuffle] ligado; fila com as [subfolders] (aleatório da lista de
     * pastas); [seed] e [anchor] do último sorteio (ver [PlayOrder]).
     */
    class Mode(val shuffle: Boolean, val subfolders: Boolean, val seed: Long, val anchor: File?)

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): Saved? {
        val track = prefs.getString(KEY_TRACK, null)?.let(::File) ?: return null
        return Saved(
            track = track,
            folder = prefs.getString(KEY_FOLDER, null)?.let(::File) ?: track.parentFile ?: track,
            folderLabel = prefs.getString(KEY_FOLDER_LABEL, null),
            positionMs = prefs.getInt(KEY_POSITION, 0),
            title = prefs.getString(KEY_TITLE, null),
            artist = prefs.getString(KEY_ARTIST, null),
        )
    }

    fun saveTrack(track: File, folder: File, folderLabel: String?, positionMs: Int, title: String?, artist: String?) {
        prefs.edit {
            putString(KEY_TRACK, track.path)
            putString(KEY_FOLDER, folder.path)
            putString(KEY_FOLDER_LABEL, folderLabel)
            putInt(KEY_POSITION, positionMs)
            putString(KEY_TITLE, title)
            putString(KEY_ARTIST, artist)
        }
    }

    fun saveInfo(title: String?, artist: String?) {
        prefs.edit { putString(KEY_TITLE, title).putString(KEY_ARTIST, artist) }
    }

    @SuppressLint("ApplySharedPref") // commit só no desligamento: o processo pode acabar logo depois.
    fun savePosition(positionMs: Int, now: Boolean = false) {
        val editor = prefs.edit().putInt(KEY_POSITION, positionMs)
        if (now) editor.commit() else editor.apply()
    }

    fun loadMode(): Mode = Mode(
        shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
        subfolders = prefs.getBoolean(KEY_SUBFOLDERS, false),
        seed = prefs.getLong(KEY_SEED, 0L),
        anchor = prefs.getString(KEY_ANCHOR, null)?.let(::File),
    )

    fun saveMode(mode: Mode) {
        prefs.edit {
            putBoolean(KEY_SHUFFLE, mode.shuffle)
            putBoolean(KEY_SUBFOLDERS, mode.subfolders)
            putLong(KEY_SEED, mode.seed)
            putString(KEY_ANCHOR, mode.anchor?.path)
        }
    }

    /** Usado pela aba Música: a pergunta de permissão já foi feita uma vez? */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    private companion object {
        const val FILE_NAME = "player"
        const val KEY_TRACK = "track"
        const val KEY_FOLDER = "folder"
        const val KEY_FOLDER_LABEL = "folder_label"
        const val KEY_POSITION = "position_ms"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_PERMISSION_ASKED = "storage_permission_asked"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_SUBFOLDERS = "subfolders"
        const val KEY_SEED = "shuffle_seed"
        const val KEY_ANCHOR = "shuffle_anchor"
    }
}
