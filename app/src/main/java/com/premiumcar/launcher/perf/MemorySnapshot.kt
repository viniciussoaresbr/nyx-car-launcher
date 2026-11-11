package com.premiumcar.launcher.perf

import android.os.Debug

/** Memória do processo atual (PSS, em kB). O valor -1 indica dado indisponível. */
data class MemorySnapshot(
    val totalPssKb: Int,
    val javaHeapKb: Int,
    val nativeHeapKb: Int,
    val graphicsKb: Int,
) {
    companion object {
        /** Lê /proc/self/smaps e leva dezenas de ms: prefira chamar fora da thread principal. */
        fun capture(): MemorySnapshot {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            fun stat(name: String) = info.getMemoryStat(name)?.toIntOrNull() ?: -1
            return MemorySnapshot(
                totalPssKb = info.totalPss,
                javaHeapKb = stat("summary.java-heap"),
                nativeHeapKb = stat("summary.native-heap"),
                graphicsKb = stat("summary.graphics"),
            )
        }
    }
}
