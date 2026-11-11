package com.premiumcar.launcher.ui.catalog

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.ui.Destination
import com.premiumcar.launcher.ui.components.BottomNavBar
import com.premiumcar.launcher.ui.components.ShortcutTile
import kotlin.math.roundToInt

/**
 * Catálogo do Design System: todos os tokens e componentes, em todos os estados.
 * Ferramenta de desenvolvimento, aberta pelo Diagnóstico (aba Calibração), para
 * conferir contraste e legibilidade no painel real.
 */
class DesignCatalogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val margin = px(R.dimen.pc_screen_margin)
        val content = LinearLayout(this).apply {
            id = R.id.catalog_content
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
            setBackgroundColor(getColor(R.color.pc_background))
        }
        content.addView(text(getString(R.string.design_catalog), R.style.PcText_Headline))
        content.addSection("Cores", colors())
        content.addSection("Tipografia", typography())
        content.addSection("Botões", buttons())
        content.addSection("Atalhos", tiles())
        content.addSection("Controles de mídia", mediaControls())
        content.addSection("Barra de navegação", navigationBar())
        content.addSection("Cartões", cards())
        content.addSection("Ícones", icons())
        content.addSection("Espaçamentos", spacing())
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.pc_background))
                addView(content)
            },
        )
    }

    // ---------- Seções ----------

    private fun colors(): View {
        val swatches = listOf(
            "Fundo" to R.color.pc_background,
            "Superfície" to R.color.pc_surface,
            "Superfície +" to R.color.pc_surface_high,
            "Superfície ++" to R.color.pc_surface_highest,
            "Borda" to R.color.pc_outline,
            "Texto" to R.color.pc_text_primary,
            "Texto secundário" to R.color.pc_text_secondary,
            "Texto desativado" to R.color.pc_text_disabled,
            "Destaque" to R.color.pc_accent,
            "Destaque escuro" to R.color.pc_accent_dim,
            "Fundo de destaque" to R.color.pc_accent_container,
            "Sobre o destaque" to R.color.pc_on_accent,
            "Alerta" to R.color.pc_warning,
            "Erro" to R.color.pc_error,
            "Sucesso" to R.color.pc_success,
        ).map { (name, color) -> labeled(swatch(color), "$name\n${hex(color)}") }
        return column(row(swatches.take(8)), row(swatches.drop(8)))
    }

    private fun typography(): View {
        val samples = listOf(
            Triple(R.style.PcText_Clock, "15:48", "Relógio · 112 sp · leve"),
            Triple(R.style.PcText_Headline, "Configurações do launcher", "Headline · 28 sp"),
            Triple(R.style.PcText_Title, "Navegação · Música", "Title · 24 sp · médio"),
            Triple(R.style.PcText_BodyLarge, "domingo, 9 de novembro", "BodyLarge · 22 sp"),
            Triple(R.style.PcText_Body, "Nome do artista", "Body · 20 sp · secundário"),
            Triple(R.style.PcText_Label, "Aplicativos", "Label · 18 sp · médio"),
            Triple(R.style.PcText_Caption, "Toque para traçar uma rota", "Caption · 16 sp · secundário (mínimo)"),
        )
        return column(
            *samples.map { (style, sample, spec) ->
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(text(spec, R.style.PcText_Caption), LinearLayout.LayoutParams(dp(360), WRAP))
                    addView(text(sample, style))
                }
            }.toTypedArray(),
        )
    }

    private fun buttons(): View = row(
        labeled(button(R.style.PcButton_Primary, "Abrir navegação"), "Principal"),
        labeled(button(R.style.PcButton_Primary, "Abrir navegação").pressed(), "Principal · pressionado"),
        labeled(button(R.style.PcButton_Primary, "Abrir navegação").disabled(), "Principal · desativado"),
        labeled(button(R.style.PcButton, "Cancelar"), "Secundário"),
        labeled(button(R.style.PcButton, "Cancelar").pressed(), "Secundário · pressionado"),
        labeled(button(R.style.PcButton, "Cancelar").disabled(), "Secundário · desativado"),
    )

    private fun tiles(): View {
        fun tile() = ShortcutTile(this).apply { bind(R.drawable.ic_bluetooth, "Bluetooth") }
        val size = { view: View -> view.apply { layoutParams = LinearLayout.LayoutParams(dp(192), px(R.dimen.pc_tile_height)) } }
        return row(
            labeled(size(tile()), "Normal"),
            labeled(size(tile().pressed()), "Pressionado"),
            labeled(size(tile().apply { isSelected = true }), "Selecionado"),
            labeled(size(tile().disabled()), "Desativado"),
            labeled(size(tile().disabled().apply { caption = getString(R.string.tile_not_found) }), "App ausente"),
        )
    }

    private fun mediaControls(): View {
        fun round(@StyleRes style: Int, @DrawableRes icon: Int, @DimenRes size: Int) =
            ImageButton(this, null, 0, style).apply {
                setImageResource(icon)
                layoutParams = LinearLayout.LayoutParams(px(size), px(size))
            }
        return row(
            labeled(round(R.style.PcRoundButton, R.drawable.ic_skip_next, R.dimen.pc_media_button), "Normal"),
            labeled(round(R.style.PcRoundButton, R.drawable.ic_skip_next, R.dimen.pc_media_button).pressed(), "Pressionado"),
            labeled(round(R.style.PcRoundButton, R.drawable.ic_skip_next, R.dimen.pc_media_button).disabled(), "Desativado"),
            labeled(round(R.style.PcRoundButton_Primary, R.drawable.ic_play, R.dimen.pc_media_button_primary), "Principal"),
            labeled(round(R.style.PcRoundButton_Primary, R.drawable.ic_pause, R.dimen.pc_media_button_primary).pressed(), "Principal · pressionado"),
        )
    }

    private fun navigationBar(): View {
        val bar = BottomNavBar(this).apply {
            setBackgroundResource(R.drawable.pc_bg_nav_bar)
            setDestinations(Destination.entries)
            selected = Destination.MUSIC
        }
        val pressedBar = BottomNavBar(this).apply {
            setBackgroundResource(R.drawable.pc_bg_nav_bar)
            setDestinations(Destination.entries)
            selected = Destination.MUSIC
            itemView(Destination.APPS)?.isPressed = true
        }
        val params = { LinearLayout.LayoutParams(MATCH, px(R.dimen.pc_nav_bar_height)) }
        return column(
            labeled(bar.apply { layoutParams = params() }, "Destino selecionado: Música"),
            labeled(pressedBar.apply { layoutParams = params() }, "Toque em Aplicativos (pressionado)"),
        )
    }

    private fun cards(): View {
        fun card(@DrawableRes background: Int, title: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(background)
            val padding = px(R.dimen.pc_card_padding)
            setPadding(padding, padding, padding, padding)
            addView(text(title, R.style.PcText_Title))
            addView(text("Conteúdo do cartão em texto de apoio.", R.style.PcText_Body))
            layoutParams = LinearLayout.LayoutParams(dp(400), dp(160))
        }
        return row(
            labeled(card(R.drawable.pc_bg_card, "Cartão"), "Normal"),
            labeled(card(R.drawable.pc_bg_card_clickable, "Cartão tocável").apply { isClickable = true }.pressed(), "Tocável · pressionado"),
        )
    }

    private fun icons(): View {
        val all = listOf(
            R.drawable.ic_home to "home", R.drawable.ic_music to "music", R.drawable.ic_apps to "apps",
            R.drawable.ic_connectivity to "connectivity", R.drawable.ic_settings to "settings",
            R.drawable.ic_bluetooth to "bluetooth", R.drawable.ic_wifi to "wifi",
            R.drawable.ic_navigation to "navigation", R.drawable.ic_carplay to "carplay",
            R.drawable.ic_chevron_right to "chevron", R.drawable.ic_play to "play", R.drawable.ic_pause to "pause",
            R.drawable.ic_skip_previous to "previous", R.drawable.ic_skip_next to "next",
            R.drawable.ic_usb to "usb", R.drawable.ic_radio to "radio", R.drawable.ic_video to "video",
            R.drawable.ic_movie to "movie", R.drawable.ic_map to "map", R.drawable.ic_camera to "camera",
            R.drawable.ic_car to "car", R.drawable.ic_gauge to "gauge", R.drawable.ic_equalizer to "equalizer",
            R.drawable.ic_steering to "steering", R.drawable.ic_folder to "folder", R.drawable.ic_gear to "gear",
            R.drawable.ic_volume to "volume", R.drawable.ic_display to "display", R.drawable.ic_clock to "clock",
            R.drawable.ic_diagnostics to "diagnostics",
        )
        val views = all.map { (icon, name) ->
            labeled(
                ImageView(this).apply {
                    setImageResource(icon)
                    imageTintList = getColorStateList(R.color.pc_text_primary)
                    layoutParams = LinearLayout.LayoutParams(px(R.dimen.pc_icon_m), px(R.dimen.pc_icon_m))
                },
                name,
            )
        }
        return column(*views.chunked(10).map { row(it, spacing = dp(40)) }.toTypedArray())
    }

    private fun spacing(): View {
        val tokens = listOf(
            "xs · 4" to R.dimen.pc_space_xs, "s · 8" to R.dimen.pc_space_s, "m · 16" to R.dimen.pc_space_m,
            "l · 24" to R.dimen.pc_space_l, "xl · 32" to R.dimen.pc_space_xl, "xxl · 48" to R.dimen.pc_space_xxl,
            "toque mín. · 64" to R.dimen.pc_touch_min,
        )
        return row(
            tokens.map { (name, size) ->
                labeled(
                    View(this).apply {
                        setBackgroundColor(getColor(R.color.pc_accent))
                        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
                    },
                    "$name dp",
                )
            },
        )
    }

    // ---------- Montagem ----------

    private fun LinearLayout.addSection(title: String, body: View) {
        addView(
            text(title, R.style.PcText_Title).apply { setTextColor(getColor(R.color.pc_accent)) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(R.dimen.pc_space_xl) },
        )
        addView(body, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(R.dimen.pc_space_m) })
    }

    private fun row(vararg views: View) = row(views.toList())

    private fun row(views: List<View>, spacing: Int = px(R.dimen.pc_gutter)) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { index, view ->
            val params = (view.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(WRAP, WRAP)
            if (index > 0) params.marginStart = spacing
            addView(view, params)
        }
    }

    private fun column(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { index, view ->
            val params = (view.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(MATCH, WRAP)
            if (index > 0) params.topMargin = px(R.dimen.pc_space_m)
            addView(view, params)
        }
    }

    /** O componente com uma legenda embaixo. */
    private fun labeled(view: View, caption: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val params = (view.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(WRAP, WRAP)
        addView(view, params)
        addView(
            text(caption, R.style.PcText_Caption),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(R.dimen.pc_space_s) },
        )
        layoutParams = LinearLayout.LayoutParams(
            if (params.width == MATCH) MATCH else WRAP,
            WRAP,
        )
    }

    private fun swatch(@ColorRes color: Int): View = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(getColor(color))
            setStroke(dp(1), getColor(R.color.pc_outline))
            cornerRadius = px(R.dimen.pc_radius_s).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(136), dp(64))
    }

    private fun button(@StyleRes style: Int, label: String) = Button(this, null, 0, style).apply { text = label }

    private fun text(value: CharSequence, @StyleRes style: Int) = TextView(this, null, 0, style).apply { text = value }

    private fun <T : View> T.pressed(): T = apply { isPressed = true }

    private fun <T : View> T.disabled(): T = apply { isEnabled = false }

    private fun hex(@ColorRes color: Int) = "#%06X".format(getColor(color) and 0xFFFFFF)

    private fun px(@DimenRes dimen: Int) = resources.getDimensionPixelSize(dimen)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
