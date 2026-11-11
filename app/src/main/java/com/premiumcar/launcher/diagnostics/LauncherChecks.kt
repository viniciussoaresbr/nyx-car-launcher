package com.premiumcar.launcher.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import com.premiumcar.launcher.launcher.AppLauncher
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.CarApps
import com.premiumcar.launcher.system.BluetoothStatus
import com.premiumcar.launcher.system.WifiStatus

/**
 * O que o launcher enxerga nesta central: para onde cada atalho leva e os estados usados
 * pelos indicadores. Serve para conferir a Fase 3 no carro (somente leitura).
 */
object LauncherChecks {

    private val shortcuts: List<Pair<String, AppTarget>> = listOf(
        "CarPlay (ZLINK)" to CarApps.CARPLAY,
        "Bluetooth" to CarApps.BLUETOOTH,
        "BT Música" to CarApps.BT_MUSIC,
        "Música USB" to CarApps.USB_MUSIC,
        "Vídeo" to CarApps.VIDEO_PLAYER,
        "Rádio" to CarApps.RADIO,
        "Maps" to CarApps.MAPS,
        "YouTube" to CarApps.YOUTUBE,
        "Câmera" to CarApps.CAMERA,
        "Informações do carro" to CarApps.CAR_INFO,
        "Veículo" to CarApps.VEHICLE_SETTINGS,
        "Comandos de volante" to CarApps.STEERING_CONTROLS,
        "Equalizador" to CarApps.EQUALIZER,
        "Arquivos" to CarApps.FILES,
        "Play Store" to CarApps.PLAY_STORE,
        "Configurações do Android" to CarApps.ANDROID_SETTINGS,
        "Wi-Fi" to CarApps.WIFI_SETTINGS,
        "Som" to CarApps.SOUND_SETTINGS,
        "Tela" to CarApps.DISPLAY_SETTINGS,
        "Data e hora" to CarApps.DATE_SETTINGS,
        "Tela inicial (escolher)" to CarApps.HOME_SETTINGS,
        "Launcher original" to CarApps.ORIGINAL_LAUNCHER,
    )

    fun section(context: Context) = section("Atalhos e indicadores do launcher") {
        val launcher = AppLauncher(context)
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo
        when {
            home == null -> warn("Nyx Car como tela inicial", "não foi possível consultar")
            home.packageName == context.packageName -> add("Nyx Car como tela inicial", "SIM (padrão)", EntryLevel.HIGHLIGHT)
            home.packageName == "android" -> add("Nyx Car como tela inicial", "não definida: o Android pergunta ao tocar em Home")
            else -> add("Nyx Car como tela inicial", "não (padrão atual: ${home.packageName})")
        }
        add("Wi-Fi (indicador da Home)", if (WifiStatus.isConnectedNow(context)) "conectado" else "desconectado")
        add(
            "Bluetooth segundo o Android",
            when (BluetoothStatus.read(context)) {
                BluetoothStatus.State.ON -> "ligado (indicador visível)"
                BluetoothStatus.State.OFF -> "desligado (indicador oculto)"
                BluetoothStatus.State.UNKNOWN -> "não informado (indicador oculto)"
            },
        )
        val musicActive = runCatching { context.getSystemService(AudioManager::class.java)?.isMusicActive == true }.getOrDefault(false)
        add("Música tocando agora (qualquer app)", if (musicActive) "sim" else "não")
        shortcuts.forEach { (name, target) ->
            val intent = launcher.resolve(target)
            val destination = intent?.component?.flattenToShortString() ?: intent?.action
            when {
                destination != null -> add("Atalho: $name", destination)
                target is AppTarget.App && launcher.isDisabled(target.packageName) ->
                    warn("Atalho: $name", "app desativado (Configurações → Apps)")
                else -> warn("Atalho: $name", "não encontrado")
            }
        }
        note = "Cada atalho mostra a tela que será aberta. \"Não encontrado\" ou \"app desativado\" deixa o atalho " +
            "desativado no launcher."
    }
}
