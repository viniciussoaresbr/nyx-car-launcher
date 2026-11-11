package com.premiumcar.launcher.perf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import com.premiumcar.launcher.design.PcPalette

/** Parâmetros idênticos para os testes em Compose e em Views (comparação justa). */
object BenchmarkSpec {
    const val ITEM_COUNT = 300
    const val TILE_MIN_WIDTH_DP = 120
    const val TILE_HEIGHT_DP = 120
    const val ICON_SIZE_DP = 48
    const val SPACING_DP = 12
    const val PADDING_DP = 16
    const val CORNER_DP = 16
    const val LABEL_SP = 14
    const val SPEED_DP_PER_SECOND = 1000
    const val DIRECTION_MILLIS = 3000L
    const val SETTLE_MILLIS = 700L
}

class BenchItem(val label: String, val icon: Bitmap)

class LoadedItems(val items: List<BenchItem>, val distinctIcons: Int, val loadMillis: Long)

object BenchmarkData {
    /**
     * Carrega nome e ícone (já no tamanho de exibição) dos apps instalados e
     * repete a lista até [BenchmarkSpec.ITEM_COUNT] itens. Chame fora da thread principal.
     */
    fun load(context: Context, iconPx: Int): LoadedItems {
        val start = SystemClock.uptimeMillis()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val distinct = pm.queryIntentActivities(intent, 0).map { info ->
            BenchItem(info.loadLabel(pm).toString(), info.loadIcon(pm).renderToBitmap(iconPx))
        }
        val elapsed = SystemClock.uptimeMillis() - start
        val base = distinct.ifEmpty { listOf(BenchItem("App", placeholder(iconPx))) }
        val items = List(BenchmarkSpec.ITEM_COUNT) { base[it % base.size] }
        return LoadedItems(items, distinct.size, elapsed)
    }

    private fun Drawable.renderToBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val previousBounds = copyBounds()
        setBounds(0, 0, size, size)
        draw(Canvas(bitmap))
        bounds = previousBounds
        return bitmap
    }

    private fun placeholder(size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(PcPalette.ACCENT_DIM) }
}

/** Resultado de uma execução, transportado entre processos via extras da Intent. */
data class BenchmarkResult(
    val engine: String,
    val coldStart: Boolean,
    /** Início do processo até o primeiro quadro; -1 se desconhecido. */
    val startupMs: Long,
    val distinctIcons: Int,
    val iconLoadMs: Long,
    val stats: FrameStats,
    val memory: MemorySnapshot,
) {
    fun toBundle() = Bundle().apply {
        putString(K_ENGINE, engine)
        putBoolean(K_COLD, coldStart)
        putLong(K_STARTUP, startupMs)
        putInt(K_ICONS, distinctIcons)
        putLong(K_ICON_MS, iconLoadMs)
        putInt(K_FRAMES, stats.frames)
        putLong(K_DURATION, stats.durationMs)
        putDouble(K_FPS, stats.averageFps)
        putDouble(K_REFRESH, stats.refreshRateHz)
        putInt(K_JANKY, stats.jankyFrames)
        putDouble(K_P50, stats.p50Ms)
        putDouble(K_P90, stats.p90Ms)
        putDouble(K_P99, stats.p99Ms)
        putDouble(K_MAX, stats.maxMs)
        putInt(K_PSS, memory.totalPssKb)
        putInt(K_JAVA, memory.javaHeapKb)
        putInt(K_NATIVE, memory.nativeHeapKb)
        putInt(K_GRAPHICS, memory.graphicsKb)
    }

    companion object {
        private const val K_ENGINE = "engine"
        private const val K_COLD = "cold"
        private const val K_STARTUP = "startup"
        private const val K_ICONS = "icons"
        private const val K_ICON_MS = "iconMs"
        private const val K_FRAMES = "frames"
        private const val K_DURATION = "duration"
        private const val K_FPS = "fps"
        private const val K_REFRESH = "refresh"
        private const val K_JANKY = "janky"
        private const val K_P50 = "p50"
        private const val K_P90 = "p90"
        private const val K_P99 = "p99"
        private const val K_MAX = "max"
        private const val K_PSS = "pss"
        private const val K_JAVA = "java"
        private const val K_NATIVE = "native"
        private const val K_GRAPHICS = "graphics"

        fun fromBundle(bundle: Bundle?): BenchmarkResult? {
            val engine = bundle?.getString(K_ENGINE) ?: return null
            return BenchmarkResult(
                engine = engine,
                coldStart = bundle.getBoolean(K_COLD),
                startupMs = bundle.getLong(K_STARTUP, -1),
                distinctIcons = bundle.getInt(K_ICONS),
                iconLoadMs = bundle.getLong(K_ICON_MS),
                stats = FrameStats(
                    frames = bundle.getInt(K_FRAMES),
                    durationMs = bundle.getLong(K_DURATION),
                    averageFps = bundle.getDouble(K_FPS),
                    refreshRateHz = bundle.getDouble(K_REFRESH),
                    jankyFrames = bundle.getInt(K_JANKY),
                    p50Ms = bundle.getDouble(K_P50),
                    p90Ms = bundle.getDouble(K_P90),
                    p99Ms = bundle.getDouble(K_P99),
                    maxMs = bundle.getDouble(K_MAX),
                ),
                memory = MemorySnapshot(
                    totalPssKb = bundle.getInt(K_PSS),
                    javaHeapKb = bundle.getInt(K_JAVA, -1),
                    nativeHeapKb = bundle.getInt(K_NATIVE, -1),
                    graphicsKb = bundle.getInt(K_GRAPHICS, -1),
                ),
            )
        }
    }
}

/** Resultados recebidos nesta sessão do app principal. */
object BenchmarkResults {
    val all = mutableStateListOf<BenchmarkResult>()
}

/** Finaliza um teste: mede memória, devolve o resultado ao app principal e fecha a tela. */
internal fun Activity.finishBenchmark(engine: String, coldStart: Boolean, loaded: LoadedItems, frameTimes: LongArray) {
    @Suppress("DEPRECATION")
    val refreshRate = windowManager.defaultDisplay.refreshRate
    val result = BenchmarkResult(
        engine = engine,
        coldStart = coldStart,
        startupMs = StartupTimeline.startupMillis ?: -1,
        distinctIcons = loaded.distinctIcons,
        iconLoadMs = loaded.loadMillis,
        stats = FrameStats.from(frameTimes, refreshRate),
        memory = MemorySnapshot.capture(),
    )
    setResult(Activity.RESULT_OK, Intent().putExtras(result.toBundle()))
    finish()
}
