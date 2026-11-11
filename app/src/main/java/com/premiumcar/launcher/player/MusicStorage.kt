package com.premiumcar.launcher.player

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import com.premiumcar.launcher.R
import java.io.File

/** Um pendrive (ou cartão) montado e legível. */
data class MusicVolume(val root: File, val label: String) {
    fun contains(file: File): Boolean = file.path == root.path || file.path.startsWith(root.path.trimEnd('/') + "/")
}

/** Conteúdo de uma pasta: subpastas e músicas, já em ordem natural. */
class FolderListing(val folder: File, val folders: List<File>, val tracks: List<File>)

/**
 * Leitura do pendrive por pastas, direto no sistema de arquivos (somente leitura). Não usa a
 * biblioteca de mídia do Android: ela pode demorar a indexar o pendrive ou nem indexá-lo.
 *
 * Todas as funções acessam o disco: chame-as fora da thread principal.
 */
object MusicStorage {

    /** Para testes: substitui a busca de pendrives. */
    @Volatile
    var volumeSource: ((Context) -> List<MusicVolume>)? = null

    /** Como os pendrives foram encontrados na última busca (vai para o Nyx Diagnóstico). */
    @Volatile
    var lastMethod: String = "nenhuma busca ainda"
        private set

    private val READABLE = setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)

    /**
     * Pendrives montados. Primeiro pela lista de volumes do Android; se ela não trouxer nenhum,
     * pelas pastas de `/storage` (alternativa caso a função interna do Android 8.1 falhe).
     */
    fun volumes(context: Context): List<MusicVolume> {
        volumeSource?.let { return it(context) }
        val fromSystem = runCatching { fromStorageManager(context) }.getOrDefault(emptyList())
        if (fromSystem.isNotEmpty()) {
            lastMethod = "lista de volumes do Android"
            return fromSystem
        }
        val fromFolder = runCatching { fromStorageFolder(context) }.getOrDefault(emptyList())
        lastMethod = if (fromFolder.isNotEmpty()) "pastas de /storage" else "nenhum encontrado"
        return fromFolder
    }

    private fun fromStorageManager(context: Context): List<MusicVolume> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) storageVolumes(context) else emptyList()

    @RequiresApi(Build.VERSION_CODES.N)
    private fun storageVolumes(context: Context): List<MusicVolume> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        val fallback = context.getString(R.string.player_volume_default)
        return manager.storageVolumes
            .filter { it.isRemovable && it.state in READABLE }
            .mapNotNull { volume ->
                val directory = directoryOf(volume)?.takeIf { it.canRead() } ?: return@mapNotNull null
                MusicVolume(directory, volume.getDescription(context)?.takeIf { it.isNotBlank() } ?: fallback)
            }
            .distinctBy { it.root.path }
    }

    /**
     * Pasta do volume. No Android 11+ há função pública; antes disso, só a interna `getPath()`,
     * que existe até o Android 10 (a central roda o 8.1, sem bloqueio de funções internas).
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun directoryOf(volume: StorageVolume): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            runCatching { (StorageVolume::class.java.getMethod("getPath").invoke(volume) as? String)?.let(::File) }.getOrNull()
        }

    private fun fromStorageFolder(context: Context): List<MusicVolume> {
        val ignored = setOf("emulated", "self", "sdcard", "sdcard0")
        val found = File("/storage").listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in ignored && it.canRead() }
            .sortedBy { it.name }
        val label = context.getString(R.string.player_volume_default)
        return found.mapIndexed { i, dir -> MusicVolume(dir, if (found.size == 1) label else "$label ${i + 1}") }
    }

    /**
     * Lista uma pasta. Arquivos com extensão de música entram sem consulta extra ao disco
     * (pendrives são lentos); só os demais são conferidos como pasta.
     */
    fun list(folder: File): FolderListing {
        val folders = ArrayList<File>()
        val tracks = ArrayList<File>()
        folder.listFiles().orEmpty().forEach { child ->
            val name = child.name
            when {
                AudioFiles.isAudio(name) -> tracks += child
                AudioFiles.isBrowsableFolder(name) && child.isDirectory -> folders += child
            }
        }
        return FolderListing(folder, AudioFiles.sorted(folders), AudioFiles.sorted(tracks))
    }

    fun tracks(folder: File): List<File> = list(folder).tracks
}
