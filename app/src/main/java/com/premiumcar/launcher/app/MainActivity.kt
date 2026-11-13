package com.premiumcar.launcher.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.annotation.StringRes
import com.premiumcar.launcher.R
import com.premiumcar.launcher.diagnostics.AppInfo
import com.premiumcar.launcher.diagnostics.ui.DiagnosticsActivity
import com.premiumcar.launcher.launcher.AppLauncher
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.CarApps
import com.premiumcar.launcher.launcher.CarPlayReturn
import com.premiumcar.launcher.perf.HomeMetrics
import com.premiumcar.launcher.perf.StartupTimeline
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.player.StoragePermission
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.system.BluetoothStatus
import com.premiumcar.launcher.system.WifiStatus
import com.premiumcar.launcher.ui.Destination
import com.premiumcar.launcher.ui.StableSystemBars
import com.premiumcar.launcher.ui.apps.AppsScreen
import com.premiumcar.launcher.ui.components.BottomNavBar
import com.premiumcar.launcher.ui.components.ShortcutTile
import com.premiumcar.launcher.ui.components.SwipeTabsLayout
import com.premiumcar.launcher.ui.music.MusicScreen
import com.premiumcar.launcher.ui.screens.ScreenBuilder
import com.premiumcar.launcher.ui.screens.ShortcutAction
import com.premiumcar.launcher.ui.screens.TabContent
import java.util.EnumMap

/**
 * Janela principal do launcher, em Views nativas (decisão D2), com navegação por estado
 * (decisão D3): a barra inferior troca a tela visível dentro de `screen_container`, e o deslize
 * horizontal troca para a aba vizinha, na ordem da barra ([SwipeTabsLayout], etapa 0.4.1).
 *
 * - A Home já vem no layout; as outras abas são montadas na primeira visita e depois só
 *   mostradas/escondidas (pela barra, troca instantânea; pelo deslize, transição curta).
 * - Quais apps existem é conferido em segundo plano, depois do primeiro quadro.
 * - Wi-Fi é escutado só enquanto a tela está visível.
 * - Pode ser a tela inicial (CATEGORY_HOME, etapa 0.4.0) se o usuário escolher em Apps padrão;
 *   o botão Home do sistema volta à Home de qualquer aba.
 * - O cartão de música e a aba Música mostram e controlam o player próprio ([MusicPlayer]).
 */
class MainActivity : Activity() {

    private lateinit var nav: BottomNavBar
    private lateinit var container: SwipeTabsLayout
    private lateinit var launcher: AppLauncher
    private lateinit var wifi: WifiStatus

    private val screens = EnumMap<Destination, View>(Destination::class.java)
    private var current = Destination.HOME

    /**
     * Atalho que abre um app, com o destino (para os estados "Não encontrado" e "Desativado").
     * Os atalhos compactos da aba Música somem quando o app não está disponível.
     */
    private class AppShortcut(val view: View, val target: AppTarget, val hideWhenUnavailable: Boolean)

    private val appShortcuts = mutableListOf<AppShortcut>()
    private var availability: Map<AppTarget, AppLauncher.Availability> = emptyMap()

    private var musicScreen: MusicScreen? = null
    private var appsScreen: AppsScreen? = null
    private var started = false
    private val playerListener: () -> Unit = { renderPlayer() }

    private var wifiConnected = false
    private var bluetooth = BluetoothStatus.State.UNKNOWN
    private var connectivityWifiLine: TextView? = null
    private var connectivityBluetoothLine: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StartupTimeline.markActivityCreated(SCREEN_NAME)) {
            StartupTimeline.trackFirstFrame(window.decorView)
            StartupTimeline.doOnFirstFrame {
                // Processo reaberto pelo Android em segundo plano: o tempo de abertura conta da
                // criação da tela, não do início do processo (que aconteceu bem antes).
                val background = StartupTimeline.startedWithoutScreen
                val millis = if (background) StartupTimeline.screenToFirstFrameMillis else StartupTimeline.startupMillis
                millis?.let { HomeMetrics.scheduleCapture(this, it, background) }
            }
        }
        setContentView(R.layout.activity_home)
        launcher = AppLauncher(this)
        MusicPlayer.init(this)
        // A Home não pula ao voltar de um app em tela cheia (CarPlay). Mudanças na altura da
        // barra vão para o diário: se aparecerem, o firmware mexe na altura "estável" também.
        StableSystemBars.apply(window, findViewById(R.id.home_root)) { old, new ->
            PlayerLog.add("Tela da Home: espaço da barra de status mudou de $old para $new px")
        }
        wifi = WifiStatus(this) { connected ->
            wifiConnected = connected
            renderConnectivity()
        }
        container = findViewById(R.id.screen_container)
        screens[Destination.HOME] = findViewById(R.id.home_content)
        setUpNavigation()
        setUpHome()
        setUpBack()
        handleIntent(intent)
        // Roda depois da primeira passagem de desenho: não atrasa a abertura.
        window.decorView.post { refreshAvailability() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Botão Home do sistema: volta à Home. Toque na notificação do player: aba Música. */
    private fun handleIntent(intent: Intent?) {
        when {
            intent?.getStringExtra(EXTRA_TAB) == TAB_MUSIC -> show(Destination.MUSIC)
            intent?.hasCategory(Intent.CATEGORY_HOME) == true -> show(Destination.HOME)
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        wifi.start()
        bluetooth = BluetoothStatus.read(this)
        renderConnectivity()
        MusicPlayer.addListener(playerListener)
        if (StoragePermission.isGranted(this)) MusicPlayer.loadSavedInfo()
        renderPlayer()
        musicScreen?.setActive(current == Destination.MUSIC)
    }

    override fun onRestart() {
        super.onRestart()
        // Ao voltar de outro app: algo pode ter sido instalado ou removido.
        refreshAvailability()
        appsScreen?.refresh()
    }

    override fun onResume() {
        super.onResume()
        CarPlayReturn.onLauncherResumed() // a tela de espera do CarPlay sai quando a Home volta
    }

    override fun onPause() {
        // Se for o ZLINK abrindo sozinho, a tela de espera do Nyx Car cobre a dele (0.4.7/0.4.8).
        CarPlayReturn.onLauncherPaused()
        super.onPause()
    }

    override fun onStop() {
        started = false
        wifi.stop()
        MusicPlayer.removeListener(playerListener)
        musicScreen?.setActive(false)
        super.onStop()
    }

    override fun onDestroy() {
        musicScreen?.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MusicScreen.REQUEST_STORAGE) {
            musicScreen?.onPermissionResult(StoragePermission.isGranted(this))
        }
    }

    // ---------- Navegação ----------

    private fun setUpNavigation() {
        nav = findViewById(R.id.home_nav)
        nav.setDestinations(Destination.entries)
        nav.selected = Destination.HOME
        nav.onDestinationClick = { show(it) }
        container.canSwipe = { neighbor(it) != null }
        container.onSwipe = { direction ->
            val target = neighbor(direction)
            if (target != null) show(target)
            target != null
        }
    }

    /** Aba vizinha na ordem da barra; `null` nas pontas (o deslize não dá a volta). */
    private fun neighbor(direction: SwipeTabsLayout.Direction): Destination? {
        val step = if (direction == SwipeTabsLayout.Direction.NEXT) 1 else -1
        return Destination.entries.getOrNull(current.ordinal + step)
    }

    /** Mostra [destination], montando a tela na primeira visita. */
    private fun show(destination: Destination) {
        if (destination == current) return
        val screen = screens[destination] ?: buildScreen(destination).also {
            screens[destination] = it
            container.addView(it)
        }
        screens[current]?.visibility = View.GONE
        screen.visibility = View.VISIBLE
        // A aba Aplicativos sempre abre na página "Favoritos" (e sai da edição, se estava nela).
        if (destination == Destination.APPS) appsScreen?.showFavorites()
        current = destination
        nav.selected = destination
        musicScreen?.setActive(started && destination == Destination.MUSIC)
    }

    /**
     * Voltar: na edição dos favoritos, um passo para trás; nas abas, para a Home; na Home, nada
     * (como em qualquer launcher).
     */
    private fun handleBack() {
        if (current == Destination.APPS && appsScreen?.onBack() == true) return
        if (current != Destination.HOME) show(Destination.HOME)
    }

    private fun setUpBack() {
        // Android 13+: o sistema usa este callback (gesto de voltar preditivo).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
    }

    @Deprecated("Usado até o Android 12 (a central roda o 8.1); no 13+ vale o callback de setUpBack.")
    @SuppressLint("GestureBackNavigation") // No 13+, o gesto chega pelo OnBackInvokedCallback de setUpBack.
    override fun onBackPressed() {
        handleBack()
    }

    // ---------- Home ----------

    private fun setUpHome() {
        registerTile(findViewById(R.id.shortcut_bluetooth), CarApps.BLUETOOTH)
        registerTile(findViewById(R.id.shortcut_carplay), CarApps.CARPLAY)
        findViewById<View>(R.id.shortcut_apps).setOnClickListener { show(Destination.APPS) }
        findViewById<View>(R.id.shortcut_settings).setOnClickListener { show(Destination.SETTINGS) }

        val openCarPlay = View.OnClickListener { open(CarApps.CARPLAY, R.string.shortcut_carplay) }
        findViewById<View>(R.id.card_navigation).setOnClickListener(openCarPlay)
        findViewById<View>(R.id.navigation_open).setOnClickListener(openCarPlay)

        // Cartão de música: o player próprio. Sem música escolhida, os botões levam à aba Música.
        findViewById<View>(R.id.card_music).setOnClickListener { show(Destination.MUSIC) }
        findViewById<View>(R.id.music_artwork).clipToOutline = true
        findViewById<View>(R.id.music_play).setOnClickListener {
            if (MusicPlayer.state.hasTrack) MusicPlayer.togglePlayPause() else show(Destination.MUSIC)
        }
        findViewById<View>(R.id.music_previous).setOnClickListener {
            if (MusicPlayer.state.hasTrack) MusicPlayer.previous() else show(Destination.MUSIC)
        }
        findViewById<View>(R.id.music_next).setOnClickListener {
            if (MusicPlayer.state.hasTrack) MusicPlayer.next() else show(Destination.MUSIC)
        }
    }

    // ---------- Abas ----------

    private fun buildScreen(destination: Destination): View {
        val builder = ScreenBuilder(this) { tile, shortcut -> registerTile(tile, shortcut.action) }
        return when (destination) {
            Destination.HOME -> error("A Home faz parte do layout principal")
            Destination.MUSIC -> MusicScreen(this, TabContent.musicSources) { view, shortcut ->
                val target = (shortcut.action as? ShortcutAction.Open)?.target
                // Ex.: player original desativado: o atalho "Música USB" some.
                if (target != null) registerShortcut(view, target, getString(shortcut.label), hideWhenUnavailable = true)
            }.also { musicScreen = it }.view
            Destination.APPS -> AppsScreen(this, launcher, appsHost, builder).also { appsScreen = it }.view
            Destination.CONNECTIVITY -> builder.screen(R.string.nav_connectivity) {
                tiles(TabContent.connectivity)
                val card = infoCard(builder, getString(R.string.connectivity_status_title))
                connectivityWifiLine = card.addLine()
                connectivityBluetoothLine = card.addLine()
                card.addLine().setText(R.string.connectivity_carplay_hint)
                block(card.view, weight = 2f)
                renderConnectivity()
            }
            Destination.SETTINGS -> builder.screen(R.string.nav_settings) {
                TabContent.settings.forEach { tiles(it) }
                val card = infoCard(builder, getString(R.string.about_title, AppInfo.versionName(this@MainActivity)))
                card.addLine().setText(R.string.about_home_hint)
                card.addLine(R.style.PcText_Caption).setText(R.string.about_soon)
                block(card.view, weight = 1f)
            }
        }
    }

    private class InfoCard(val view: View, private val lines: LinearLayout) {
        fun addLine(style: Int = R.style.PcText_Body): TextView {
            val line = TextView(lines.context, null, 0, style)
            val spacing = lines.resources.getDimensionPixelSize(R.dimen.pc_space_s)
            lines.addView(
                line,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = spacing },
            )
            return line
        }
    }

    private fun infoCard(builder: ScreenBuilder, title: String): InfoCard {
        val view = builder.inflate(R.layout.view_info_card)
        view.findViewById<TextView>(R.id.info_title).text = title
        return InfoCard(view, view.findViewById(R.id.info_lines))
    }

    // ---------- Atalhos ----------

    /** Os favoritos da aba Aplicativos são refeitos ao editar: os atalhos antigos saem da lista. */
    private val appsHost = object : AppsScreen.Host {
        override fun registerTile(tile: ShortcutTile, action: ShortcutAction) = this@MainActivity.registerTile(tile, action)
        override fun forgetTiles(tiles: Collection<View>) {
            appShortcuts.removeAll { it.view in tiles }
        }
        override fun refreshAvailability() = this@MainActivity.refreshAvailability()
    }

    private fun registerTile(tile: ShortcutTile, action: ShortcutAction) {
        when (action) {
            is ShortcutAction.Open -> registerTile(tile, action.target)
            is ShortcutAction.Go -> tile.setOnClickListener { show(action.destination) }
            ShortcutAction.Diagnostics -> tile.setOnClickListener {
                startActivity(Intent(this, DiagnosticsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun registerTile(tile: ShortcutTile, target: AppTarget) = registerShortcut(tile, target, tile.labelText)

    /** Liga um atalho (grande ou compacto) a um app; desativado (ou escondido) se o app não estiver disponível. */
    private fun registerShortcut(view: View, target: AppTarget, label: CharSequence, hideWhenUnavailable: Boolean = false) {
        view.setOnClickListener { open(target, label) }
        val shortcut = AppShortcut(view, target, hideWhenUnavailable)
        appShortcuts += shortcut
        applyAvailability(shortcut)
    }

    private fun open(target: AppTarget, @StringRes label: Int) = open(target, getString(label))

    private fun open(target: AppTarget, label: CharSequence) {
        if (!launcher.launch(target, this)) {
            Toast.makeText(this, getString(R.string.app_not_found, label), Toast.LENGTH_SHORT).show()
        }
    }

    /** Confere em segundo plano quais apps existem (e estão ativados) e ajusta os atalhos. */
    private fun refreshAvailability() {
        val targets = (appShortcuts.map { it.target } + TabContent.allTargets).distinct()
        Background.executor.execute {
            val result = targets.associateWith { launcher.availability(it) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                availability = result
                appShortcuts.forEach(::applyAvailability)
            }
        }
    }

    private fun applyAvailability(shortcut: AppShortcut) {
        val state = availability[shortcut.target] ?: return // ainda não conferido: fica ativo
        val available = state == AppLauncher.Availability.AVAILABLE
        if (shortcut.hideWhenUnavailable) shortcut.view.visibility = if (available) View.VISIBLE else View.GONE
        shortcut.view.isEnabled = available
        (shortcut.view as? ShortcutTile)?.caption = when (state) {
            AppLauncher.Availability.AVAILABLE -> null
            AppLauncher.Availability.DISABLED -> getString(R.string.tile_disabled)
            AppLauncher.Availability.MISSING -> getString(R.string.tile_not_found)
        }
    }

    // ---------- Música ----------

    /** Cartão de música da Home (e a aba Música, se já montada) conforme o player. */
    private fun renderPlayer() {
        val state = MusicPlayer.state
        findViewById<TextView>(R.id.music_source).text = state.title ?: getString(R.string.card_music_source)
        val hint = findViewById<TextView>(R.id.music_hint)
        hint.text = state.message ?: state.artist ?: state.folderLabel ?: getString(R.string.card_music_hint)
        hint.setTextColor(getColor(if (state.message != null) R.color.pc_warning else R.color.pc_text_secondary))
        val cover = findViewById<ImageView>(R.id.music_cover)
        cover.setImageBitmap(state.cover)
        cover.visibility = if (state.cover != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.music_placeholder).visibility = if (state.cover != null) View.GONE else View.VISIBLE
        val play = findViewById<ImageButton>(R.id.music_play)
        play.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        play.contentDescription = getString(if (state.playing) R.string.music_pause else R.string.music_play)
        musicScreen?.render(state)
    }

    // ---------- Conectividade ----------

    private fun renderConnectivity() {
        val wifiView = findViewById<TextView>(R.id.status_wifi)
        wifiView.setTextColor(getColor(if (wifiConnected) R.color.pc_text_primary else R.color.pc_text_secondary))
        wifiView.compoundDrawableTintList =
            ColorStateList.valueOf(getColor(if (wifiConnected) R.color.pc_accent else R.color.pc_text_secondary))
        findViewById<View>(R.id.status_bluetooth).visibility =
            if (bluetooth == BluetoothStatus.State.ON) View.VISIBLE else View.GONE

        connectivityWifiLine?.setText(if (wifiConnected) R.string.connectivity_wifi_on else R.string.connectivity_wifi_off)
        connectivityBluetoothLine?.setText(
            when (bluetooth) {
                BluetoothStatus.State.ON -> R.string.connectivity_bt_on
                BluetoothStatus.State.OFF -> R.string.connectivity_bt_off
                BluetoothStatus.State.UNKNOWN -> R.string.connectivity_bt_unknown
            },
        )
    }

    companion object {
        const val SCREEN_NAME = "Home (Views)"

        /** Extra do Intent que abre uma aba (usado pela notificação do player). */
        const val EXTRA_TAB = "com.premiumcar.launcher.TAB"
        const val TAB_MUSIC = "music"
    }
}
