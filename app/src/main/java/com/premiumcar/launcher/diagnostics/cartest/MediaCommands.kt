package com.premiumcar.launcher.diagnostics.cartest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.premiumcar.launcher.launcher.CarApps

/**
 * Comandos de mídia de teste para o player USB, por três caminhos padrão do Android:
 * - **sistema:** tecla de mídia entregue à sessão ativa (o que a Home usa hoje);
 * - **receptor do player:** a mesma tecla enviada direto ao receptor de botões de mídia
 *   que o player declara (se declarar);
 * - **sessão do player:** comando à sessão de mídia do player (exige acesso às sessões).
 *
 * Cada envio fica na linha do tempo, com "música tocando" antes e 1,5 s depois.
 */
object MediaCommands {

    val PLAYER_PACKAGE = CarApps.USB_MUSIC.packageName

    enum class Command(val label: String, val keyCode: Int) {
        PREVIOUS("Anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        PLAY_PAUSE("Play/Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        NEXT("Próxima", KeyEvent.KEYCODE_MEDIA_NEXT),
    }

    enum class Route(val label: String) {
        SYSTEM("sistema"),
        PLAYER_RECEIVER("receptor do player"),
        PLAYER_SESSION("sessão do player"),
    }

    private val main = Handler(Looper.getMainLooper())

    /** Receptores de botões de mídia declarados pelo player USB. */
    fun playerReceivers(context: Context): List<ComponentName> =
        context.packageManager
            .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(PLAYER_PACKAGE), PackageManager.GET_META_DATA)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    fun send(context: Context, route: Route, command: Command) {
        val app = context.applicationContext
        val before = musicActive(app)
        val result = runCatching {
            when (route) {
                Route.SYSTEM -> {
                    keyEvents(command).forEach { app.getSystemService(AudioManager::class.java)?.dispatchMediaKeyEvent(it) }
                    "enviado"
                }
                Route.PLAYER_RECEIVER -> {
                    val receivers = playerReceivers(app)
                    if (receivers.isEmpty()) {
                        "o player não declara receptor de botões de mídia"
                    } else {
                        receivers.forEach { component ->
                            keyEvents(command).forEach { event ->
                                app.sendBroadcast(
                                    Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(component).putExtra(Intent.EXTRA_KEY_EVENT, event),
                                )
                            }
                        }
                        "enviado a ${receivers.joinToString { it.shortClassName }}"
                    }
                }
                Route.PLAYER_SESSION -> {
                    val controllers = MediaSessionAccess.controllers(app)
                    val player = controllers?.firstOrNull { it.packageName == PLAYER_PACKAGE }
                    when {
                        controllers == null -> "sem acesso às sessões de mídia (opcional)"
                        player == null -> "o player não tem sessão de mídia ativa"
                        else -> {
                            val controls = player.transportControls
                            when (command) {
                                Command.PREVIOUS -> controls.skipToPrevious()
                                Command.NEXT -> controls.skipToNext()
                                Command.PLAY_PAUSE ->
                                    if (player.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                            }
                            "enviado à sessão"
                        }
                    }
                }
            }
        }.getOrElse { "falhou: ${it.javaClass.simpleName}" }
        CarTestLog.add("Comando ${command.label} via ${route.label}: $result · música antes: ${yesNo(before)}")
        main.postDelayed({
            CarTestLog.add("   ↳ 1,5 s depois (${command.label}, ${route.label}): música tocando = ${yesNo(musicActive(app))}")
        }, 1_500L)
    }

    private fun keyEvents(command: Command): List<KeyEvent> {
        val now = SystemClock.uptimeMillis()
        return listOf(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, command.keyCode, 0),
            KeyEvent(now, now, KeyEvent.ACTION_UP, command.keyCode, 0),
        )
    }

    private fun musicActive(context: Context) = context.getSystemService(AudioManager::class.java)?.isMusicActive == true

    private fun yesNo(value: Boolean) = if (value) "SIM" else "não"
}
