package com.premiumcar.launcher.launcher

import android.provider.Settings

/** O que um atalho abre. */
sealed interface AppTarget {

    /**
     * App da central, aberto pela entrada padrão (MAIN/LAUNCHER) do pacote.
     *
     * Alguns pacotes têm mais de uma entrada, por exemplo `com.hcn.AutoMediaPlayer` (Música e
     * Vídeo). Os nomes das classes não são conhecidos (e não devem ser presumidos), então a
     * escolha é feita pelo nome exibido: [labelHints] preferidos, [labelExcludes] evitados.
     */
    data class App(
        val packageName: String,
        val labelHints: List<String> = emptyList(),
        val labelExcludes: List<String> = emptyList(),
    ) : AppTarget

    /**
     * Tela do sistema aberta por uma ação pública, como `Settings.ACTION_SOUND_SETTINGS`.
     * [alternatives]: outras ações tentadas, na ordem, se a principal não existir na central.
     */
    data class SystemScreen(val action: String, val alternatives: List<String> = emptyList()) : AppTarget

    /**
     * Tela inicial (launcher) de um pacote, aberta pela entrada MAIN/HOME. Usado para o
     * launcher original, que não tem ícone na lista de apps.
     */
    data class Home(val packageName: String) : AppTarget
}

/**
 * Apps e telas da central usados pelos atalhos. Pacotes levantados pelo diagnóstico
 * (HARDWARE.md, seção 3). Se um deles sumir, o atalho aparece desativado.
 */
object CarApps {
    private val MUSIC = listOf("música", "musica", "music")
    private val VIDEO = listOf("vídeo", "video")

    /** ZLINK, exibido como "CarPlay". Só é aberto, nunca modificado (ARCHITECTURE.md, D10). */
    val CARPLAY = AppTarget.App("com.zjinnova.zlink")
    val BLUETOOTH = AppTarget.App("com.autochips.bluetooth", labelExcludes = MUSIC)
    val BT_MUSIC = AppTarget.App("com.autochips.bluetooth", labelHints = MUSIC)
    val USB_MUSIC = AppTarget.App("com.hcn.AutoMediaPlayer", labelHints = MUSIC, labelExcludes = VIDEO)
    val VIDEO_PLAYER = AppTarget.App("com.hcn.AutoMediaPlayer", labelHints = VIDEO)
    val RADIO = AppTarget.App("com.hcn.autoradio")
    val MAPS = AppTarget.App("com.google.android.apps.maps")
    val YOUTUBE = AppTarget.App("com.google.android.youtube")
    val CAMERA = AppTarget.App("com.auto.hcamera")
    val CAR_INFO = AppTarget.App("com.hcn.autocan")
    val VEHICLE_SETTINGS = AppTarget.App("com.hcn.autosetting")
    val STEERING_CONTROLS = AppTarget.App("com.hcn.swc")
    val EQUALIZER = AppTarget.App("com.hcn.autoeq")
    val FILES = AppTarget.App("com.mediatek.filemanager")
    val PLAY_STORE = AppTarget.App("com.android.vending")

    val ANDROID_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_SETTINGS)
    val WIFI_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_WIFI_SETTINGS)
    val SOUND_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_SOUND_SETTINGS)
    val DISPLAY_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_DISPLAY_SETTINGS)
    val DATE_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_DATE_SETTINGS)

    /** Launcher original da central (HARDWARE.md, seção 3): a volta fica a um toque. */
    val ORIGINAL_LAUNCHER = AppTarget.Home("com.android.launcher3")

    /**
     * Escolha da tela inicial. Nesta central, as duas ações abrem "Apps padrão"
     * (`Settings$AdvancedAppsActivity`, conferido pelo diagnóstico).
     */
    val HOME_SETTINGS = AppTarget.SystemScreen(Settings.ACTION_HOME_SETTINGS, listOf("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
}
