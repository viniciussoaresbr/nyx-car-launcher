package com.premiumcar.launcher.diagnostics

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Tela, janela e toque. Precisa da janela já desenhada: chame na thread
 * principal, depois do primeiro quadro.
 */
object DisplayInfoCollector {

    fun collect(activity: Activity, props: PropertySnapshot): List<InfoSection> =
        listOf(screen(activity, props), window(activity), touch(activity))

    @Suppress("DEPRECATION")
    private fun screen(activity: Activity, props: PropertySnapshot) = section("Tela") {
        val display = activity.windowManager.defaultDisplay
        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val config = activity.resources.configuration
        highlight("Resolução física", "${real.widthPixels} × ${real.heightPixels} px")
        highlight(
            "Densidade",
            "${real.densityDpi} dpi (${densityBucket(real.densityDpi)}) · 1 dp = ${Fmt.decimal(real.density, 2)} px",
        )
        add("Densidade de fábrica (ro.sf.lcd_density)", props["ro.sf.lcd_density"])
        highlight("Área lógica disponível", "${config.screenWidthDp} × ${config.screenHeightDp} dp")
        add("Menor largura (sw)", "${config.smallestScreenWidthDp} dp")
        add("DPI físico informado (x / y)", "${Fmt.decimal(real.xdpi)} / ${Fmt.decimal(real.ydpi)}")
        val diagonal = sqrt((real.widthPixels / real.xdpi).pow(2) + (real.heightPixels / real.ydpi).pow(2))
        add("Diagonal estimada", "${Fmt.decimal(diagonal)}\" (depende do DPI informado; pode ser impreciso)")
        highlight("Taxa de atualização", "${Fmt.decimal(display.refreshRate)} Hz")
        add(
            "Modos de vídeo",
            display.supportedModes.joinToString { "${it.physicalWidth}×${it.physicalHeight} @ ${Fmt.decimal(it.refreshRate)} Hz" },
        )
        val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "horizontal" else "vertical"
        add("Orientação", "$orientation · rotação ${display.rotation * 90}°")
        add("Modo de interface (uiMode)", uiMode(config.uiMode))
        add("Escala de fonte do sistema", Fmt.decimal(config.fontScale, 2))
    }

    private fun window(activity: Activity) = section("Janela do aplicativo") {
        val density = activity.resources.displayMetrics.density
        fun size(width: Int, height: Int) =
            "$width × $height px (${(width / density).roundToInt()} × ${(height / density).roundToInt()} dp)"
        fun length(px: Int) = "$px px (${(px / density).roundToInt()} dp)"

        val decor = activity.window.decorView
        val content = activity.findViewById<View>(android.R.id.content)
        add("Janela", size(decor.width, decor.height))
        highlight("Área útil para a interface", size(content.width, content.height))
        val bars = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.systemBars())
        if (bars == null) {
            add("Barras do sistema", null)
        } else {
            highlight("Barra superior", length(bars.top))
            add("Barra inferior", length(bars.bottom))
            add("Barras laterais (esq. / dir.)", "${length(bars.left)} / ${length(bars.right)}")
        }
        note = "A área útil é o espaço real disponível para o launcher, já descontadas as barras do sistema."
    }

    private fun touch(activity: Activity) = section("Toque e entrada") {
        val pm = activity.packageManager
        add(
            "Multitoque",
            when {
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5 ou mais pontos"
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "2 pontos independentes"
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "básico"
                else -> "não informado"
            },
        )
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        devices.filter { (it.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN }
            .forEach { device ->
                val maxX = device.getMotionRange(MotionEvent.AXIS_X)?.max?.roundToInt()
                val maxY = device.getMotionRange(MotionEvent.AXIS_Y)?.max?.roundToInt()
                highlight("Tela de toque: ${device.name}", "faixa X 0–${maxX ?: "?"} · Y 0–${maxY ?: "?"}")
            }
        add("Dispositivos de entrada", devices.joinToString("\n") { it.name }.ifEmpty { null })
        note = "Use a aba Calibração para testar a precisão do toque."
    }

    private fun densityBucket(dpi: Int) = when (dpi) {
        120 -> "ldpi"
        160 -> "mdpi"
        213 -> "tvdpi"
        240 -> "hdpi"
        320 -> "xhdpi"
        480 -> "xxhdpi"
        640 -> "xxxhdpi"
        else -> "personalizada"
    }

    private fun uiMode(mode: Int): String {
        val type = when (mode and Configuration.UI_MODE_TYPE_MASK) {
            Configuration.UI_MODE_TYPE_NORMAL -> "normal"
            Configuration.UI_MODE_TYPE_CAR -> "carro"
            Configuration.UI_MODE_TYPE_DESK -> "mesa"
            Configuration.UI_MODE_TYPE_TELEVISION -> "TV"
            Configuration.UI_MODE_TYPE_APPLIANCE -> "aparelho"
            Configuration.UI_MODE_TYPE_WATCH -> "relógio"
            else -> "outro"
        }
        val night = when (mode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "noturno"
            Configuration.UI_MODE_NIGHT_NO -> "diurno"
            else -> "indefinido"
        }
        return "$type · $night"
    }
}
