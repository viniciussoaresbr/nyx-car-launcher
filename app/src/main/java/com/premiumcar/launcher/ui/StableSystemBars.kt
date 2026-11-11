package com.premiumcar.launcher.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import androidx.core.view.WindowCompat
import kotlin.math.max

/**
 * Mantém a Home parada quando a barra de status some e volta (0.4.2).
 *
 * O CarPlay (ZLINK) roda em tela cheia e esconde a barra de status (65 px na central). Ao voltar
 * para a Home, o Android desenhava o primeiro quadro sem a barra e, quando ela reaparecia,
 * empurrava o conteúdo 65 px para baixo: o "pulo" descendo do topo visto pelo usuário. Outros
 * apps não escondem a barra, por isso só acontecia ao sair do ZLINK.
 *
 * Solução padrão do Android: a janela ocupa a tela toda e o conteúdo recebe como margem a altura
 * **estável** das barras (a que vale com elas visíveis), que não muda quando outro app as esconde.
 * Se o firmware não informar a altura estável, vale a altura visível (o comportamento anterior).
 */
object StableSystemBars {

    /** Margens do conteúdo, em px. */
    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Aplica em [root] (a raiz do layout, com o fundo da tela). [onTopChange] recebe a margem de
     * cima anterior e a nova sempre que ela muda depois da primeira vez (diário do app).
     */
    fun apply(window: Window, root: View, onTopChange: ((old: Int, new: Int) -> Unit)? = null) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        var lastTop: Int? = null
        root.setOnApplyWindowInsetsListener { view, insets ->
            val padding = paddingFor(insets)
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            lastTop?.let { old -> if (old != padding.top) onTopChange?.invoke(old, padding.top) }
            lastTop = padding.top
            consume(insets)
        }
        root.requestApplyInsets()
    }

    /** Lado a lado, a maior entre a altura estável e a visível de cada barra. */
    internal fun combine(stable: Padding, visible: Padding) = Padding(
        max(stable.left, visible.left),
        max(stable.top, visible.top),
        max(stable.right, visible.right),
        max(stable.bottom, visible.bottom),
    )

    private fun paddingFor(insets: WindowInsets): Padding =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val stable = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val visible = insets.getInsets(WindowInsets.Type.systemBars())
            combine(
                Padding(stable.left, stable.top, stable.right, stable.bottom),
                Padding(visible.left, visible.top, visible.right, visible.bottom),
            )
        } else {
            legacyPadding(insets)
        }

    @Suppress("DEPRECATION") // Até o Android 10 (a central roda o 8.1), só existem estas funções.
    private fun legacyPadding(insets: WindowInsets): Padding = combine(
        Padding(insets.stableInsetLeft, insets.stableInsetTop, insets.stableInsetRight, insets.stableInsetBottom),
        Padding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom),
    )

    private fun consume(insets: WindowInsets): WindowInsets =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets.CONSUMED
        } else {
            @Suppress("DEPRECATION")
            insets.consumeSystemWindowInsets()
        }
}
