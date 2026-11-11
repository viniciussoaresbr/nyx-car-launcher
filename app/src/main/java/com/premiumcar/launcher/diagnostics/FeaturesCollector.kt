package com.premiumcar.launcher.diagnostics

import android.content.Context

/** Recursos de hardware e software que o firmware declara ao Android. */
object FeaturesCollector {

    private val keyFeatures = listOf(
        "android.hardware.bluetooth" to "Bluetooth",
        "android.hardware.bluetooth_le" to "Bluetooth LE",
        "android.hardware.wifi" to "Wi-Fi",
        "android.hardware.wifi.direct" to "Wi-Fi Direct",
        "android.hardware.location.gps" to "GPS",
        "android.hardware.usb.host" to "USB host (pendrive)",
        "android.hardware.usb.accessory" to "USB acessório",
        "android.hardware.camera.any" to "Câmera",
        "android.hardware.microphone" to "Microfone",
        "android.hardware.telephony" to "Telefonia (chip)",
        "android.hardware.type.automotive" to "Android Automotive oficial",
        "android.hardware.ram.low" to "Declarado como RAM baixa",
        "android.software.home_screen" to "Suporte a launchers",
        "android.software.app_widgets" to "Widgets de apps",
        "android.software.live_wallpaper" to "Papel de parede animado",
        "android.software.picture_in_picture" to "Picture-in-picture",
        "android.software.activities_on_secondary_displays" to "Apps em telas secundárias",
        "android.software.webview" to "WebView",
    )

    fun collect(context: Context): List<InfoSection> {
        val pm = context.packageManager
        val main = section("Recursos principais") {
            keyFeatures.forEach { (name, label) -> add(label, if (pm.hasSystemFeature(name)) "sim" else "não") }
        }
        val names = pm.systemAvailableFeatures.mapNotNull { it.name }.sorted()
        val all = section("Todos os recursos declarados (${names.size})") {
            names.forEach { add(it, "sim") }
        }
        return listOf(main, all)
    }
}
