package com.premiumcar.launcher.ui.apps

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.view.doOnLayout
import com.premiumcar.launcher.R
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.launcher.AllApps
import com.premiumcar.launcher.launcher.AppItem
import com.premiumcar.launcher.launcher.AppLauncher
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.FavoriteKey
import com.premiumcar.launcher.launcher.Favorites
import com.premiumcar.launcher.settings.FavoritesStore
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.ui.components.ShortcutTile
import com.premiumcar.launcher.ui.screens.ScreenBuilder
import com.premiumcar.launcher.ui.screens.ShortcutAction
import com.premiumcar.launcher.ui.screens.TabContent

/**
 * Aba Aplicativos (0.4.4): seletor "Favoritos | Todos" e edição dos favoritos.
 *
 * - **Favoritos:** os 12 favoritos em 3 linhas de 4. A aba sempre abre aqui.
 * - **Todos:** os apps da central em ordem alfabética, com ícones de traço ([AllApps]), nos
 *   mesmos atalhos dos Favoritos (4 colunas, 3 linhas por tela) e com barra de rolagem. A lista
 *   é montada em segundo plano no primeiro toque e refeita ao voltar de outro app. Não carrega
 *   ícones de outros apps: só lê os nomes.
 * - **Editar** (botão no alto, só em Favoritos): toque num lugar → escolha o app na lista. Um app
 *   que já é favorito troca de lugar com o tocado, então são sempre 12, sem repetição. Cada troca
 *   já fica salva; "Restaurar padrão" volta aos 12 de antes e "Concluir" sai da edição.
 */
class AppsScreen(
    private val activity: Activity,
    private val launcher: AppLauncher,
    private val host: Host,
    builder: ScreenBuilder,
) {
    /** O que a MainActivity faz pelos atalhos: toque, estado (disponível ou não) e limpeza. */
    interface Host {
        fun registerTile(tile: ShortcutTile, action: ShortcutAction)
        fun forgetTiles(tiles: Collection<View>)
        fun refreshAvailability()
    }

    enum class Mode { FAVORITES, ALL, EDIT, PICKER }

    val view: View = builder.inflate(R.layout.screen_apps)

    private val title: TextView = view.findViewById(R.id.screen_title)
    private val editButton: View = view.findViewById(R.id.apps_edit)
    private val restoreButton: View = view.findViewById(R.id.apps_restore)
    private val doneButton: View = view.findViewById(R.id.apps_done)
    private val cancelButton: View = view.findViewById(R.id.apps_cancel)
    private val selector: View = view.findViewById(R.id.apps_selector)
    private val favoritesTab: TextView = view.findViewById(R.id.apps_tab_favorites)
    private val allTab: TextView = view.findViewById(R.id.apps_tab_all)
    private val pages: View = view.findViewById(R.id.apps_pages)
    private val favoritesPage: LinearLayout = view.findViewById(R.id.apps_favorites)
    private val allPage: View = view.findViewById(R.id.apps_all)
    private val allList: LinearLayout = view.findViewById(R.id.apps_all_list)

    private val gutter = activity.resources.getDimensionPixelSize(R.dimen.pc_gutter)

    private val store = FavoritesStore.create(activity)

    var mode = Mode.FAVORITES
        private set

    /** Os 12 favoritos, na ordem da grade. */
    var favorites: List<FavoriteKey> = store.load()
        private set

    private var pickerSlot = -1
    private val registeredTiles = mutableListOf<View>()
    private var items: List<AppItem>? = null
    private var loading = false

    init {
        favoritesTab.setOnClickListener { showFavorites() }
        allTab.setOnClickListener { showAll() }
        editButton.setOnClickListener { startEditing() }
        doneButton.setOnClickListener { finishEditing() }
        restoreButton.setOnClickListener { change(store.defaultList) }
        cancelButton.setOnClickListener { setMode(Mode.EDIT) }
        renderFavorites(editing = false)
        if (favorites.any { it is FavoriteKey.Other }) host.refreshAvailability()
        setMode(Mode.FAVORITES)
    }

    /** Página "Favoritos" (ao entrar na aba); sai da edição, se estiver nela. */
    fun showFavorites() {
        if (mode == Mode.EDIT || mode == Mode.PICKER) {
            renderFavorites(editing = false)
            host.refreshAvailability()
        }
        setMode(Mode.FAVORITES)
    }

    /** Página "Todos". */
    fun showAll() = setMode(Mode.ALL)

    /** Ao voltar de outro app: refaz a lista, se ela já foi montada. */
    fun refresh() {
        if (items != null) reload()
    }

    /** Voltar: da escolha para a edição; da edição para os favoritos. Nos demais, quem decide é a tela. */
    fun onBack(): Boolean = when (mode) {
        Mode.PICKER -> {
            setMode(Mode.EDIT)
            true
        }
        Mode.EDIT -> {
            showFavorites()
            true
        }
        else -> false
    }

    private fun startEditing() {
        renderFavorites(editing = true)
        setMode(Mode.EDIT)
    }

    private fun finishEditing() = showFavorites()

    private fun openPicker(slot: Int) {
        pickerSlot = slot
        setMode(Mode.PICKER)
    }

    private fun pick(item: AppItem) {
        change(Favorites.replace(favorites, pickerSlot, item.favoriteKey))
        setMode(Mode.EDIT)
    }

    /** Aplica e salva na hora (decisão do usuário): nada se perde se a central desligar. */
    private fun change(list: List<FavoriteKey>) {
        favorites = list
        store.save(list)
        renderFavorites(editing = true)
    }

    private fun setMode(newMode: Mode) {
        mode = newMode
        val editing = newMode == Mode.EDIT || newMode == Mode.PICKER
        title.text = when (newMode) {
            Mode.FAVORITES, Mode.ALL -> activity.getString(R.string.nav_apps)
            Mode.EDIT -> activity.getString(R.string.apps_edit_title)
            Mode.PICKER -> activity.getString(R.string.apps_pick_title, describe(favorites[pickerSlot]).label)
        }
        selector.visibility = if (editing) View.GONE else View.VISIBLE
        editButton.visibility = if (newMode == Mode.FAVORITES) View.VISIBLE else View.GONE
        restoreButton.visibility = if (newMode == Mode.EDIT) View.VISIBLE else View.GONE
        doneButton.visibility = if (newMode == Mode.EDIT) View.VISIBLE else View.GONE
        cancelButton.visibility = if (newMode == Mode.PICKER) View.VISIBLE else View.GONE
        favoritesTab.isSelected = newMode == Mode.FAVORITES
        allTab.isSelected = newMode == Mode.ALL
        val list = newMode == Mode.ALL || newMode == Mode.PICKER
        favoritesPage.visibility = if (list) View.GONE else View.VISIBLE
        allPage.visibility = if (list) View.VISIBLE else View.GONE
        if (list) {
            allPage.scrollTo(0, 0)
            items?.let(::renderAll) ?: reload()
        }
    }

    // ---------- Favoritos ----------

    private class Look(@DrawableRes val icon: Int, val label: String, val action: ShortcutAction)

    private fun describe(key: FavoriteKey): Look = when (key) {
        is FavoriteKey.Known -> TabContent.knownApp(key.appId)!!.shortcut.let { Look(it.icon, activity.getString(it.label), it.action) }
        is FavoriteKey.Other -> Look(
            R.drawable.ic_app_generic,
            key.label,
            ShortcutAction.Open(AppTarget.App(key.packageName, labelHints = listOf(key.label))),
        )
    }

    /** Grade 3 × 4. Fora da edição, cada atalho abre o app; na edição, abre a escolha do lugar. */
    private fun renderFavorites(editing: Boolean) {
        host.forgetTiles(registeredTiles)
        registeredTiles.clear()
        favoritesPage.removeAllViews()
        favorites.chunked(TabContent.APPS_COLUMNS).forEachIndexed { rowIndex, keys ->
            val cells = keys.mapIndexed { column, key ->
                val slot = rowIndex * TabContent.APPS_COLUMNS + column
                val look = describe(key)
                ShortcutTile(activity).apply {
                    bind(look.icon, look.label)
                    if (editing) {
                        isSelected = true
                        caption = activity.getString(R.string.tile_swap)
                        setOnClickListener { openPicker(slot) }
                    } else {
                        host.registerTile(this, look.action)
                        registeredTiles += this
                    }
                }
            }
            favoritesPage.addView(row(cells, TabContent.APPS_COLUMNS), rowParams(rowIndex, height = 0, weight = 1f))
        }
    }

    // ---------- Todos ----------

    private fun reload() {
        if (loading) return
        loading = true
        val looks = TabContent.appLooks(activity)
        Background.executor.execute {
            val result = AllApps.build(launcher.allEntries(), looks, MainActivity::class.java.name, R.drawable.ic_app_generic)
            activity.runOnUiThread {
                loading = false
                if (activity.isDestroyed) return@runOnUiThread
                items = result
                if (mode == Mode.ALL || mode == Mode.PICKER) renderAll(result)
            }
        }
    }

    /**
     * Em "Todos", cada atalho abre o app; na escolha, ele vai para o lugar tocado. As linhas têm a
     * altura das linhas dos Favoritos (3 por tela), medida depois que a aba ganha tamanho.
     */
    private fun renderAll(list: List<AppItem>) {
        if (pages.height == 0) {
            pages.doOnLayout { if (mode == Mode.ALL || mode == Mode.PICKER) renderAll(list) }
            return
        }
        val rowHeight = (pages.height - (TabContent.APPS_ROWS - 1) * gutter) / TabContent.APPS_ROWS
        allList.removeAllViews()
        val picking = mode == Mode.PICKER
        val current = if (picking) favorites[pickerSlot].id else null
        list.chunked(TabContent.APPS_COLUMNS).forEachIndexed { rowIndex, rowItems ->
            val cells = rowItems.map { item ->
                ShortcutTile(activity).apply {
                    bind(item.icon, item.label)
                    if (picking) {
                        val id = item.favoriteKey.id
                        isSelected = id == current
                        caption = if (favorites.any { it.id == id }) activity.getString(R.string.tile_favorite) else null
                        setOnClickListener { pick(item) }
                    } else {
                        setOnClickListener { open(item) }
                    }
                }
            }
            allList.addView(row(cells, TabContent.APPS_COLUMNS), rowParams(rowIndex, height = rowHeight, weight = 0f))
        }
    }

    private fun open(item: AppItem) {
        if (!launcher.launch(item.entry, activity)) {
            Toast.makeText(activity, activity.getString(R.string.app_not_found, item.label), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Grade ----------

    private fun row(cells: List<View>, columns: Int) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        for (column in 0 until columns) {
            addView(
                cells.getOrNull(column) ?: Space(activity),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (column > 0) marginStart = gutter
                },
            )
        }
    }

    private fun rowParams(index: Int, height: Int, weight: Float) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, weight).apply {
            if (index > 0) topMargin = gutter
        }
}
