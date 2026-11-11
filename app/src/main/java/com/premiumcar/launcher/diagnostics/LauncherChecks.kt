package com.premiumcar.launcher.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import com.premiumcar.launcher.R
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.launcher.AllApps
import com.premiumcar.launcher.launcher.AppLauncher
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.CarApps
import com.premiumcar.launcher.launcher.FavoriteKey
import com.premiumcar.launcher.settings.FavoritesStore
import com.premiumcar.launcher.system.BluetoothStatus
import com.premiumcar.launcher.system.WifiStatus
import com.premiumcar.launcher.ui.screens.TabContent

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
        // Favoritos da aba Aplicativos (0.4.4): só os nomes, na ordem da grade.
        val favoritesStore = FavoritesStore.create(context)
        val favorites = favoritesStore.load()
        val favoriteNames = favorites.map { key ->
            when (key) {
                is FavoriteKey.Known -> TabContent.knownApp(key.appId)?.let { context.getString(it.shortcut.label) } ?: key.appId
                is FavoriteKey.Other -> key.label
            }
        }
        add("Aplicativos → Favoritos (${favorites.size})", favoriteNames.joinToString(" · "))
        add("Favoritos personalizados", if (favorites == favoritesStore.defaultList) "não (padrão)" else "sim")
        // Lista "Todos" da aba Aplicativos (0.4.4): o que aparece e o que fica escondido.
        val entries = launcher.allEntries()
        val all = AllApps.build(entries, TabContent.appLooks(context), MainActivity::class.java.name, R.drawable.ic_app_generic)
        add("Aplicativos → Todos (${all.size})", all.joinToString(" · ") { it.label })
        val generic = all.filter { it.icon == R.drawable.ic_app_generic }
        add("Todos com ícone genérico", if (generic.isEmpty()) "nenhum" else generic.joinToString(" · ") { it.label })
        val hidden = entries.filter { AllApps.isHidden(it, MainActivity::class.java.name) }
        add("Escondidos de Todos", hidden.joinToString(" · ") { it.label.ifBlank { it.packageName } }.ifEmpty { "nenhum" })
        note = "Cada atalho mostra a tela que será aberta. \"Não encontrado\" ou \"app desativado\" deixa o atalho " +
            "desativado no launcher. Em \"Todos\" ficam escondidos o Nyx Car, os atualizadores de firmware, MCU e " +
            "CAN, o Theme e a Música USB."
    }
}
