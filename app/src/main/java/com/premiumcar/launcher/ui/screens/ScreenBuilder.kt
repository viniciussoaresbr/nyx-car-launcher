package com.premiumcar.launcher.ui.screens

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.ui.components.ShortcutTile

/**
 * Monta as abas: título e um corpo em que linhas de atalhos e cartões dividem a altura
 * por peso. Sem rolagem e sem animação: tudo cabe na área útil de 1280 × 575 dp.
 *
 * [onTile] recebe cada atalho criado, para ligar o toque e o estado (disponível ou não).
 */
class ScreenBuilder(
    private val context: Context,
    private val onTile: (ShortcutTile, Shortcut) -> Unit,
) {
    private val inflater = LayoutInflater.from(context)
    private val gutter = context.resources.getDimensionPixelSize(R.dimen.pc_gutter)

    fun screen(@StringRes title: Int, content: Body.() -> Unit): View {
        val root = inflater.inflate(R.layout.screen_base, null, false)
        root.findViewById<TextView>(R.id.screen_title).setText(title)
        Body(root.findViewById(R.id.screen_body)).content()
        return root
    }

    fun inflate(@LayoutRes layout: Int): View = inflater.inflate(layout, null, false)

    inner class Body(private val body: LinearLayout) {

        /** Uma linha de atalhos; colunas vazias completam a grade. */
        fun tiles(items: List<Shortcut>, columns: Int = items.size, weight: Float = 1f) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (index in 0 until columns) {
                val cell: View = items.getOrNull(index)?.let { shortcut ->
                    ShortcutTile(context).also { tile ->
                        tile.bind(shortcut.icon, context.getString(shortcut.label))
                        onTile(tile, shortcut)
                    }
                } ?: Space(context)
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (index > 0) marginStart = gutter
                    },
                )
            }
            add(row, weight)
        }

        /** Um cartão ou outro bloco ocupando [weight] partes da altura. */
        fun block(view: View, weight: Float) = add(view, weight)

        private fun add(view: View, weight: Float) {
            val height = if (weight > 0f) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            body.addView(
                view,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, weight).apply {
                    if (body.childCount > 0) topMargin = gutter
                },
            )
        }
    }
}
