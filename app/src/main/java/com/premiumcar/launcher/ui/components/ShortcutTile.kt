package com.premiumcar.launcher.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.premiumcar.launcher.R

/**
 * Atalho: ícone de destaque sobre um rótulo (e uma legenda opcional), num bloco tocável.
 * Estados (pressionado, selecionado, desativado) vêm de `pc_bg_tile` e das listas de cores.
 *
 * Em XML: `app:tileIcon` e `app:tileLabel`. Em código: [bind].
 */
class ShortcutTile @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val icon: ImageView
    private val label: TextView
    private val captionView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setBackgroundResource(R.drawable.pc_bg_tile)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.pc_tile_height)
        LayoutInflater.from(context).inflate(R.layout.view_shortcut_tile, this, true)
        icon = findViewById(R.id.tile_icon)
        label = findViewById(R.id.tile_label)
        captionView = findViewById(R.id.tile_caption)
        context.withStyledAttributes(attrs, R.styleable.ShortcutTile) {
            icon.setImageDrawable(getDrawable(R.styleable.ShortcutTile_tileIcon))
            label.text = getText(R.styleable.ShortcutTile_tileLabel)
        }
        contentDescription = label.text
    }

    fun bind(@DrawableRes iconRes: Int, text: CharSequence) {
        icon.setImageResource(iconRes)
        label.text = text
        contentDescription = text
    }

    val labelText: CharSequence get() = label.text

    /** Linha de apoio sob o rótulo (ex.: "Não encontrado"); `null` esconde. */
    var caption: CharSequence?
        get() = captionView.text.takeIf { captionView.isVisible }
        set(value) {
            captionView.text = value
            captionView.isVisible = !value.isNullOrEmpty()
        }
}
