package com.premiumcar.launcher.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.premiumcar.launcher.R
import com.premiumcar.launcher.ui.Destination

/**
 * Barra inferior com acesso rápido aos destinos principais, só com ícones.
 * Cada item ocupa a mesma largura (256 dp na central): alvos de toque grandes.
 * O destino selecionado recebe a cor de destaque e uma pílula atrás do ícone.
 * O nome do destino fica em `contentDescription` (leitores de tela e avisos).
 */
class BottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val items = linkedMapOf<Destination, View>()

    /** Chamado quando o usuário toca num destino (inclusive o já selecionado). */
    var onDestinationClick: ((Destination) -> Unit)? = null

    var selected: Destination? = null
        set(value) {
            field = value
            items.forEach { (destination, view) -> view.isSelected = destination == value }
        }

    init {
        orientation = HORIZONTAL
    }

    fun setDestinations(destinations: List<Destination>) {
        removeAllViews()
        items.clear()
        val inflater = LayoutInflater.from(context)
        destinations.forEach { destination ->
            val item = inflater.inflate(R.layout.view_nav_item, this, false)
            item.findViewById<ImageView>(R.id.nav_icon).setImageResource(destination.iconRes)
            item.contentDescription = context.getString(destination.labelRes)
            item.setOnClickListener { onDestinationClick?.invoke(destination) }
            addView(item)
            items[destination] = item
        }
        selected = selected
    }

    fun itemView(destination: Destination): View? = items[destination]
}
