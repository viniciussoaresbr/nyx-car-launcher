package com.premiumcar.launcher.perf

import kotlin.math.ceil
import kotlin.math.roundToLong

/** Estatísticas de fluidez calculadas a partir dos instantes de vsync registrados. */
data class FrameStats(
    val frames: Int,
    val durationMs: Long,
    val averageFps: Double,
    val refreshRateHz: Double,
    val jankyFrames: Int,
    val p50Ms: Double,
    val p90Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
) {
    val jankPercent: Double
        get() = if (frames == 0) 0.0 else jankyFrames * 100.0 / frames

    companion object {
        /** Um intervalo acima de 1,5× o período da tela conta como quadro atrasado. */
        const val JANK_FACTOR = 1.5

        fun from(frameTimesNanos: LongArray, refreshRateHz: Float): FrameStats {
            val rate = if (refreshRateHz in 1f..1000f) refreshRateHz.toDouble() else 60.0
            if (frameTimesNanos.size < 2) {
                return FrameStats(0, 0, 0.0, rate, 0, 0.0, 0.0, 0.0, 0.0)
            }
            val intervals = DoubleArray(frameTimesNanos.size - 1) { i ->
                (frameTimesNanos[i + 1] - frameTimesNanos[i]) / 1_000_000.0
            }
            val totalMs = (frameTimesNanos.last() - frameTimesNanos.first()) / 1_000_000.0
            val periodMs = 1000.0 / rate
            val sorted = intervals.sorted()
            return FrameStats(
                frames = intervals.size,
                durationMs = totalMs.roundToLong(),
                averageFps = if (totalMs > 0) intervals.size * 1000.0 / totalMs else 0.0,
                refreshRateHz = rate,
                jankyFrames = intervals.count { it > periodMs * JANK_FACTOR },
                p50Ms = percentile(sorted, 50.0),
                p90Ms = percentile(sorted, 90.0),
                p99Ms = percentile(sorted, 99.0),
                maxMs = sorted.last(),
            )
        }

        /** Percentil pelo método do posto mais próximo. */
        internal fun percentile(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
    }
}
