package com.premiumcar.launcher.player

import android.annotation.SuppressLint
import android.content.Context
import java.io.File

/**
 * Última música, pasta e posição, em `SharedPreferences` (nativo, sem biblioteca). Gravação
 * assíncrona (`apply`), exceto no desligamento do sistema (`commit`).
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
        prefs.edit()
            .putString(KEY_TRACK, track.path)
            .putString(KEY_FOLDER, folder.path)
            .putString(KEY_FOLDER_LABEL, folderLabel)
            .putInt(KEY_POSITION, positionMs)
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .apply()
    }

    fun saveInfo(title: String?, artist: String?) {
        prefs.edit().putString(KEY_TITLE, title).putString(KEY_ARTIST, artist).apply()
    }

    @SuppressLint("ApplySharedPref") // commit só no desligamento: o processo pode acabar logo depois.
    fun savePosition(positionMs: Int, now: Boolean = false) {
        val editor = prefs.edit().putInt(KEY_POSITION, positionMs)
        if (now) editor.commit() else editor.apply()
    }

    /** Usado pela aba Música: a pergunta de permissão já foi feita uma vez? */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    private companion object {
        const val FILE_NAME = "player"
        const val KEY_TRACK = "track"
        const val KEY_FOLDER = "folder"
        const val KEY_FOLDER_LABEL = "folder_label"
        const val KEY_POSITION = "position_ms"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_PERMISSION_ASKED = "storage_permission_asked"
    }
}
