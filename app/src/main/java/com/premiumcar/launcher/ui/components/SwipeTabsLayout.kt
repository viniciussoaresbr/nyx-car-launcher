package com.premiumcar.launcher.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Contêiner das abas que reconhece o deslize horizontal (etapa 0.4.1): arrastar o dedo para a
 * esquerda pede a próxima aba; para a direita, a anterior. As abas são os filhos; só a atual
 * fica visível (quem troca é a MainActivity, em [onSwipe]).
 *
 * - Só assume o toque quando o movimento é claramente horizontal. Rolagens verticais (lista de
 *   músicas) e toques continuam com os filhos. Ao assumir, o filho recebe `ACTION_CANCEL`: o
 *   botão onde o dedo começou não é acionado.
 * - Para não trocar de aba num solavanco, a troca exige uma distância mínima ou um gesto rápido.
 * - A aba atual acompanha o dedo com resistência (mais forte quando não há aba naquele lado).
 *   Ao soltar, a nova aba entra com uma transição curta; sem troca, a atual volta ao lugar.
 *   Nenhuma animação contínua.
 */
class SwipeTabsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    enum class Direction { NEXT, PREVIOUS }

    /** Troca de aba pedida pelo gesto. Devolve `false` se não há aba nessa direção. */
    var onSwipe: ((Direction) -> Boolean)? = null

    /** Existe aba nessa direção? Sem aba, o arrasto tem resistência maior e nada troca. */
    var canSwipe: (Direction) -> Boolean = { true }

    private val config = ViewConfiguration.get(context)
    private val density = resources.displayMetrics.density
    private val startDistance = config.scaledTouchSlop * 2f
    private val minFlingVelocity = FLING_VELOCITY_DP * density
    private val maxFlingVelocity = config.scaledMaximumFlingVelocity.toFloat()

    private var downX = 0f
    private var downY = 0f
    private var dragStartX = 0f
    private var tracking = false
    private var dragging = false
    private var velocity: VelocityTracker? = null

    /** Distância mínima para trocar de aba ao soltar devagar. */
    private val commitDistance: Float
        get() = max(MIN_COMMIT_DP * density, width * COMMIT_FRACTION)

    private val current: View?
        get() = children.firstOrNull { it.isVisible }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        observe(event)
        return dragging
    }

    // O deslize é um atalho para a barra inferior, que continua acessível: não é um clique.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // O ACTION_DOWN já passou por onInterceptTouchEvent; os demais chegam só aqui quando
        // nenhum filho ficou com o toque (dedo numa área vazia) ou depois de assumir o gesto.
        if (event.actionMasked != MotionEvent.ACTION_DOWN) observe(event)
        if (dragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> follow(event.x - dragStartX)
                MotionEvent.ACTION_UP -> finish(event.x - downX)
                MotionEvent.ACTION_CANCEL -> settle()
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) clear()
        return tracking || dragging
    }

    /** Acompanha o toque e decide quando ele vira um deslize horizontal. */
    private fun observe(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clear()
                downX = event.x
                downY = event.y
                tracking = onSwipe != null
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                current?.animate()?.cancel()
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!dragging) tracking = false // dois dedos: não é deslize
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!tracking || dragging) return
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > startDistance && abs(dx) > abs(dy) * DOMINANCE) {
                    dragging = true
                    dragStartX = event.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (abs(dy) > startDistance) {
                    tracking = false // rolagem vertical: fica com os filhos
                }
            }
            MotionEvent.ACTION_UP -> velocity?.addMovement(event)
        }
    }

    private fun follow(dx: Float) {
        val screen = current ?: return
        val direction = if (dx < 0) Direction.NEXT else Direction.PREVIOUS
        val factor = if (canSwipe(direction)) FOLLOW_FACTOR else EDGE_FACTOR
        val limit = MAX_FOLLOW_DP * density
        screen.translationX = (dx * factor).coerceIn(-limit, limit)
        screen.alpha = 1f - abs(screen.translationX) / limit * FOLLOW_FADE
    }

    /** Dedo solto: troca de aba se o gesto foi longo ou rápido o bastante; senão, volta. */
    private fun finish(totalDx: Float) {
        val tracker = velocity
        tracker?.computeCurrentVelocity(1000, maxFlingVelocity)
        val vx = tracker?.xVelocity ?: 0f
        val direction = if (totalDx < 0) Direction.NEXT else Direction.PREVIOUS
        val farEnough = abs(totalDx) >= commitDistance
        val quickEnough = abs(totalDx) >= MIN_FLING_DISTANCE_DP * density &&
            abs(vx) >= minFlingVelocity && sign(vx) == sign(totalDx)
        val previous = current
        if ((farEnough || quickEnough) && canSwipe(direction) && onSwipe?.invoke(direction) == true) {
            previous?.reset()
            current?.let { enter(it, direction) }
        } else {
            settle()
        }
    }

    /** A nova aba entra pelo lado de onde o dedo veio. */
    private fun enter(screen: View, direction: Direction) {
        val offset = ENTER_OFFSET_DP * density
        screen.translationX = if (direction == Direction.NEXT) offset else -offset
        screen.alpha = ENTER_ALPHA
        screen.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .start()
    }

    /** Sem troca: a aba atual volta ao lugar. */
    private fun settle() {
        current?.animate()
            ?.translationX(0f)
            ?.alpha(1f)
            ?.setDuration(SETTLE_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }

    private fun View.reset() {
        animate().cancel()
        translationX = 0f
        alpha = 1f
    }

    private fun clear() {
        tracking = false
        dragging = false
        velocity?.recycle()
        velocity = null
    }

    private companion object {
        /** Quanto o movimento horizontal precisa superar o vertical para virar deslize. */
        const val DOMINANCE = 2f

        /** Distância para trocar ao soltar devagar: 12% da largura (154 px na central), no mínimo 96 dp. */
        const val COMMIT_FRACTION = 0.12f
        const val MIN_COMMIT_DP = 96f

        /** Gesto rápido: troca com menos distância, mas nunca menos que 48 dp. */
        const val MIN_FLING_DISTANCE_DP = 48f
        const val FLING_VELOCITY_DP = 600f

        /** A aba acompanha o dedo com resistência, até 120 dp, e fica um pouco transparente. */
        const val FOLLOW_FACTOR = 0.35f
        const val EDGE_FACTOR = 0.1f
        const val MAX_FOLLOW_DP = 120f
        const val FOLLOW_FADE = 0.3f

        const val ENTER_OFFSET_DP = 96f
        const val ENTER_ALPHA = 0.4f
        const val ENTER_MS = 160L
        const val SETTLE_MS = 120L
    }
}
