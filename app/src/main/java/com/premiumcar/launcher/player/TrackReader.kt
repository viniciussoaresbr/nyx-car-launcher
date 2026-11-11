package com.premiumcar.launcher.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/** Dados de uma música lidos das etiquetas do arquivo (ID3 e similares). */
class TrackInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Int,
    val cover: Bitmap?,
)

/**
 * Lê título, artista, duração e capa de uma música. Só é usado para a música atual (nunca
 * para a lista inteira: seria lento num pendrive com centenas de arquivos).
 * Acessa o disco: chame fora da thread principal.
 */
object TrackReader {

    /** Lado máximo da capa guardada (cartão de 128 dp e player de 160 dp, a 160 dpi). */
    private const val COVER_SIZE = 256

    /** `null` se o arquivo não pôde ser lido (pendrive removido, sem permissão…). */
    fun read(file: File): TrackInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            TrackInfo(
                title = retriever.text(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: AudioFiles.displayName(file.name),
                artist = retriever.text(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0,
                cover = runCatching { retriever.embeddedPicture?.let(::decodeCover) }.getOrNull(),
            )
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.text(key: Int): String? = extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

    /** Reduz a capa ao tamanho exibido, em 16 bits: pouca memória e cabe na sessão de mídia. */
    private fun decodeCover(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = COVER_SIZE.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
