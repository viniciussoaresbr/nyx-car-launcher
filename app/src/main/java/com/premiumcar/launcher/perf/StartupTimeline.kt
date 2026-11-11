package com.premiumcar.launcher.perf

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.CompletableDeferred

/**
 * Marcos da inicialização do processo atual, em `SystemClock.uptimeMillis()`.
 * Cada processo (app principal e testes de fluidez) tem a sua própria cópia.
 */
object StartupTimeline {

    /** Início do processo. Indisponível antes do Android 7 (API 24). */
    val processStart: Long? =
        if (Build.VERSION.SDK_INT >= 24) Process.getStartUptimeMillis() else null

    @Volatile
    var applicationCreated: Long = 0L
        private set

    @Volatile
    var activityCreated: Long = 0L
        private set

    @Volatile
    var firstFrame: Long = 0L
        private set

    /** Nome da primeira tela aberta neste processo (ex.: "Home (Views)"). */
    @Volatile
    var firstScreen: String? = null
        private set

    private val firstFrameSignal = CompletableDeferred<Unit>()
    private val firstFrameActions = mutableListOf<() -> Unit>()

    /** Tempo entre o início do processo e o primeiro quadro desenhado, se conhecido. */
    val startupMillis: Long?
        get() = processStart?.takeIf { firstFrame != 0L }?.let { firstFrame - it }

    /** Tempo entre a criação da primeira tela e o primeiro quadro, se conhecido. */
    val screenToFirstFrameMillis: Long?
        get() = activityCreated.takeIf { it != 0L && firstFrame != 0L }?.let { firstFrame - it }

    /**
     * O processo nasceu sem tela e ela só veio bem depois: por exemplo, o Android reabriu o app em
     * segundo plano (a central fecha os apps ao desligar o carro). Numa abertura a frio comum, a
     * tela é criada poucos milissegundos depois do processo (15 ms na central).
     */
    val startedWithoutScreen: Boolean
        get() = activityCreated != 0L && applicationCreated != 0L && activityCreated - applicationCreated > BACKGROUND_GAP_MS

    private const val BACKGROUND_GAP_MS = 1_500L

    fun markApplicationCreated() {
        applicationCreated = SystemClock.uptimeMillis()
    }

    /**
     * Registra a primeira Activity do processo.
     * Retorna `true` somente na primeira chamada, ou seja, numa partida a frio.
     */
    @Synchronized
    fun markActivityCreated(screenName: String): Boolean {
        if (activityCreated != 0L) return false
        activityCreated = SystemClock.uptimeMillis()
        firstScreen = screenName
        return true
    }

    suspend fun awaitFirstFrame() = firstFrameSignal.await()

    /** Executa [action] na thread principal logo após o primeiro quadro (ou já, se ele já ocorreu). */
    fun doOnFirstFrame(action: () -> Unit) {
        if (firstFrame != 0L) action() else firstFrameActions += action
    }

    /** Registra o instante em que a janela termina de desenhar o primeiro quadro. */
    fun trackFirstFrame(decorView: View) {
        if (decorView.isAttachedToWindow) {
            registerDrawListener(decorView)
        } else {
            // Antes do Android 8, um OnDrawListener registrado antes do attach é perdido.
            decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    registerDrawListener(view)
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            })
        }
    }

    private fun registerDrawListener(view: View) {
        val handler = Handler(Looper.getMainLooper())
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var seen = false

            override fun onDraw() {
                if (seen) return
                seen = true
                // Roda logo depois que o quadro é entregue ao RenderThread.
                handler.postAtFrontOfQueue {
                    firstFrame = SystemClock.uptimeMillis()
                    firstFrameSignal.complete(Unit)
                    firstFrameActions.forEach { it() }
                    firstFrameActions.clear()
                    // Não é permitido remover o listener dentro de onDraw; aqui é seguro.
                    val observer = view.viewTreeObserver
                    if (observer.isAlive) observer.removeOnDrawListener(this)
                }
            }
        })
    }
}
