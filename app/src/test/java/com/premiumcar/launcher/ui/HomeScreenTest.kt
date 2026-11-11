package com.premiumcar.launcher.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.github.takahirom.roborazzi.captureRoboImage
import com.premiumcar.launcher.R
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.launcher.AppTarget
import com.premiumcar.launcher.launcher.CarApps
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.player.MusicStorage
import com.premiumcar.launcher.player.MusicVolume
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.ui.components.BottomNavBar
import com.premiumcar.launcher.ui.components.ShortcutTile
import com.premiumcar.launcher.ui.components.SwipeTabsLayout
import com.premiumcar.launcher.ui.screens.TabContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.util.DataSource
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Launcher na área útil real da central: 1280 × 655 dp a 160 dpi (a barra do sistema
 * ocupa os outros 65 px), em português, simulando o Android 8.1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "pt-rBR-w1280dp-h655dp-land-mdpi")
class HomeScreenTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun setUp() {
        // A central roda em pt-BR (persist.sys.locale). Atenção: no Robolectric para a API 27,
        // os nomes de dias e meses do TextClock saem sempre em inglês (dados de idioma
        // simulados). Na central, o formatador nativo usa o português.
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        // Consultas "em segundo plano" rodam na hora, para o teste ser determinístico.
        Background.executor = Executor { it.run() }
        // Cada teste é um "processo novo" do player.
        MusicPlayer.resetForTests()
        MusicStorage.volumeSource = null
    }

    @After
    fun tearDown() {
        MusicPlayer.resetForTests()
        MusicStorage.volumeSource = null
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun controller(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { idle() }

    private fun launch(): MainActivity = controller().get()

    /** Pendrive simulado: três músicas soltas na raiz (como o do usuário) e uma pasta. */
    private fun insertPendrive(): List<File> {
        val root = temp.newFolder("1234-ABCD")
        File(root, "Viagem").mkdir()
        val songs = listOf(
            Triple("01 - Abertura.mp3", "Abertura", "Banda Exemplo"),
            Triple("02 - Estrada Longa.mp3", "Estrada Longa", "Banda Exemplo"),
            Triple("10 - Final.flac", "Final", "Outro Artista"),
        )
        val files = songs.map { (name, title, artist) ->
            File(root, name).apply {
                writeText("x")
                val source = DataSource.toDataSource(path)
                ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(225_000, 0))
                ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_TITLE, title)
                ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_ARTIST, artist)
                ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_DURATION, "225000")
            }
        }
        MusicStorage.volumeSource = { listOf(MusicVolume(root, "Pendrive")) }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        return files
    }

    /** Simula um app instalado com uma entrada na lista de apps. */
    private fun install(packageName: String, activity: String = "$packageName.Main"): ComponentName {
        val component = ComponentName(packageName, activity)
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
        return component
    }

    /** Simula a central: todos os apps e telas de sistema que os atalhos usam. */
    private fun installCarApps() {
        TabContent.allTargets.forEach { target ->
            when (target) {
                is AppTarget.App -> install(target.packageName, "${target.packageName}.${target.labelHints.firstOrNull() ?: "Main"}")
                is AppTarget.Home -> {
                    val component = ComponentName(target.packageName, "${target.packageName}.Launcher")
                    val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
                    pm.addActivityIfNotPresent(component)
                    pm.addIntentFilterForActivity(
                        component,
                        IntentFilter(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        },
                    )
                }
                is AppTarget.SystemScreen -> {
                    val component = ComponentName("com.android.settings", "com.android.settings.${target.action}")
                    val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
                    pm.addActivityIfNotPresent(component)
                    pm.addIntentFilterForActivity(component, IntentFilter(target.action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
                }
            }
        }
    }

    private fun MainActivity.nav() = findViewById<BottomNavBar>(R.id.home_nav)

    private fun MainActivity.go(destination: Destination) {
        nav().itemView(destination)!!.performClick()
        idle()
    }

    private fun visibleViews(root: View): List<View> = buildList {
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return
            add(view)
            if (view is ViewGroup) (0 until view.childCount).forEach { collect(view.getChildAt(it)) }
        }
        collect(root)
    }

    private fun MainActivity.tile(label: String): ShortcutTile =
        visibleViews(findViewById(android.R.id.content)).filterIsInstance<ShortcutTile>().first { it.labelText == label }

    private fun assertTouchTargets(activity: MainActivity, where: String) {
        val minimum = activity.resources.getDimensionPixelSize(R.dimen.pc_touch_min)
        val clickable = visibleViews(activity.findViewById(android.R.id.content)).filter { it.isClickable }
        assertTrue("$where: nenhum elemento tocável encontrado", clickable.size >= 6)
        clickable.forEach { view ->
            val name = runCatching { activity.resources.getResourceEntryName(view.id) }.getOrNull()
                ?: (view as? ShortcutTile)?.labelText ?: view.javaClass.simpleName
            assertTrue(
                "$where: $name tem ${view.width}×${view.height} px (mínimo $minimum)",
                view.width >= minimum && view.height >= minimum,
            )
        }
    }

    // ---------- Home ----------

    @Test
    fun `home ocupa a area util e gera a captura de referencia`() {
        installCarApps()
        val root = launch().findViewById<View>(android.R.id.content)
        assertEquals(1280, root.width)
        assertEquals(655, root.height)
        root.captureRoboImage("build/outputs/roborazzi/home_reference.png")
    }

    @Test
    fun `todo elemento tocavel da home tem pelo menos 64 dp`() = assertTouchTargets(launch(), "Home")

    @Test
    fun `barra inferior tem os cinco destinos e a home selecionada`() {
        val nav = launch().nav()
        assertEquals(Destination.entries.size, nav.childCount)
        assertTrue(nav.itemView(Destination.HOME)!!.isSelected)
        Destination.entries.filter { it != Destination.HOME }.forEach {
            assertFalse("$it não deveria estar selecionado", nav.itemView(it)!!.isSelected)
        }
    }

    @Test
    fun `barra inferior mostra so icones e guarda o nome para acessibilidade`() {
        val activity = launch()
        Destination.entries.forEach { destination ->
            val item = activity.nav().itemView(destination)!!
            assertEquals(activity.getString(destination.labelRes), item.contentDescription)
            assertTrue("$destination não deveria ter rótulo visível", visibleViews(item).none { it is TextView })
        }
    }

    @Test
    fun `atalho do CarPlay abre o ZLINK trazendo a tarefa existente`() {
        val zlink = install("com.zjinnova.zlink")
        val activity = launch()
        val tile = activity.findViewById<ShortcutTile>(R.id.shortcut_carplay)
        assertEquals("CarPlay", tile.labelText)
        assertTrue(tile.isEnabled)
        tile.performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(zlink, started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
    }

    @Test
    fun `cartao de navegacao abre o CarPlay e o de musica leva ao player`() {
        val zlink = install("com.zjinnova.zlink")
        val activity = launch()
        activity.findViewById<View>(R.id.navigation_open).performClick()
        assertEquals(zlink, shadowOf(activity).nextStartedActivity.component)
        activity.findViewById<View>(R.id.card_music).performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.nav().itemView(Destination.MUSIC)!!.isSelected)
    }

    @Test
    fun `app ausente deixa o atalho desativado e o cartao avisa`() {
        val activity = launch()
        val bluetooth = activity.findViewById<ShortcutTile>(R.id.shortcut_bluetooth)
        assertFalse(bluetooth.isEnabled)
        assertEquals("Não encontrado", bluetooth.caption)
        activity.findViewById<View>(R.id.card_navigation).performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("CarPlay não foi encontrado nesta central", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `sem musica escolhida o play da home leva a aba Musica`() {
        val activity = launch()
        assertEquals("Músicas do pendrive", activity.findViewById<TextView>(R.id.music_source).text)
        assertEquals("Toque para escolher uma música", activity.findViewById<TextView>(R.id.music_hint).text)
        activity.findViewById<View>(R.id.music_play).performClick()
        idle()
        assertTrue(activity.nav().itemView(Destination.MUSIC)!!.isSelected)
        assertFalse(MusicPlayer.state.playing)
    }

    @Test
    fun `aba Musica toca do pendrive e a home mostra e controla a musica`() {
        insertPendrive()
        installCarApps()
        val activity = launch()
        val root = activity.findViewById<View>(android.R.id.content)
        activity.go(Destination.MUSIC)

        val list = activity.findViewById<ListView>(R.id.music_list)
        assertEquals("Pasta e três músicas", 4, list.adapter.count)
        assertEquals("Pendrive", activity.findViewById<TextView>(R.id.music_path).text)
        // Toca "02 - Estrada Longa" (linha 2: a pasta vem primeiro).
        list.performItemClick(list.getChildAt(2), 2, 2)
        idle()
        assertTrue(MusicPlayer.state.playing)
        assertEquals("Estrada Longa", activity.findViewById<TextView>(R.id.player_title).text)
        assertEquals("Banda Exemplo", activity.findViewById<TextView>(R.id.player_artist).text)
        assertEquals("Pendrive · 2 de 3", activity.findViewById<TextView>(R.id.player_details).text)
        assertEquals("Pausar", activity.findViewById<ImageButton>(R.id.player_play).contentDescription)
        assertTouchTargets(activity, "Música com pendrive")
        root.captureRoboImage("build/outputs/roborazzi/tab_music_playing.png")

        activity.go(Destination.HOME)
        assertEquals("Estrada Longa", activity.findViewById<TextView>(R.id.music_source).text)
        assertEquals("Banda Exemplo", activity.findViewById<TextView>(R.id.music_hint).text)
        val play = activity.findViewById<ImageButton>(R.id.music_play)
        assertEquals("Pausar", play.contentDescription)
        root.captureRoboImage("build/outputs/roborazzi/home_playing.png")

        play.performClick()
        idle()
        assertFalse(MusicPlayer.state.playing)
        assertEquals("Reproduzir", play.contentDescription)
        activity.findViewById<View>(R.id.music_next).performClick()
        idle()
        assertEquals("Final", activity.findViewById<TextView>(R.id.music_source).text)
        assertFalse("Pausado continua pausado ao trocar de música", MusicPlayer.state.playing)
    }

    @Test
    fun `sem permissao a aba Musica explica e pede o acesso`() {
        val activity = launch()
        activity.go(Destination.MUSIC)
        val action = activity.findViewById<TextView>(R.id.music_empty_action)
        assertEquals("Permitir acesso", action.text)
        action.performClick()
        val request = shadowOf(activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), request.requestedPermissions.toList())
    }

    // ---------- Tela inicial ----------

    @Test
    fun `launcher pode ser escolhido como tela inicial e mantem o icone`() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(home.any { it.activityInfo.name == MainActivity::class.java.name })
        val launcherIcon = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        assertTrue(launcherIcon.any { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test
    fun `botao Home do sistema volta a Home de qualquer aba`() {
        val controller = controller()
        val activity = controller.get()
        activity.go(Destination.SETTINGS)
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        idle()
        assertTrue(activity.nav().itemView(Destination.HOME)!!.isSelected)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_content).visibility)
    }

    @Test
    fun `notificacao do player abre a aba Musica`() {
        val controller = controller()
        controller.newIntent(Intent().putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MUSIC))
        idle()
        assertTrue(controller.get().nav().itemView(Destination.MUSIC)!!.isSelected)
    }

    @Test
    fun `configuracoes leva a escolha da tela inicial e ao launcher original`() {
        installCarApps()
        val activity = launch()
        activity.go(Destination.SETTINGS)
        activity.tile("Tela inicial").performClick()
        assertEquals(Settings.ACTION_HOME_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        activity.tile("Launcher original").performClick()
        val original = shadowOf(activity).nextStartedActivity
        assertEquals("com.android.launcher3", original.component?.packageName)
        assertTrue(original.hasCategory(Intent.CATEGORY_HOME))
    }

    // ---------- Abas ----------

    @Test
    fun `barra troca de aba e voltar retorna a home sem fechar o launcher`() {
        val activity = launch()
        activity.go(Destination.MUSIC)
        assertTrue(activity.nav().itemView(Destination.MUSIC)!!.isSelected)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_content).visibility)

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertTrue(activity.nav().itemView(Destination.HOME)!!.isSelected)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_content).visibility)

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertFalse("Voltar na Home não deve fechar o launcher", activity.isFinishing)
    }

    @Test
    fun `atalhos aplicativos e configuracoes da home levam as abas`() {
        val activity = launch()
        activity.findViewById<View>(R.id.shortcut_apps).performClick()
        assertTrue(activity.nav().itemView(Destination.APPS)!!.isSelected)
        activity.go(Destination.HOME)
        activity.findViewById<View>(R.id.shortcut_settings).performClick()
        assertTrue(activity.nav().itemView(Destination.SETTINGS)!!.isSelected)
    }

    @Test
    fun `cada aba cabe na tela, respeita 64 dp e gera captura`() {
        installCarApps()
        val activity = launch()
        val root = activity.findViewById<View>(android.R.id.content)
        Destination.entries.filter { it != Destination.HOME }.forEach { destination ->
            activity.go(destination)
            assertTouchTargets(activity, destination.name)
            val missing = visibleViews(root).filterIsInstance<ShortcutTile>().filterNot { it.isEnabled }
            assertTrue("$destination: atalhos desativados ${missing.map { it.labelText }}", missing.isEmpty())
            root.captureRoboImage("build/outputs/roborazzi/tab_${destination.name.lowercase()}.png")
        }
    }

    @Test
    fun `configuracoes abre a tela de som do Android`() {
        val settings = ComponentName("com.android.settings", "com.android.settings.SoundSettings")
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        pm.addActivityIfNotPresent(settings)
        pm.addIntentFilterForActivity(
            settings,
            IntentFilter(Settings.ACTION_SOUND_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
        val activity = launch()
        activity.go(Destination.SETTINGS)
        val sound = activity.tile("Som")
        assertTrue(sound.isEnabled)
        sound.performClick()
        assertEquals(Settings.ACTION_SOUND_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun `configuracoes abre o Nyx Diagnostico`() {
        val activity = launch()
        activity.go(Destination.SETTINGS)
        activity.tile("Nyx Diagnóstico").performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals("com.premiumcar.launcher.diagnostics.ui.DiagnosticsActivity", started.component?.className)
    }

    // ---------- Deslizar entre abas (0.4.1) ----------

    private fun MainActivity.container() = findViewById<SwipeTabsLayout>(R.id.screen_container)

    private fun MainActivity.selected(): Destination = Destination.entries.single { nav().itemView(it)!!.isSelected }

    private fun MainActivity.visibleScreen(): View =
        container().let { c -> (0 until c.childCount).map(c::getChildAt).single { it.visibility == View.VISIBLE } }

    /** Centro de [view] em coordenadas do contêiner das abas. */
    private fun MainActivity.centerInContainer(view: View): Pair<Float, Float> {
        val origin = IntArray(2).also { container().getLocationInWindow(it) }
        val position = IntArray(2).also { view.getLocationInWindow(it) }
        return (position[0] - origin[0] + view.width / 2f) to (position[1] - origin[1] + view.height / 2f)
    }

    /** Arrasta um dedo de (fromX, fromY) a (toX, toY), em coordenadas do contêiner das abas. */
    private fun MainActivity.drag(
        fromX: Float,
        toX: Float,
        fromY: Float = 300f,
        toY: Float = fromY,
        durationMs: Long = 250,
        steps: Int = 10,
    ) {
        val target = container()
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float, time: Long) {
            val event = MotionEvent.obtain(start, time, action, x, y, 0)
            target.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, fromX, fromY, start)
        for (i in 1..steps) {
            val f = i / steps.toFloat()
            send(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * f, fromY + (toY - fromY) * f, start + durationMs * i / steps)
        }
        send(MotionEvent.ACTION_UP, toX, toY, start + durationMs)
        idle()
    }

    @Test
    fun `deslizar troca para a aba vizinha na ordem da barra e para nas pontas`() {
        val activity = launch()
        activity.drag(1000f, 500f) // dedo para a esquerda: próxima aba
        assertEquals(Destination.MUSIC, activity.selected())
        activity.drag(1000f, 500f)
        assertEquals(Destination.APPS, activity.selected())
        activity.drag(500f, 1000f) // dedo para a direita: aba anterior
        assertEquals(Destination.MUSIC, activity.selected())
        activity.drag(500f, 1000f)
        assertEquals(Destination.HOME, activity.selected())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_content).visibility)

        activity.drag(500f, 1000f)
        assertEquals("Na Home não há aba anterior", Destination.HOME, activity.selected())
        activity.go(Destination.SETTINGS)
        activity.drag(1000f, 500f)
        assertEquals("Em Configurações o deslize não dá a volta", Destination.SETTINGS, activity.selected())
        assertNull("Nenhum atalho foi acionado pelos deslizes", shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `nova aba termina a transicao no lugar e a anterior fica sem deslocamento`() {
        val activity = launch()
        activity.drag(1000f, 500f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val screen = activity.visibleScreen()
        assertEquals(0f, screen.translationX, 0.01f)
        assertEquals(1f, screen.alpha, 0.01f)
        val home = activity.findViewById<View>(R.id.home_content)
        assertEquals(0f, home.translationX, 0.01f)
        assertEquals(1f, home.alpha, 0.01f)
    }

    @Test
    fun `arrasto vertical, curto ou lento e pequeno nao troca de aba`() {
        val activity = launch()
        activity.drag(640f, 640f, fromY = 100f, toY = 450f) // vertical
        activity.drag(1000f, 960f) // 40 px
        activity.drag(1000f, 900f, durationMs = 2_000) // 100 px, devagar
        assertEquals(Destination.HOME, activity.selected())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals("A Home volta ao lugar", 0f, activity.findViewById<View>(R.id.home_content).translationX, 0.01f)
    }

    @Test
    fun `gesto rapido troca de aba com distancia menor`() {
        val activity = launch()
        activity.drag(1000f, 900f, durationMs = 80, steps = 4) // 100 px em 80 ms
        assertEquals(Destination.MUSIC, activity.selected())
    }

    @Test
    fun `deslize que comeca num botao nao aciona o botao, mas o toque continua funcionando`() {
        val zlink = install("com.zjinnova.zlink")
        val activity = launch()
        val (x, y) = activity.centerInContainer(activity.findViewById(R.id.navigation_open))
        activity.drag(x, x - 500f, fromY = y)
        assertNull("O deslize não pode abrir o CarPlay", shadowOf(activity).nextStartedActivity)
        assertEquals(Destination.MUSIC, activity.selected())

        activity.go(Destination.HOME)
        activity.drag(x, x, fromY = y, durationMs = 80, steps = 1) // toque
        assertEquals(zlink, shadowOf(activity).nextStartedActivity?.component)
    }

    @Test
    fun `lista de musicas continua rolando na vertical sem trocar de aba`() {
        insertPendrive()
        val activity = launch()
        activity.go(Destination.MUSIC)
        val list = activity.findViewById<ListView>(R.id.music_list)
        val (x, y) = activity.centerInContainer(list)
        activity.drag(x, x + 30f, fromY = y + 100f, toY = y - 150f)
        assertEquals(Destination.MUSIC, activity.selected())
    }

    // ---------- Player original (0.4.1 e 0.4.2) ----------

    @Test
    fun `aba Musica oferece so BT Musica e Radio e o Video desativado aparece como Desativado`() {
        installCarApps()
        // O usuário pode desativar o player original (exceção à seção 9); Vídeo é o mesmo app.
        RuntimeEnvironment.getApplication().packageManager.setApplicationEnabledSetting(
            CarApps.USB_MUSIC.packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            0,
        )
        val activity = launch()
        activity.go(Destination.MUSIC)
        val sources = listOf(R.id.source_1, R.id.source_2, R.id.source_3).map { id ->
            activity.findViewById<View>(id).takeIf { it.visibility == View.VISIBLE }
                ?.findViewById<TextView>(R.id.source_label)?.text?.toString()
        }
        assertEquals("Sem o atalho do player USB do fabricante (0.4.2)", listOf("BT Música", "Rádio", null), sources)

        activity.go(Destination.APPS)
        val video = activity.tile(activity.getString(R.string.app_video))
        assertFalse(video.isEnabled)
        assertEquals("Desativado", video.caption)
        val labels = visibleViews(activity.findViewById(android.R.id.content)).filterIsInstance<ShortcutTile>().map { it.labelText }
        assertFalse("Aplicativos sem \"Música USB\": $labels", labels.contains("Música USB"))
    }

    @Test
    fun `aba Aplicativos completa a grade com a Play Store`() {
        installCarApps()
        val activity = launch()
        activity.go(Destination.APPS)
        val labels = visibleViews(activity.findViewById(android.R.id.content))
            .filterIsInstance<ShortcutTile>().map { it.labelText.toString() }
        assertEquals("3 linhas de 4 atalhos, sem lugar vazio (0.4.3): $labels", 12, labels.size)
        assertEquals("Play Store", labels.last())
        assertTrue(activity.tile("Play Store").isEnabled)
    }

    // ---------- Aplicativos → Todos (0.4.4) ----------

    /** Os apps com ícone da central, com os nomes do sistema (relatório de 11/11/2026). */
    private fun installCentralApps() {
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        listOf(
            Triple("com.hcn.screensaver", "ScreenSaver", ""),
            Triple("com.hcn.autoupdate", "Update", "atualização"),
            Triple("com.hcn.canupgrade", "CanUpgrade", "Atualização de Rede Can"),
            Triple("com.hcn.mcuupgrade", "McuUpgrade", "Atualização Mcu"),
            Triple("com.auto.hcamera", "VideoInActivity", "Av In"),
            Triple("com.autochips.bluetooth", "MainBluetoothActivity", "Bluetooth"),
            Triple("com.autochips.bluetooth", "BtMusicActivity", "BT Música"),
            Triple("com.android.chrome", "Main", "Chrome"),
            Triple("com.hcn.swc", "MainActivity", "Comandos de volante"),
            Triple("com.android.settings", "Settings", "Configurar"),
            Triple("com.hcn.autosetting", "SettingActivity", "Configurações Do Veículo"),
            Triple("com.hcn.autoeq", "MainActivity", "DSP"),
            Triple("com.mediatek.filemanager", "FileManagerOperationActivity", "Gerenciador de arquivos"),
            Triple("com.google.android.googlequicksearchbox", "SearchActivity", "Google"),
            Triple("com.hcn.autocan", "CanActivity", "Informaçãodocarro"),
            Triple("com.hcn.autouserguide", "MainActivity", "Instruções"),
            Triple("com.google.android.apps.maps", "MapsActivity", "Maps"),
            Triple("com.hcn.AutoMediaPlayer", "MusicPlayerUiActivity", "Música"),
            Triple("com.android.vending", "AssetBrowserActivity", "Play Store"),
            Triple("com.hcn.autoradio", "RadioMain", "Rádio"),
            Triple("com.hcn.gallery", "MainActivity", "Scan de imagens"),
            Triple("com.hcn.themepicker", "MainActivity", "Theme"),
            Triple("com.hcn.AutoMediaPlayer", "VideoPlayerUiActivity", "Vídeo"),
            Triple("com.google.android.youtube", "HomeActivity", "YouTube"),
            Triple("com.zjinnova.zlink", "MainActivity", "ZLINK5"),
        ).forEach { (pkg, cls, label) ->
            val component = ComponentName(pkg, "$pkg.$cls")
            pm.addActivityIfNotPresent(component).nonLocalizedLabel = label
            pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }

    private val centralAllApps = listOf(
        "Arquivos", "Bluetooth", "BT Música", "Câmera", "CarPlay", "Chrome",
        "Comandos de volante", "Configurações do Android", "Equalizador", "Google", "Informações do carro", "Instruções",
        "Maps", "Nyx Diagnóstico", "Play Store", "Rádio", "Scan de imagens", "Veículo",
        "Vídeo", "YouTube",
    )

    @Test
    fun `aba Aplicativos abre em Favoritos e Todos mostra os apps da central em ordem`() {
        installCentralApps()
        val activity = launch()
        activity.go(Destination.APPS)
        val favorites = activity.findViewById<View>(R.id.apps_tab_favorites)
        val all = activity.findViewById<View>(R.id.apps_tab_all)
        assertTrue("Abre na página Favoritos", favorites.isSelected)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.apps_edit).visibility)
        assertEquals(12, activity.visibleTiles().size)
        val favoriteSize = activity.tile("Rádio").let { it.width to it.height }

        all.performClick()
        idle()
        assertTrue(all.isSelected)
        assertEquals("Editar só aparece em Favoritos", View.GONE, activity.findViewById<View>(R.id.apps_edit).visibility)
        assertEquals(
            "Sem Música USB, Theme, atualizadores e o próprio Nyx Car; nomes do Nyx Car para os conhecidos",
            centralAllApps,
            activity.visibleTiles(),
        )
        val sizes = visibleViews(activity.findViewById(android.R.id.content)).filterIsInstance<ShortcutTile>()
            .map { it.width to it.height }.distinct()
        assertEquals("Atalhos do mesmo tamanho dos Favoritos", listOf(favoriteSize), sizes)
        val list = activity.findViewById<View>(R.id.apps_all_list)
        val page = activity.findViewById<ScrollView>(R.id.apps_all)
        assertTrue("20 apps em 5 linhas: a lista rola (${list.height} de ${page.height} px)", list.height > page.height)
        assertTrue("Barra de rolagem sempre visível", page.isVerticalScrollBarEnabled && !page.isScrollbarFadingEnabled)
        assertTouchTargets(activity, "Aplicativos → Todos")
        activity.findViewById<View>(android.R.id.content).captureRoboImage("build/outputs/roborazzi/tab_apps_all.png")
        page.fullScroll(View.FOCUS_DOWN)
        idle()
        assertTrue("Rola até o fim", page.scrollY > 0)
        activity.findViewById<View>(android.R.id.content).captureRoboImage("build/outputs/roborazzi/tab_apps_all_end.png")

        activity.tile("CarPlay").performClick()
        assertEquals(
            ComponentName("com.zjinnova.zlink", "com.zjinnova.zlink.MainActivity"),
            shadowOf(activity).nextStartedActivity.component,
        )
    }

    @Test
    fun `aba Aplicativos volta para Favoritos ao sair e entrar de novo`() {
        installCentralApps()
        val activity = launch()
        activity.go(Destination.APPS)
        activity.findViewById<View>(R.id.apps_tab_all).performClick()
        idle()
        activity.go(Destination.HOME)
        activity.go(Destination.APPS)
        assertTrue(activity.findViewById<View>(R.id.apps_tab_favorites).isSelected)
        assertFalse(activity.findViewById<View>(R.id.apps_tab_all).isSelected)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.apps_all).visibility)
    }

    private val defaultFavorites = listOf(
        "Rádio", "Vídeo", "Bluetooth", "CarPlay", "Maps", "YouTube",
        "Câmera", "Informações do carro", "Equalizador", "Comandos de volante", "Arquivos", "Play Store",
    )

    private fun MainActivity.click(id: Int) {
        findViewById<View>(id).performClick()
        idle()
    }

    /** Título da aba visível (cada aba tem o seu `screen_title`). */
    private fun MainActivity.title(): String =
        visibleViews(findViewById(android.R.id.content)).filterIsInstance<TextView>().first { it.id == R.id.screen_title }.text.toString()

    @Test
    fun `editar favoritos troca um lugar, salva na hora e continua depois de reabrir`() {
        installCentralApps()
        val activity = launch()
        activity.go(Destination.APPS)
        assertEquals(defaultFavorites, activity.visibleTiles())

        activity.click(R.id.apps_edit)
        assertEquals("Editar favoritos", activity.title())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.apps_selector).visibility)
        val editing = visibleViews(activity.findViewById(android.R.id.content)).filterIsInstance<ShortcutTile>()
        assertEquals(12, editing.size)
        assertTrue(editing.all { it.isSelected && it.caption == "Trocar" })
        assertTouchTargets(activity, "Editar favoritos")
        activity.findViewById<View>(android.R.id.content).captureRoboImage("build/outputs/roborazzi/tab_apps_edit.png")

        activity.tile("Vídeo").performClick()
        idle()
        assertEquals("Trocar “Vídeo” por…", activity.title())
        assertEquals(centralAllApps, activity.visibleTiles())
        assertTrue("O lugar tocado fica marcado", activity.tile("Vídeo").isSelected)
        assertEquals("Favorito", activity.tile("CarPlay").caption)
        assertNull(activity.tile("Chrome").caption)
        assertTouchTargets(activity, "Escolha do favorito")
        activity.findViewById<View>(android.R.id.content).captureRoboImage("build/outputs/roborazzi/tab_apps_pick.png")

        activity.tile("Chrome").performClick()
        idle()
        assertEquals("Editar favoritos", activity.title())
        activity.click(R.id.apps_done)
        val expected = defaultFavorites.toMutableList().apply { this[1] = "Chrome" }
        assertEquals(expected, activity.visibleTiles())
        assertTrue(activity.tile("Chrome").isEnabled)
        activity.tile("Chrome").performClick()
        assertEquals(ComponentName("com.android.chrome", "com.android.chrome.Main"), shadowOf(activity).nextStartedActivity.component)

        // Outra abertura do app: os favoritos continuam.
        val again = launch()
        again.go(Destination.APPS)
        assertEquals(expected, again.visibleTiles())
    }

    @Test
    fun `escolher um favorito da grade troca os dois de lugar e restaurar volta ao padrao`() {
        installCentralApps()
        val activity = launch()
        activity.go(Destination.APPS)
        activity.click(R.id.apps_edit)
        activity.tile("Rádio").performClick()
        idle()
        activity.tile("CarPlay").performClick()
        idle()
        assertEquals(listOf("CarPlay", "Vídeo", "Bluetooth", "Rádio"), activity.visibleTiles().take(4))

        // Nome longo virando favorito: na escolha, com a legenda "Favorito", o atalho não cresce.
        activity.tile("Bluetooth").performClick()
        idle()
        activity.tile("Configurações do Android").performClick()
        idle()
        activity.tile("Maps").performClick()
        idle()
        val longName = activity.tile("Configurações do Android")
        assertEquals("Favorito", longName.caption)
        assertEquals(activity.tile("Chrome").height, longName.height)
        activity.click(R.id.apps_cancel)

        // Voltar: da escolha para a edição; da edição para os favoritos.
        activity.tile("Maps").performClick()
        idle()
        activity.onBackPressed()
        idle()
        assertEquals("Editar favoritos", activity.title())

        activity.click(R.id.apps_restore)
        assertEquals(defaultFavorites, activity.visibleTiles())
        activity.onBackPressed()
        idle()
        assertTrue(activity.findViewById<View>(R.id.apps_tab_favorites).isSelected)
        assertEquals(Destination.APPS, activity.selected())
    }

    private fun MainActivity.visibleTiles(): List<String> =
        visibleViews(findViewById(android.R.id.content)).filterIsInstance<ShortcutTile>().map { it.labelText.toString() }

    // ---------- Barra de status (0.4.2) ----------

    /** Insets do Android 8.1 (construtor interno): barra visível agora × altura estável. */
    private fun insets(visibleTop: Int, stableTop: Int): WindowInsets = ReflectionHelpers.callConstructor(
        WindowInsets::class.java,
        ClassParameter.from(Rect::class.java, Rect(0, visibleTop, 0, 0)),
        ClassParameter.from(Rect::class.java, null),
        ClassParameter.from(Rect::class.java, Rect(0, stableTop, 0, 0)),
        ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        ClassParameter.from(Boolean::class.javaPrimitiveType, false),
    )

    @Test
    fun `home nao pula quando o CarPlay esconde a barra de status`() {
        val activity = launch()
        @Suppress("DEPRECATION")
        val flags = activity.window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        assertTrue("A janela ocupa a tela toda, com margens estáveis", flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE != 0)

        val root = activity.findViewById<View>(R.id.home_root)
        root.dispatchApplyWindowInsets(insets(visibleTop = 65, stableTop = 65))
        assertEquals(65, root.paddingTop)
        // Volta do CarPlay: o primeiro quadro chega com a barra ainda escondida.
        root.dispatchApplyWindowInsets(insets(visibleTop = 0, stableTop = 65))
        assertEquals("O conteúdo fica no mesmo lugar", 65, root.paddingTop)
        // Firmware sem altura estável: vale a barra visível, como antes da correção.
        root.dispatchApplyWindowInsets(insets(visibleTop = 65, stableTop = 0))
        assertEquals(65, root.paddingTop)
    }
}
