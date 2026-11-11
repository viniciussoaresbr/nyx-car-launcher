package com.premiumcar.launcher.perf

import android.view.Choreographer

/**
 * Registra o instante de cada vsync enquanto ativo. Intervalos maiores que o
 * período da tela indicam quadros perdidos (engasgos). Use na thread principal.
 */
class FrameRecorder(capacity: Int = 4096) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private val frameTimes = LongArray(capacity)
    private var count = 0
    private var running = false

    fun start() {
        count = 0
        running = true
        choreographer.postFrameCallback(this)
    }

    /** Encerra a gravação e devolve os instantes registrados (nanossegundos). */
    fun stop(): LongArray {
        running = false
        choreographer.removeFrameCallback(this)
        return frameTimes.copyOf(count)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (count < frameTimes.size) frameTimes[count++] = frameTimeNanos
        choreographer.postFrameCallback(this)
    }
}
