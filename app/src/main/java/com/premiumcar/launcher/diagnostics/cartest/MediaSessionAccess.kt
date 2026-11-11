package com.premiumcar.launcher.diagnostics.cartest

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Componente exigido pelo Android para listar as sessões de mídia de outros apps
 * (`MediaSessionManager.getActiveSessions`). Só funciona se o usuário conceder
 * "Acesso a notificações" ao Nyx Car; sem isso, fica inerte.
 *
 * Não lê nem guarda notificações: nenhum método é sobrescrito.
 */
class MediaAccessListenerService : NotificationListenerService()

/** Sessões de mídia ativas (player USB, Bluetooth, CarPlay…), para o teste de sincronização. */
object MediaSessionAccess {

    fun component(context: Context) = ComponentName(context, MediaAccessListenerService::class.java)

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Sessões ativas, ou `null` se o acesso não foi concedido. */
    fun controllers(context: Context): List<MediaController>? {
        if (!isGranted(context)) return null
        return try {
            context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(component(context))
        } catch (e: SecurityException) {
            null
        }
    }

    /** Resumo das sessões ("nenhuma" se não há), ou `null` sem acesso. */
    fun summary(context: Context): String? = controllers(context)?.let { list ->
        if (list.isEmpty()) "nenhuma" else list.joinToString(" | ") { describe(it) }
    }

    fun describe(controller: MediaController): String {
        val playback = controller.playbackState
        val state = when (playback?.state) {
            null -> "sem estado"
            PlaybackState.STATE_PLAYING -> "tocando"
            PlaybackState.STATE_PAUSED -> "pausado"
            PlaybackState.STATE_STOPPED -> "parado"
            PlaybackState.STATE_BUFFERING -> "carregando"
            PlaybackState.STATE_NONE -> "nenhum"
            else -> "estado ${playback.state}"
        }
        // Só registra QUAIS informações existem, não o conteúdo (nome das músicas).
        val metadata = controller.metadata
        val fields = listOfNotNull(
            "título".takeIf { metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null },
            "artista".takeIf { metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) != null },
            "duração".takeIf { (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) > 0L },
            "capa".takeIf {
                metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                    metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) != null
            },
        )
        val actions = playback?.actions ?: 0L
        val supported = listOfNotNull(
            "play".takeIf { actions and PlaybackState.ACTION_PLAY != 0L },
            "pause".takeIf { actions and PlaybackState.ACTION_PAUSE != 0L },
            "play/pause".takeIf { actions and PlaybackState.ACTION_PLAY_PAUSE != 0L },
            "próxima".takeIf { actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L },
            "anterior".takeIf { actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L },
        )
        return "${controller.packageName} ($state; dados: ${fields.joinToString("+").ifEmpty { "nenhum" }}; " +
            "comandos: ${supported.joinToString(", ").ifEmpty { "nenhum" }})"
    }

    /** Abre a tela padrão "Acesso a notificações". Abrir não muda nada. */
    fun openSettings(context: Context): String = try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Na lista, ative \"Nyx Car\" só durante o teste. Para desfazer, desative na mesma tela."
    } catch (e: ActivityNotFoundException) {
        "Este firmware não permite abrir a tela de acesso a notificações."
    }
}
