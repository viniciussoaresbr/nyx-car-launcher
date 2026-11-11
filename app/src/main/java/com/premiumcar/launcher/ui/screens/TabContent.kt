package com.premiumcar.launcher.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.CarApps
import com.premiumcar.launcher.ui.Destination

/** Atalho de uma aba: ícone, rótulo e o que acontece ao tocar. */
data class Shortcut(@DrawableRes val icon: Int, @StringRes val label: Int, val action: ShortcutAction)

sealed interface ShortcutAction {
    /** Abre um app ou uma tela do sistema. */
    data class Open(val target: AppTarget) : ShortcutAction

    /** Troca de aba dentro do launcher. */
    data class Go(val destination: Destination) : ShortcutAction

    /** Abre o Nyx Diagnóstico (ferramenta de medição, tarefa própria). */
    data object Diagnostics : ShortcutAction
}

/**
 * Atalhos fixos das abas para os apps e telas da central levantados pelo diagnóstico. A lista
 * completa de apps, os favoritos e os atalhos configuráveis chegam em etapas seguintes da Fase 4.
 */
object TabContent {

    private fun open(@DrawableRes icon: Int, @StringRes label: Int, target: AppTarget) =
        Shortcut(icon, label, ShortcutAction.Open(target))

    private val bluetooth = open(R.drawable.ic_bluetooth, R.string.shortcut_bluetooth, CarApps.BLUETOOTH)
    private val carplay = open(R.drawable.ic_carplay, R.string.shortcut_carplay, CarApps.CARPLAY)
    private val btMusic = open(R.drawable.ic_bluetooth, R.string.app_bt_music, CarApps.BT_MUSIC)
    private val radio = open(R.drawable.ic_radio, R.string.app_radio, CarApps.RADIO)
    private val youtube = open(R.drawable.ic_video, R.string.app_youtube, CarApps.YOUTUBE)
    private val video = open(R.drawable.ic_movie, R.string.app_video, CarApps.VIDEO_PLAYER)
    private val maps = open(R.drawable.ic_map, R.string.app_maps, CarApps.MAPS)
    private val camera = open(R.drawable.ic_camera, R.string.app_camera, CarApps.CAMERA)
    private val carInfo = open(R.drawable.ic_gauge, R.string.app_car_info, CarApps.CAR_INFO)
    private val equalizer = open(R.drawable.ic_equalizer, R.string.app_equalizer, CarApps.EQUALIZER)
    private val steering = open(R.drawable.ic_steering, R.string.app_steering, CarApps.STEERING_CONTROLS)
    private val files = open(R.drawable.ic_folder, R.string.app_files, CarApps.FILES)
    private val playStore = open(R.drawable.ic_store, R.string.app_play_store, CarApps.PLAY_STORE)

    /**
     * Música: outras fontes de áudio, na linha compacta sob o player. Sem o player USB do
     * fabricante desde a 0.4.2: o player próprio o substitui (pedido do usuário).
     */
    val musicSources = listOf(btMusic, radio)

    /** Aplicativos: os apps do carro, em grade de 4 colunas. A Play Store ocupa o lugar que sobrou (0.4.3). */
    val apps = listOf(
        listOf(radio, video, bluetooth, carplay),
        listOf(maps, youtube, camera, carInfo),
        listOf(equalizer, steering, files, playStore),
    )

    const val APPS_COLUMNS = 4

    val connectivity = listOf(
        bluetooth,
        open(R.drawable.ic_wifi, R.string.app_wifi, CarApps.WIFI_SETTINGS),
        carplay,
    )

    /** Configurações: inclui a escolha da tela inicial e o launcher original (recuperação). */
    val settings = listOf(
        listOf(
            open(R.drawable.ic_gear, R.string.settings_android, CarApps.ANDROID_SETTINGS),
            open(R.drawable.ic_volume, R.string.settings_sound, CarApps.SOUND_SETTINGS),
            open(R.drawable.ic_display, R.string.settings_display, CarApps.DISPLAY_SETTINGS),
            open(R.drawable.ic_clock, R.string.settings_date, CarApps.DATE_SETTINGS),
        ),
        listOf(
            open(R.drawable.ic_car, R.string.settings_vehicle, CarApps.VEHICLE_SETTINGS),
            open(R.drawable.ic_home, R.string.settings_home_choice, CarApps.HOME_SETTINGS),
            open(R.drawable.ic_restore, R.string.settings_original_launcher, CarApps.ORIGINAL_LAUNCHER),
            Shortcut(R.drawable.ic_diagnostics, R.string.settings_diagnostics, ShortcutAction.Diagnostics),
        ),
    )

    /** Todos os destinos de apps das abas, para conferir em segundo plano quais existem. */
    val allTargets: List<AppTarget> by lazy {
        (musicSources + apps.flatten() + connectivity + settings.flatten())
            .mapNotNull { (it.action as? ShortcutAction.Open)?.target }
            .distinct()
    }
}
