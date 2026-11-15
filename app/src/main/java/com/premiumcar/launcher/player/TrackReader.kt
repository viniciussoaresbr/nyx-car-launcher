package com.premiumcar.launcher.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.roundToInt

/**
 * Dados de uma música lidos das etiquetas do arquivo (ID3 e similares). [cover] é a capa
 * pequena (cartões, sessão de mídia e notificação); [largeCover], a do player grande (0.4.24).
 */
class TrackInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Int,
    val cover: Bitmap?,
    val largeCover: Bitmap? = cover,
)

/**
 * Lê título, artista, duração e capa de uma música. Só é usado para a música atual (nunca
 * para a lista inteira: seria lento num pendrive com centenas de arquivos).
 * Acessa o disco: chame fora da thread principal.
 */
object TrackReader {

    /** Lado máximo da capa pequena (cartão de 128 dp e player de 160 dp, a 160 dpi). */
    private const val COVER_SIZE = 256

    /** Lado máximo da capa do player grande (440 dp, a 160 dpi), 0.4.24. */
    private const val LARGE_COVER_SIZE = 440

    /** `null` se o arquivo não pôde ser lido (pendrive removido, sem permissão…). */
    fun read(file: File): TrackInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val covers = runCatching { retriever.embeddedPicture?.let(::decodeCovers) }.getOrNull()
            TrackInfo(
                title = retriever.text(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: AudioFiles.displayName(file.name),
                artist = retriever.text(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0,
                cover = covers?.first,
                largeCover = covers?.second,
            )
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.text(key: Int): String? = extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Uma leitura da capa, reduzida aos tamanhos exibidos, em 16 bits (pouca memória; a pequena
     * cabe na sessão de mídia e na notificação): (pequena, grande). Quando a imagem do arquivo
     * já é menor, as duas são a mesma.
     */
    internal fun decodeCovers(bytes: ByteArray): Pair<Bitmap, Bitmap>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LARGE_COVER_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val large = scaledDown(decoded, LARGE_COVER_SIZE)
        if (large !== decoded) decoded.recycle()
        return scaledDown(large, COVER_SIZE) to large
    }

    private fun scaledDown(bitmap: Bitmap, size: Int): Bitmap {
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
