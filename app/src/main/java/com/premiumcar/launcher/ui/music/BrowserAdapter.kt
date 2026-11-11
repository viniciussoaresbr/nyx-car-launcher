package com.premiumcar.launcher.ui.music

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.premiumcar.launcher.R
import com.premiumcar.launcher.player.AudioFiles
import com.premiumcar.launcher.player.MusicVolume
import java.io.File

/** Uma linha da lista da aba Música. */
sealed interface BrowserItem {
    data class Volume(val volume: MusicVolume) : BrowserItem

    data class Folder(val file: File) : BrowserItem

    /** [index]: posição da música na pasta (a fila do player). */
    data class Track(val file: File, val index: Int) : BrowserItem
}

/**
 * Lista de pendrives, pastas e músicas, com reciclagem de linhas da própria `ListView`:
 * só as linhas visíveis existem, mesmo com centenas de músicas na pasta.
 */
class BrowserAdapter(context: Context) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val accent = ColorStateList.valueOf(context.getColor(R.color.pc_accent))
    private val secondary = ColorStateList.valueOf(context.getColor(R.color.pc_text_secondary))
    private val primaryText = context.getColor(R.color.pc_text_primary)
    private val accentText = context.getColor(R.color.pc_accent)

    var items: List<BrowserItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Música tocando agora, destacada na lista. */
    var current: File? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): BrowserItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.view_browser_item, parent, false)
        val holder = view.tag as? Holder ?: Holder(view).also { view.tag = it }
        holder.bind(items[position])
        return view
    }

    private inner class Holder(view: View) {
        private val icon: ImageView = view.findViewById(R.id.item_icon)
        private val name: TextView = view.findViewById(R.id.item_name)
        private val trailing: ImageView = view.findViewById(R.id.item_trailing)

        fun bind(item: BrowserItem) {
            when (item) {
                is BrowserItem.Volume -> container(R.drawable.ic_usb, item.volume.label)
                is BrowserItem.Folder -> container(R.drawable.ic_folder, item.file.name)
                is BrowserItem.Track -> {
                    val playing = item.file == current
                    icon.setImageResource(R.drawable.ic_music)
                    icon.imageTintList = if (playing) accent else secondary
                    name.text = AudioFiles.displayName(item.file.name)
                    name.setTextColor(if (playing) accentText else primaryText)
                    trailing.setImageResource(R.drawable.ic_volume)
                    trailing.imageTintList = accent
                    trailing.visibility = if (playing) View.VISIBLE else View.GONE
                }
            }
        }

        private fun container(iconRes: Int, label: String) {
            icon.setImageResource(iconRes)
            icon.imageTintList = accent
            name.text = label
            name.setTextColor(primaryText)
            trailing.setImageResource(R.drawable.ic_chevron_right)
            trailing.imageTintList = secondary
            trailing.visibility = View.VISIBLE
        }
    }
}
