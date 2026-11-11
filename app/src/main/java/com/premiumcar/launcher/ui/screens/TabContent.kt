package com.premiumcar.launcher.ui.screens

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.launcher.AppLook
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
 * App conhecido do Nyx Car (0.4.4): [id] estável (guardado nos favoritos), o atalho (ícone,
 * nome e o que abre) e o [target] usado para reconhecê-lo na lista "Todos".
 */
class KnownApp(val id: String, val shortcut: Shortcut, val target: AppTarget.App)

/**
 * Atalhos das abas para os apps e telas da central levantados pelo diagnóstico, e os apps
 * conhecidos que podem ser favoritos da aba Aplicativos (0.4.4).
 */
object TabContent {

    private fun open(@DrawableRes icon: Int, @StringRes label: Int, target: AppTarget) =
        Shortcut(icon, label, ShortcutAction.Open(target))

    private fun known(id: String, @DrawableRes icon: Int, @StringRes label: Int, target: AppTarget.App) =
        KnownApp(id, open(icon, label, target), target)

    /**
     * Apps conhecidos, com ícone de traço e nome do Nyx Car. A ordem importa: na lista "Todos",
     * cada app da central fica com o primeiro que o reconhecer. Os ids não mudam entre versões
     * (os favoritos guardados dependem deles).
     */
    val knownApps: List<KnownApp> = listOf(
        known("radio", R.drawable.ic_radio, R.string.app_radio, CarApps.RADIO),
        known("video", R.drawable.ic_movie, R.string.app_video, CarApps.VIDEO_PLAYER),
        known("bluetooth", R.drawable.ic_bluetooth, R.string.shortcut_bluetooth, CarApps.BLUETOOTH),
        known("carplay", R.drawable.ic_carplay, R.string.shortcut_carplay, CarApps.CARPLAY),
        known("maps", R.drawable.ic_map, R.string.app_maps, CarApps.MAPS),
        known("youtube", R.drawable.ic_video, R.string.app_youtube, CarApps.YOUTUBE),
        known("camera", R.drawable.ic_camera, R.string.app_camera, CarApps.CAMERA),
        known("car_info", R.drawable.ic_gauge, R.string.app_car_info, CarApps.CAR_INFO),
        known("equalizer", R.drawable.ic_equalizer, R.string.app_equalizer, CarApps.EQUALIZER),
        known("steering", R.drawable.ic_steering, R.string.app_steering, CarApps.STEERING_CONTROLS),
        known("files", R.drawable.ic_folder, R.string.app_files, CarApps.FILES),
        known("play_store", R.drawable.ic_store, R.string.app_play_store, CarApps.PLAY_STORE),
        known("bt_music", R.drawable.ic_bluetooth, R.string.app_bt_music, CarApps.BT_MUSIC),
        known("android_settings", R.drawable.ic_gear, R.string.settings_android, CarApps.ANDROID_SETTINGS_APP),
        known("vehicle", R.drawable.ic_car, R.string.settings_vehicle, CarApps.VEHICLE_SETTINGS),
        // O próprio Nyx Diagnóstico abre direto pela tarefa dele, como na aba Configurações.
        KnownApp(
            "diagnostics",
            Shortcut(R.drawable.ic_diagnostics, R.string.settings_diagnostics, ShortcutAction.Diagnostics),
            CarApps.DIAGNOSTICS,
        ),
        known("chrome", R.drawable.ic_browser, R.string.app_chrome, CarApps.CHROME),
        known("google", R.drawable.ic_search, R.string.app_google, CarApps.GOOGLE),
        known("user_guide", R.drawable.ic_book, R.string.app_user_guide, CarApps.USER_GUIDE),
        known("gallery", R.drawable.ic_image, R.string.app_gallery, CarApps.GALLERY),
    )

    private val byId = knownApps.associateBy { it.id }

    fun knownApp(id: String): KnownApp? = byId[id]

    private fun shortcut(id: String) = byId.getValue(id).shortcut

    /**
     * Favoritos padrão da aba Aplicativos, em 3 linhas de 4: os 12 atalhos do carro de antes
     * da 0.4.4 ("Restaurar padrão" volta a eles).
     */
    val defaultFavorites: List<String> = listOf(
        "radio", "video", "bluetooth", "carplay",
        "maps", "youtube", "camera", "car_info",
        "equalizer", "steering", "files", "play_store",
    )

    /** Grade da aba Aplicativos (Favoritos e Todos): 4 colunas e 3 linhas por tela. */
    const val APPS_COLUMNS = 4
    const val APPS_ROWS = 3

    /**
     * Música: outras fontes de áudio, na linha compacta sob o player. Sem o player USB do
     * fabricante desde a 0.4.2: o player próprio o substitui (pedido do usuário).
     */
    val musicSources = listOf(shortcut("bt_music"), shortcut("radio"))

    val connectivity = listOf(
        shortcut("bluetooth"),
        open(R.drawable.ic_wifi, R.string.app_wifi, CarApps.WIFI_SETTINGS),
        shortcut("carplay"),
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
            shortcut("vehicle"),
            open(R.drawable.ic_home, R.string.settings_home_choice, CarApps.HOME_SETTINGS),
            open(R.drawable.ic_restore, R.string.settings_original_launcher, CarApps.ORIGINAL_LAUNCHER),
            shortcut("diagnostics"),
        ),
    )

    /** Aparência dos apps conhecidos na lista "Todos" (ícone de traço e nome do Nyx Car). */
    fun appLooks(context: Context): List<AppLook> = knownApps.map { known ->
        AppLook(known.id, known.target, known.shortcut.icon, context.getString(known.shortcut.label))
    }

    /** Todos os destinos de apps das abas e dos favoritos, para conferir em segundo plano quais existem. */
    val allTargets: List<AppTarget> by lazy {
        (musicSources + connectivity + settings.flatten() + knownApps.map { it.shortcut })
            .mapNotNull { (it.action as? ShortcutAction.Open)?.target }
            .distinct()
    }
}
