package com.premiumcar.launcher.launcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.IntentFilter
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.system.Background
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Volta ao Nyx Car quando o CarPlay termina (0.4.5), tela de espera do Nyx Car (B1, 0.4.7) e
 * mensagens de fim e de queda da conexão (0.4.9) e o toque que fecha o ZLINK (0.4.10), com
 * avisos do sistema e tráfego de rede simulados. Nos testes, a thread do CarPlay é a principal (relógio do Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class CarPlayReturnTest {

    private val app = RuntimeEnvironment.getApplication()
    private var top: String? = ZLINK
    private var received = 0L
    private val screen = FakeWaitView()

    /** Tela de espera falsa: guarda o que seria mostrado. */
    private class FakeWaitView : CarPlayWaitView {
        var allowed = true
        var state: CarPlayWaitState? = null
        var parked = false
        var parkedNext: CarPlayWaitState? = null
        var tap: (() -> Unit)? = null
        var longPress: (() -> Unit)? = null

        /** Com `true`, o que espera o próximo quadro só roda em [drawFrame]. */
        var deferFrames = false
        private val frames = mutableListOf<() -> Unit>()
        override val visible get() = state != null
        override fun canShow() = allowed
        override fun show(state: CarPlayWaitState, onTap: () -> Unit, onLongPress: (() -> Unit)?) {
            this.state = state
            parked = false
            tap = onTap
            longPress = onLongPress
        }
        override fun park(next: CarPlayWaitState) {
            clear()
            parked = true
            parkedNext = next
        }
        override fun afterNextFrame(action: (FrameMarks) -> Unit) {
            if (deferFrames) frames += { action(FrameMarks()) } else action(FrameMarks())
        }
        fun drawFrame() {
            val pending = frames.toList()
            frames.clear()
            pending.forEach { it() }
        }
        override fun hide() {
            clear()
            parked = false
        }
        private fun clear() {
            state = null
            tap = null
            longPress = null
        }
    }

    @Before
    fun setUp() {
        Background.executor = Executor { it.run() }
        PlayerLog.resetForTests()
        ZlinkNotifications.resetForTests()
        CarPlayReturn.resetForTests()
        CarPlayReturn.topActivity = { top }
        CarPlayReturn.fastTop = { top }
        CarPlayReturn.traffic = { received }
        CarPlayReturn.waitView = screen
        CarPlayReturn.useMainThread = true
        CarPlayReturn.install(app)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
    }

    @After
    fun tearDown() = CarPlayReturn.resetForTests()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        idle()
    }

    private fun usb(connected: Boolean) {
        app.sendBroadcast(Intent("android.hardware.usb.action.USB_STATE").putExtra("connected", connected))
        idle()
    }

    private fun bluetooth(state: Int) {
        app.sendBroadcast(Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, state))
        idle()
    }

    private fun hfp(state: Int) {
        app.sendBroadcast(
            Intent("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED").putExtra(BluetoothProfile.EXTRA_STATE, state),
        )
        idle()
    }

    private fun started(): Intent? = shadowOf(app).nextStartedActivity

    /** O iPhone aparece no USB (Apple 05ac, como nas gravações). */
    private fun appleAttached() {
        val device = ReflectionHelpers.callConstructor(
            UsbDevice::class.java,
            ClassParameter.from(String::class.java, "/dev/bus/usb/001/044"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0x05AC),
            ClassParameter.from(Int::class.javaPrimitiveType, 0x12A8),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "Apple Inc."),
            ClassParameter.from(String::class.java, "iPhone"),
            ClassParameter.from(String::class.java, "1.0"),
            ClassParameter.from(String::class.java, null),
        )
        app.sendBroadcast(Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device))
        idle()
    }

    /** [millis] de tráfego a [bytesPerSecond], avançando o relógio em passos de 250 ms. */
    private fun stream(bytesPerSecond: Long, millis: Long) {
        repeat((millis / 250).toInt()) {
            received += bytesPerSecond / 4
            advance(250)
        }
    }

    /** Pelo cabo até o CarPlay pronto: tela aberta, Bluetooth desligado e vídeo chegando. */
    private fun connectByCable() {
        appleAttached()
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        stream(20L * 1024, 1_000)
        stream(1_200L * 1024, 1_000)
        advance(2_100)
    }

    private fun diary() = PlayerLog.read(app).map { it.second }

    @Test
    fun `cabo desconectado com o ZLINK na tela traz o Nyx Car de volta na hora`() {
        usb(connected = true)
        usb(connected = false)
        val intent = started()!!
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertTrue(diary().toString(), diary().contains(TERMINATED_CABLE))
    }

    @Test
    fun `sem a permissao de sobrepor o fim so traz o Nyx Car`() {
        screen.allowed = false
        usb(connected = true)
        usb(connected = false)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertNull(screen.state)
        assertTrue(diary().contains("CarPlay: terminou (cabo desconectado): o Nyx Car voltou para a frente"))
    }

    @Test
    fun `sem a espera contra mau contato, o cabo posto de volta e uma conexao nova`() {
        usb(connected = true)
        usb(connected = false)
        assertEquals("Volta na hora (pedido do usuário, 0.4.8)", MainActivity::class.java.name, started()?.component?.className)
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
    }

    @Test
    fun `com outro app na tela nada muda`() {
        top = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        usb(connected = true)
        usb(connected = false)
        advance(CarPlaySession.END_GRACE_MS + 100)
        assertNull(started())
        assertTrue(diary().any { it.contains("na tela estava com.google.android.apps.maps, nada mudou") })
    }

    @Test
    fun `sem fio - Bluetooth religando depois do CarPlay traz o Nyx Car`() {
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        bluetooth(BluetoothAdapter.STATE_OFF)
        assertNull(started())
        assertTrue(diary().contains("CarPlay: Bluetooth da central desligou com o ZLINK na tela"))
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertTrue(
            diary().contains("CarPlay: terminou (Bluetooth da central ligou de novo): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente"),
        )
    }

    @Test
    fun `cabo tirado como nas gravacoes - Bluetooth religa e depois o USB cai`() {
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        advance(500)
        usb(connected = false)
        advance(CarPlaySession.END_GRACE_MS + 100)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertNull("Volta uma vez só", started())
    }

    @Test
    fun `fim pelo cabo e depois pelo Bluetooth volta uma vez so`() {
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_OFF)
        usb(connected = false)
        advance(CarPlaySession.END_GRACE_MS + 100)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        bluetooth(BluetoothAdapter.STATE_ON)
        assertNull("Não volta de novo", started())
    }

    // ---------- Tela de espera do Nyx Car (B1, 0.4.7) ----------

    @Test
    fun `pelo cabo a tela de espera cobre o ZLINK e sai quando o video comeca`() {
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        assertTrue(diary().contains("CarPlay: tela de espera do Nyx Car aberta (cabo)"))
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        stream(294L * 1024, 1_000) // controle logo depois do Bluetooth: ainda não é o vídeo
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        stream(1_200L * 1024, 750) // o vídeo é visto entre 250 e 750 ms
        advance(1_000)
        assertEquals("Fica mais 2 s depois do vídeo", CarPlayWaitState.PREPARING, screen.state)
        advance(1_100)
        assertNull("Saiu depois do vídeo", screen.state)
        assertTrue(diary().toString(), diary().any { it.startsWith("CarPlay: vídeo do CarPlay chegou") })
    }

    @Test
    fun `tempo maximo sem o CarPlay comecar fecha a tela e o ZLINK`() {
        appleAttached()
        usb(connected = true)
        advance(CarPlayReturn.WaitMode.CABLE.timeoutMs + 100)
        assertEquals("O Nyx Car volta", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains(
            "CarPlay: tempo máximo (20,0 s) e o CarPlay não começou: tela de espera e ZLINK fechados, Nyx Car na frente",
        ))
        assertEquals("A tela fica até o Nyx Car aparecer", CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
    }

    @Test
    fun `sem o trafego do video a tela fecha 4 s depois de o Bluetooth desligar`() {
        appleAttached()
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // o CarPlay começou; só o vídeo não foi percebido
        stream(20L * 1024, CarPlayReturn.PREPARING_MAX_MS - 250)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        advance(300)
        assertNull("Fecha bem antes do tempo máximo (0.4.21)", screen.state)
        assertNull("O CarPlay fica na tela", started())
        assertTrue(diary().toString(), diary().contains(
            "CarPlay: o tráfego do vídeo não passou do limite 4,0 s depois de o Bluetooth desligar: tela de espera fechada (o CarPlay já está na tela)",
        ))
    }

    @Test
    fun `um toque na tela de espera fecha tambem o ZLINK`() {
        appleAttached()
        screen.tap!!.invoke()
        assertEquals("O Nyx Car volta (pedido do usuário, 0.4.10)", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: tela de espera tocada: ela e o ZLINK fechados, Nyx Car na frente"))
        assertEquals("A tela fica até o Nyx Car aparecer", CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
    }

    @Test
    fun `um toque com outro app na frente so fecha a tela`() {
        appleAttached()
        top = HOME
        screen.tap!!.invoke()
        assertNull(screen.state)
        assertNull(started())
        assertTrue(diary().contains("CarPlay: tela de espera fechada com um toque"))
    }

    @Test
    fun `tocar e segurar na tela de espera fecha so ela e mostra o ZLINK`() {
        appleAttached()
        screen.longPress!!.invoke()
        assertNull(screen.state)
        assertNull("O ZLINK fica", started())
        assertTrue(diary().contains("CarPlay: tela de espera fechada com um toque longo: o ZLINK fica na tela"))
    }

    @Test
    fun `sem a permissao de sobrepor nada aparece`() {
        screen.allowed = false
        appleAttached()
        assertNull(screen.state)
        assertTrue(diary().any { it.contains("falta a permissão \"Sobrepor a outros apps\"") })
    }

    @Test
    fun `sem fio - o ZLINK abrindo sozinho sobre a Home abre a tela`() {
        top = HOME
        CarPlayReturn.onLauncherPaused()
        advance(200)
        assertNull("Ainda é a Home", screen.state)
        top = ZLINK
        advance(250)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        stream(8L * 1024, 1_000)
        stream(247L * 1024, 750) // sem fio o vídeo chega com menos tráfego
        advance(2_100)
        assertNull(screen.state)
    }

    @Test
    fun `sem fio com outro app na tela - o iPhone no Bluetooth arma a vigia`() {
        top = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        app.sendBroadcast(Intent(BluetoothDevice.ACTION_ACL_CONNECTED))
        advance(1_000)
        assertNull(screen.state)
        assertTrue("A janela fica reservada enquanto o iPhone pode chegar", screen.parked)
        top = ZLINK // o ZLINK abre sozinho alguns segundos depois
        advance(150)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
    }

    @Test
    fun `o Bluetooth so tentando conectar nao arma a vigia`() {
        top = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        hfp(BluetoothProfile.STATE_CONNECTING)
        assertFalse(screen.parked)
        top = ZLINK
        advance(500)
        assertNull(screen.state)
    }

    @Test
    fun `o ZLINK que ja estava na tela nao abre a tela de espera`() {
        top = ZLINK // aberto pelo usuário, por exemplo para os ajustes dele
        app.sendBroadcast(Intent(BluetoothDevice.ACTION_ACL_CONNECTED))
        advance(1_000)
        assertNull(screen.state)
        top = HOME
        advance(200)
        top = ZLINK // agora sim chegou à tela
        advance(150)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
    }

    @Test
    fun `a janela reservada pela vigia sai quando ela termina`() {
        top = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        app.sendBroadcast(Intent(BluetoothDevice.ACTION_ACL_CONNECTED))
        idle()
        assertTrue(screen.parked)
        advance(20_200)
        assertFalse(screen.parked)
        assertNull(screen.state)
    }

    @Test
    fun `a vigia termina sozinha`() {
        top = HOME
        CarPlayReturn.onLauncherPaused()
        advance(3_000)
        top = ZLINK
        advance(1_000)
        assertNull("Passou a janela da vigia", screen.state)
    }

    @Test
    fun `CarPlay aberto pelo usuario ou ja conectado nao abre a tela`() {
        CarPlayReturn.markUserLaunch()
        CarPlayReturn.onLauncherPaused()
        advance(300)
        assertNull("O usuário abriu o CarPlay", screen.state)
        advance(3_100)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false)
        CarPlayReturn.onLauncherPaused()
        advance(300)
        assertNull("Bluetooth da central desligado: o CarPlay já está conectado", screen.state)
    }

    @Test
    fun `outro app cobrindo a Home nao abre a tela`() {
        top = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        CarPlayReturn.onLauncherPaused()
        advance(3_000)
        assertNull(screen.state)
    }

    @Test
    fun `fim do CarPlay mostra CarPlay desconectado por 2 s com o Nyx Car voltando por baixo`() {
        connectByCable()
        assertNull(screen.state)
        assertTrue("Durante o CarPlay a janela fica reservada", screen.parked)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals("Cobre a espera do ZLINK na hora", CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals("O Nyx Car volta por baixo", MainActivity::class.java.name, started()?.component?.className)
        top = HOME
        CarPlayReturn.onLauncherShown()
        assertEquals("Fica os 2 s mesmo com a Home na frente", CarPlayWaitState.DISCONNECTED, screen.state)
        advance(CarPlayReturn.DISCONNECTED_SHOW_MS - 100)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        advance(200)
        assertNull(screen.state)
        assertFalse("Sem CarPlay, a janela sai de vez", screen.parked)
        assertNull("Volta uma vez só", started())
    }

    @Test
    fun `um toque em CarPlay desconectado fecha na hora`() {
        connectByCable()
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        top = HOME
        screen.tap!!.invoke()
        assertNull(screen.state)
    }

    // ---------- Queda da conexão antes do CarPlay aparecer (0.4.9) ----------

    @Test
    fun `cabo tirado logo depois de conectar - Conexao interrompida e depois o Nyx Car`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS - 100)
        assertEquals("A central ainda pode virar aparelho do iPhone", CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        advance(200)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        assertNull("Espera o iPhone voltar", started())
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS)
        assertEquals("Nada voltou: o ZLINK sai", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: o celular não voltou: tela de espera e ZLINK fechados, Nyx Car na frente"))
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
    }

    @Test
    fun `mau contato - o iPhone volta durante Conexao interrompida e a conexao segue`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        usb(connected = true)
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS + CarPlayReturn.CABLE_LINK_MS)
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        assertNull(started())
    }

    @Test
    fun `cabo tirado enquanto o CarPlay prepara a tela - Conexao interrompida`() {
        appleAttached()
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON) // como nas gravações: o Bluetooth religa primeiro
        usb(connected = false)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        assertNull(started())
        assertTrue(diary().any { it.startsWith("CarPlay: conexão interrompida antes de o CarPlay aparecer (Bluetooth da central ligou de novo)") })
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS + 100)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `Conexao interrompida nao vira Conectando sem fio com o ZLINK parado na tela`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        // Como no teste de 12/11 (20:49:38): a central tenta o Bluetooth do iPhone sozinha.
        hfp(BluetoothProfile.STATE_CONNECTING)
        hfp(BluetoothProfile.STATE_CONNECTED)
        advance(1_000)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `Conexao interrompida - o CarPlay entrando de novo segue a conexao`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // sem fio, o iPhone voltou e o CarPlay está entrando
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS + 100)
        assertNull("Não volta para a Home no meio da conexão", started())
    }

    @Test
    fun `o ZLINK voltando sozinho logo depois de fechado e coberto e sai de novo`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        screen.tap!!.invoke() // "Conexão interrompida": toque volta ao Nyx Car
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        top = HOME
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
        // O ZLINK, que estava abrindo, chega à frente sozinho um instante depois.
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(150)
        assertEquals("Coberto na hora", CarPlayWaitState.INTERRUPTED, screen.state)
        assertEquals("E sai de novo", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: o ZLINK voltou sozinho logo depois de fechado: coberto e o Nyx Car de novo na frente"))
        top = HOME
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
        // Passado o tempo de proteção, o ZLINK chegando é uma conexão nova.
        advance(CarPlayReturn.DISMISS_GUARD_MS)
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(150)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
    }

    @Test
    fun `a Home volta sem animacao`() {
        usb(connected = true)
        usb(connected = false)
        val intent = started()!!
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
    }

    @Test
    fun `pelo cabo sem o aviso da Apple no USB o texto fala em celular`() {
        top = HOME
        usb(connected = true)
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(150)
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
    }

    @Test
    fun `um toque em Conexao interrompida volta ao Nyx Car na hora`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        screen.tap!!.invoke()
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    // ---------- CarPlay sem iPhone (0.4.8) ----------

    @Test
    fun `CarPlay tocado sem iPhone mostra Aguardando o iPhone em vez do ZLINK`() {
        top = HOME
        assertTrue(CarPlayReturn.interceptCarPlay())
        assertEquals(CarPlayWaitState.WAITING_PHONE, screen.state)
        assertNull("O ZLINK não abriu", started())
        CarPlayReturn.onLauncherShown()
        assertEquals("A Home voltando não fecha a espera do iPhone", CarPlayWaitState.WAITING_PHONE, screen.state)
        // O iPhone conecta pelo cabo: a mesma tela passa para "Conectando".
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
    }

    @Test
    fun `Aguardando o iPhone - sem fio, o ZLINK abrindo sozinho passa para Conectando`() {
        top = HOME
        CarPlayReturn.interceptCarPlay()
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(250)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
    }

    @Test
    fun `Aguardando o iPhone - toque volta, tempo esgota e toque longo abre o ZLINK`() {
        top = HOME
        CarPlayReturn.interceptCarPlay()
        screen.tap!!.invoke()
        assertNull(screen.state)

        CarPlayReturn.interceptCarPlay()
        advance(60_100)
        assertNull("1 min sem iPhone", screen.state)

        installZlink()
        CarPlayReturn.interceptCarPlay()
        screen.longPress!!.invoke()
        assertNull(screen.state)
        assertEquals("com.zjinnova.zlink", started()?.component?.packageName)
    }

    @Test
    fun `CarPlay conectado ou sem permissao abre o ZLINK normalmente`() {
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false) // CarPlay conectado
        assertFalse(CarPlayReturn.interceptCarPlay())
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
        screen.allowed = false
        assertFalse(CarPlayReturn.interceptCarPlay())
        assertNull(screen.state)
    }

    // ---------- Fim pela tela do ZLINK (0.4.11) ----------

    private fun zlink(signature: String) = ZlinkScreen(signature, elements = 3, texts = 1, touchable = 1, ids = listOf("x"))

    @Test
    fun `o ZLINK voltando para a espera durante o CarPlay mostra CarPlay desconectado na hora`() {
        appleAttached()
        CarPlayReturn.onZlinkScreen(zlink("espera")) // vista com o Bluetooth ligado: aprendida
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        stream(20L * 1024, 1_000)
        CarPlayReturn.onZlinkScreen(zlink("carplay"))
        stream(1_200L * 1024, 1_000)
        advance(2_100)
        assertNull(screen.state)
        advance(2_000)
        CarPlayReturn.onZlinkScreen(zlink("espera")) // o iPhone saiu: o ZLINK volta para a espera
        assertEquals("Na hora, sem esperar o Bluetooth", CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains(
            "CarPlay: a tela do ZLINK voltou para a espera (o CarPlay terminou): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente",
        ))
        top = HOME
        advance(1_000)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON) // a central religa o Bluetooth segundos depois
        assertNull("Não volta de novo", started())
        assertEquals("A mensagem fica os 2 s", CarPlayWaitState.DISCONNECTED, screen.state)
        assertTrue(diary().contains("CarPlay: terminou (Bluetooth da central ligou de novo) 1,0 s depois de a tela do ZLINK mostrar o fim"))
        advance(1_100)
        assertNull(screen.state)
        assertFalse(screen.parked)
        assertEquals("Guardadas para as próximas vezes", 1 to 1, ZlinkScreenStore.counts(app))
    }

    @Test
    fun `tela do ZLINK nunca vista nao muda nada e o Bluetooth continua valendo`() {
        CarPlayReturn.onZlinkScreen(zlink("espera"))
        connectByCable()
        CarPlayReturn.onZlinkScreen(zlink("carplay"))
        advance(2_000)
        CarPlayReturn.onZlinkScreen(zlink("outra"))
        assertNull(screen.state)
        assertNull(started())
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `sem CarPlay as telas do ZLINK so sao aprendidas`() {
        CarPlayReturn.onZlinkScreen(zlink("espera"))
        CarPlayReturn.onZlinkScreen(zlink("ajustes"))
        CarPlayReturn.onZlinkScreen(zlink("espera"))
        assertNull(screen.state)
        assertNull(started())
        assertEquals(2 to 0, ZlinkScreenStore.counts(app))
    }

    // ---------- Imagem do CarPlay e "conectando" (0.4.12) ----------

    private fun picture(signature: String) =
        ZlinkScreen(signature, elements = 24, texts = 4, touchable = 4, ids = listOf("content", "phone_content_event_delegate"))

    private fun connecting(signature: String) =
        ZlinkScreen(signature, elements = 24, texts = 5, touchable = 3, ids = listOf("content", "state_connecting"))

    /** Pelo cabo até o CarPlay na tela, com o serviço vendo as telas do ZLINK, sem nada aprendido. */
    private fun connectSeeingScreens() {
        appleAttached()
        CarPlayReturn.onZlinkScreen(connecting("conectando"))
        usb(connected = true)
        CarPlayReturn.onZlinkScreen(picture("carplay-comecando"))
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        stream(20L * 1024, 1_000)
        stream(1_200L * 1024, 1_000)
        CarPlayReturn.onZlinkScreen(picture("carplay"))
        advance(4_000)
    }

    @Test
    fun `sem o trafego do video a imagem do CarPlay na tela do ZLINK fecha a tela de espera`() {
        appleAttached()
        CarPlayReturn.onZlinkScreen(connecting("conectando"))
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        CarPlayReturn.onZlinkScreen(picture("carplay-comecando").copy(ids = listOf("content", "phone_content_event_delegate", "state_connecting")))
        stream(20L * 1024, 1_000) // CarPlay parado na tela inicial dele: pouco tráfego
        assertNull("Ainda conectando: nada muda", diary().firstOrNull { it.startsWith("CarPlay: imagem do CarPlay na tela do ZLINK") })
        CarPlayReturn.onZlinkScreen(picture("carplay"))
        assertTrue(diary().toString(), diary().any { it.startsWith("CarPlay: imagem do CarPlay na tela do ZLINK") })
        assertEquals("A mensagem fica 2 s", CarPlayWaitState.PREPARING, screen.state)
        advance(2_100)
        assertNull(screen.state)
    }

    @Test
    fun `a imagem do CarPlay sumindo ja e fim na primeira vez`() {
        connectSeeingScreens()
        val end = zlink("espera-do-fim") // sem a imagem do CarPlay, nunca vista
        CarPlayReturn.readScreenNow = { end }
        CarPlayReturn.onZlinkScreen(end)
        assertNull("Confere antes", screen.state)
        advance(CarPlayReturn.SCREEN_CONFIRM_MS)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertEquals("Aprendida como espera", 2, ZlinkScreenStore.counts(app).first)
    }

    @Test
    fun `a imagem do CarPlay voltando na conferencia nao e fim`() {
        connectSeeingScreens()
        CarPlayReturn.readScreenNow = { picture("carplay") }
        CarPlayReturn.onZlinkScreen(zlink("sem-imagem"))
        advance(CarPlayReturn.SCREEN_CONFIRM_MS + 50)
        assertNull(screen.state)
        assertNull(started())
    }

    // ---------- Cobrir primeiro, Bluetooth falso e sessão retomada (0.4.17) ----------

    /** O CarPlay termina de verdade: a tela do ZLINK volta para uma espera nunca vista. */
    private fun zlinkShowsEnd() {
        val end = zlink("espera-do-fim")
        CarPlayReturn.readScreenNow = { end }
        CarPlayReturn.onZlinkScreen(end)
        advance(CarPlayReturn.SCREEN_CONFIRM_MS)
    }

    @Test
    fun `no fim a janela do Nyx Car entra antes de a Home ser chamada`() {
        connectSeeingScreens()
        assertTrue(screen.parked)
        assertEquals("Reservada já com a mensagem do fim", CarPlayWaitState.DISCONNECTED, screen.parkedNext)
        screen.deferFrames = true
        zlinkShowsEnd()
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertNull("A Home espera o quadro da janela", started())
        screen.drawFrame()
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `no fim o diario e a tela aprendida so sao gravados depois do quadro da janela`() {
        connectSeeingScreens()
        screen.deferFrames = true
        val end = zlink("espera-do-fim")
        CarPlayReturn.readScreenNow = { end }
        CarPlayReturn.onZlinkScreen(end)
        advance(CarPlayReturn.SCREEN_CONFIRM_MS)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        val ended = "CarPlay: a tela do ZLINK voltou para a espera (o CarPlay terminou): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente"
        assertFalse("Antes do quadro, nada no diário", diary().contains(ended))
        assertEquals("Antes do quadro, nada guardado", 1, ZlinkScreenStore.counts(app).first)
        screen.drawFrame()
        assertTrue(diary().contains(ended))
        assertEquals("Depois do quadro, a espera do fim guardada", 2, ZlinkScreenStore.counts(app).first)
    }

    @Test
    fun `tipo do aviso do ZLINK so com a estrutura`() {
        assertEquals(
            "conteúdo/FrameLayout/subárvore",
            ZlinkScreenService.eventKind(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, "android.widget.FrameLayout", AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE),
        )
        assertEquals(
            "conteúdo/TextView/texto+outro 64",
            ZlinkScreenService.eventKind(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, "android.widget.TextView", AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or 64),
        )
        assertEquals("janela/?", ZlinkScreenService.eventKind(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null, 0))
    }

    @Test
    fun `Bluetooth religando com a imagem do CarPlay ainda na tela nao e fim`() {
        connectSeeingScreens()
        CarPlayReturn.readScreenNow = { picture("carplay") }
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        bluetooth(BluetoothAdapter.STATE_ON)
        assertNull(screen.state)
        assertNull(started())
        assertTrue(diary().contains(
            "CarPlay: Bluetooth da central ligou, mas a imagem do CarPlay continua na tela do ZLINK: não é o fim, a sessão continua",
        ))
        // A central desliga o Bluetooth de novo; depois, o CarPlay termina de verdade.
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        advance(2_000)
        zlinkShowsEnd()
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `Bluetooth religando sem a imagem do CarPlay na tela continua sendo fim`() {
        connectSeeingScreens()
        CarPlayReturn.readScreenNow = { zlink("espera") }
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `imagem do CarPlay na tela sem a sessao registrada volta a vigiar o fim`() {
        // O Nyx Car abriu do zero durante o CarPlay: o aviso do Bluetooth desligando veio antes.
        // (Até a 0.4.22, também o CarPlay entrando com a Home na tela; ver "CarPlay entrando por trás".)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false)
        top = ZLINK
        CarPlayReturn.onZlinkScreen(picture("carplay"))
        assertTrue(diary().contains("CarPlay: imagem do CarPlay na tela do ZLINK sem a sessão registrada: o fim volta a ser vigiado"))
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.parkedNext)
        advance(2_000)
        zlinkShowsEnd()
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `imagem do CarPlay com o Bluetooth ligado nao abre sessao`() {
        CarPlayReturn.onZlinkScreen(picture("carplay"))
        assertFalse(diary().any { it.contains("sem a sessão registrada") })
        assertFalse(screen.parked)
    }

    @Test
    fun `pelo cabo, o ZLINK ainda conectando segura a tela ate 1 min`() {
        appleAttached()
        usb(connected = true)
        CarPlayReturn.onZlinkScreen(connecting("conectando"))
        advance(CarPlayReturn.WaitMode.CABLE.timeoutMs + 100)
        assertEquals(CarPlayWaitState.CONNECTING_IPHONE, screen.state)
        assertNull(started())
        assertTrue(diary().any { it.contains("o ZLINK ainda mostra \"conectando\" e o cabo está conectado") })
        advance(CarPlayReturn.MAX_CONNECTING_MS - CarPlayReturn.WaitMode.CABLE.timeoutMs)
        assertEquals("1 min no total: o Nyx Car volta", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().any { it.startsWith("CarPlay: tempo máximo (60,0 s) e o CarPlay não começou") })
    }

    // ---------- Notificação do ZLINK (0.4.13, medição) ----------

    @Test
    fun `o diario registra quanto antes do fim a notificacao do ZLINK mudou`() {
        connectByCable()
        ZlinkNotifications.markChangeForTests(android.os.SystemClock.uptimeMillis())
        advance(1_200)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertTrue(diary().toString(), diary().contains(
            "CarPlay: uma notificação do ZLINK mudou 1,2 s antes deste fim (Bluetooth da central ligou de novo)",
        ))
    }

    @Test
    fun `notificacao antiga do ZLINK nao entra no diario`() {
        ZlinkNotifications.markChangeForTests(android.os.SystemClock.uptimeMillis())
        connectByCable()
        advance(40_000)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertFalse(diary().any { it.contains("notificação do ZLINK") })
    }

    // ---------- Fim pelo som (0.4.21) ----------

    private fun music() = CarPlayReturn.onSound(listOf(CarPlaySound.Kind.MEDIA))

    private fun silence() = CarPlayReturn.onSound(emptyList())

    /** CarPlay sem fio com música tocando há 30 s, com o ZLINK na tela. */
    private fun carPlayWithMusic() {
        top = ZLINK
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        music()
        advance(30_000)
    }

    @Test
    fun `a musica parando cobre o ZLINK na hora e a Home volta com o Bluetooth`() {
        carPlayWithMusic()
        silence()
        assertEquals("Na hora, sem esperar o Bluetooth", CarPlayWaitState.DISCONNECTED, screen.state)
        assertNull("A Home espera a confirmação", started())
        assertTrue(diary().toString(), diary().any { it.startsWith("CarPlay: a música do CarPlay parou como no fim") })
        advance(1_200)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().toString(), diary().contains(
            "CarPlay: terminou (Bluetooth da central ligou de novo) 1,2 s depois de a música do CarPlay parar: o Nyx Car voltou para a frente",
        ))
        top = HOME
        advance(799)
        assertEquals("A mensagem fica 2 s no total", CarPlayWaitState.DISCONNECTED, screen.state)
        advance(2)
        assertNull(screen.state)
    }

    @Test
    fun `pelo cabo, o cabo caindo confirma o fim pelo som`() {
        usb(connected = true)
        carPlayWithMusic()
        silence()
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        advance(1_100)
        usb(connected = false)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().any { it.startsWith("CarPlay: terminou (cabo desconectado) 1,1 s depois de a música do CarPlay parar") })
    }

    @Test
    fun `sem confirmacao em 3 s a tela sai e o CarPlay continua`() {
        carPlayWithMusic()
        silence()
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        advance(CarPlayReturn.SOUND_CONFIRM_MS + 100)
        assertNull(screen.state)
        assertTrue("A janela volta para a reserva", screen.parked)
        assertNull(started())
        assertTrue(diary().any { it.contains("nem o Bluetooth nem o cabo confirmaram o fim em 3,0 s") })
        bluetooth(BluetoothAdapter.STATE_TURNING_ON) // o fim de verdade, mais tarde, pelo Bluetooth
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `a musica voltando desfaz a tela`() {
        carPlayWithMusic()
        silence()
        advance(500)
        music()
        assertNull(screen.state)
        assertTrue(diary().any { it.contains("a música voltou a tocar: a tela do Nyx Car saiu e o CarPlay continua") })
    }

    @Test
    fun `fim de uma ligacao nao cobre o CarPlay`() {
        carPlayWithMusic()
        CarPlayReturn.callActive = { true }
        silence()
        assertNull(screen.state)
        assertTrue(diary().contains("CarPlay: a música do CarPlay parou no fim de uma ligação ou da Siri: não é o fim"))
    }

    @Test
    fun `com o player do Nyx Car tocando o silencio nao e o fim do CarPlay`() {
        top = ZLINK
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        CarPlayReturn.ownPlayerPlaying = { true }
        music()
        advance(30_000)
        CarPlayReturn.ownPlayerPlaying = { false } // o usuário pausou o player do Nyx Car
        silence()
        assertNull(screen.state)
    }

    @Test
    fun `com outro app na frente a musica parando nao mostra nada`() {
        carPlayWithMusic()
        top = HOME
        silence()
        assertNull(screen.state)
        assertNull(started())
    }

    @Test
    fun `Bluetooth religando com a musica tocando nao e fim`() {
        carPlayWithMusic()
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertNull("Nada muda", started())
        assertNull(screen.state)
        assertTrue(diary().contains("CarPlay: Bluetooth da central ligou, mas a música do CarPlay continua tocando: não é o fim, a sessão continua"))
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // como no teste: a central o desliga de novo
        silence() // e o fim de verdade vem depois, pelo som
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
    }

    @Test
    fun `musica parando depois de um Bluetooth desmentido e o fim`() {
        carPlayWithMusic()
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertNull(started())
        advance(5_000)
        silence()
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertTrue(diary().toString(), diary().any { it.startsWith("CarPlay: terminou (som do CarPlay parou depois de o Bluetooth religar)") })
    }

    // ---------- Teste da 0.4.21: a thread principal não disputa o processador (0.4.22) ----------

    @Test
    fun `sem a acessibilidade, a thread principal fica na prioridade normal durante o CarPlay`() {
        val main = android.os.Process.myPid()
        val before = android.os.Process.getThreadPriority(main)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(before, android.os.Process.getThreadPriority(main))
    }

    @Test
    fun `com a acessibilidade ligada, a thread principal sobe de prioridade como na 0_4_19`() {
        val service = org.robolectric.Robolectric.buildService(ZlinkScreenService::class.java).create().get()
        CarPlayReturn.screenServiceConnected(service)
        idle()
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(
            android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY,
            android.os.Process.getThreadPriority(android.os.Process.myPid()),
        )
        CarPlayReturn.screenServiceDisconnected(service)
    }

    // ---------- CarPlay entrando por trás (0.4.23) ----------

    /** Sem fio: o ZLINK chega à tela sozinho e o tempo máximo o tira da frente (teste da 0.4.22). */
    private fun wirelessTimedOut() {
        top = HOME
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(250)
        assertEquals(CarPlayWaitState.CONNECTING_WIRELESS, screen.state)
        advance(CarPlayReturn.WaitMode.WIRELESS.timeoutMs + 100)
        assertEquals("O Nyx Car volta", MainActivity::class.java.name, started()?.component?.className)
        top = HOME
        CarPlayReturn.onLauncherShown()
        assertNull(screen.state)
    }

    @Test
    fun `CarPlay entrando com outro app na tela - o fim e percebido quando o ZLINK e aberto`() {
        top = HOME
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertNull("Nada aparece por cima do app", screen.state)
        assertTrue("A janela fica reservada para o fim", screen.parked)
        assertTrue(diary().toString(), diary().contains(
            "CarPlay: Bluetooth da central desligou com com.premiumcar.launcher na tela: o CarPlay entrou por trás e o fim dele passa a ser vigiado",
        ))
        top = ZLINK // o usuário toca em CarPlay no Nyx Car
        music()
        advance(30_000)
        silence()
        assertEquals("Até a 0.4.22, a espera do ZLINK ficava à vista", CarPlayWaitState.DISCONNECTED, screen.state)
        advance(600)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `CarPlay entrando ate 1 min depois do tempo maximo traz o ZLINK coberto`() {
        installZlink()
        wirelessTimedOut()
        advance(6_000) // às 13:19 do teste da 0.4.22, o CarPlay entrou 5,4 s depois
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals("Coberto antes de o ZLINK chegar", CarPlayWaitState.PREPARING, screen.state)
        assertEquals("com.zjinnova.zlink", started()?.component?.packageName)
        assertTrue(diary().toString(), diary().contains(
            "CarPlay: o CarPlay entrou depois do tempo máximo, com com.premiumcar.launcher na tela: " +
                "o ZLINK volta para a frente, coberto por \"Conectando\" (sem fio)",
        ))
        top = ZLINK
        CarPlayReturn.onLauncherPaused() // a Home sai da tela: não abre outra espera
        stream(8L * 1024, 500)
        stream(247L * 1024, 750)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        advance(2_100)
        assertNull("Fecha 2 s depois do vídeo, como numa conexão comum", screen.state)
        assertNull(started())
    }

    @Test
    fun `CarPlay entrando mais de 1 min depois do tempo maximo fica no fundo`() {
        installZlink()
        wirelessTimedOut()
        advance(CarPlayReturn.LATE_START_MS + 100)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertNull(screen.state)
        assertNull(started())
        assertTrue(diary().any { it.endsWith("o CarPlay entrou por trás e o fim dele passa a ser vigiado") })
    }

    @Test
    fun `CarPlay entrando com Aguardando o celular na tela traz o ZLINK coberto`() {
        installZlink()
        top = HOME
        assertTrue(CarPlayReturn.interceptCarPlay())
        assertEquals(CarPlayWaitState.WAITING_PHONE, screen.state)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        assertEquals("com.zjinnova.zlink", started()?.component?.packageName)
        assertTrue(diary().any { it.startsWith("CarPlay: o CarPlay entrou com o Nyx Car esperando o celular") })
        top = ZLINK
        advance(CarPlayReturn.PREPARING_MAX_MS + 100)
        assertNull("Sem o tráfego do vídeo, fecha 4 s depois, como numa conexão comum", screen.state)
        assertNull("O ZLINK fica na frente", started())
    }

    @Test
    fun `fim falso - o Bluetooth desliga de novo com CarPlay desconectado na tela e o ZLINK volta`() {
        installZlink()
        connectByCable()
        bluetooth(BluetoothAdapter.STATE_TURNING_ON) // sem música, vale o Bluetooth
        assertEquals(CarPlayWaitState.DISCONNECTED, screen.state)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        top = HOME
        advance(1_400) // teste de 13/11/2026: a central desligou o Bluetooth de novo 1,4 s depois
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals(CarPlayWaitState.PREPARING, screen.state)
        assertEquals("com.zjinnova.zlink", started()?.component?.packageName)
        assertTrue(diary().toString(), diary().any {
            it.startsWith("CarPlay: o CarPlay entrou de novo logo depois do fim, com com.premiumcar.launcher na tela")
        })
        top = ZLINK
        advance(CarPlayReturn.DISCONNECTED_SHOW_MS)
        assertEquals("O tempo da mensagem de fim não fecha a nova espera", CarPlayWaitState.PREPARING, screen.state)
        stream(1_200L * 1024, 750)
        advance(2_100)
        assertNull(screen.state)
        assertNull(started())
    }

    @Test
    fun `depois de um toque na espera, o CarPlay entrando fica no fundo`() {
        installZlink()
        top = HOME
        CarPlayReturn.onLauncherPaused()
        top = ZLINK
        advance(250)
        screen.tap!!.invoke() // o usuário desistiu de esperar
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        top = HOME
        CarPlayReturn.onLauncherShown()
        advance(6_000)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF)
        assertNull(screen.state)
        assertNull(started())
    }

    @Test
    fun `iPhone no USB com o CarPlay sem fio em uso nao fecha o ZLINK`() {
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // CarPlay sem fio entrou
        appleAttached() // como no teste da 0.4.24, 0,1 s depois do vídeo
        assertNull("Sem a tela de espera do cabo", screen.state)
        advance(CarPlayReturn.CABLE_LINK_MS + CarPlayReturn.INTERRUPTED_HOLD_MS + 1_000)
        assertNull("O ZLINK continua na frente", started())
        assertTrue(
            diary().contains("CarPlay: iPhone apareceu no USB com o CarPlay sem fio em uso: a sessão continua, sem a tela de espera do cabo"),
        )
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals("O fim continua valendo", MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `espera do cabo com o CarPlay sem fio entrando nao vira Conexao interrompida`() {
        appleAttached()
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // entrou sem fio antes de o cabo completar
        advance(CarPlayReturn.CABLE_LINK_MS + CarPlayReturn.INTERRUPTED_HOLD_MS + 1_000)
        assertNull(started())
        assertTrue(diary().none { it.contains("conexão interrompida") })
    }

    @Test
    fun `Nyx Car aberto do zero durante o CarPlay volta a vigiar o fim`() {
        CarPlayReturn.resetForTests()
        PlayerLog.resetForTests()
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(false) // CarPlay em uso
        CarPlayReturn.topActivity = { top }
        CarPlayReturn.fastTop = { top }
        CarPlayReturn.traffic = { received }
        CarPlayReturn.waitView = screen
        CarPlayReturn.useMainThread = true
        CarPlayReturn.install(app)
        idle()
        assertTrue(
            diary().contains(
                "CarPlay: Nyx Car aberto do zero com o Bluetooth da central desligado (CarPlay em uso): o fim volta a ser vigiado",
            ),
        )
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
    }

    @Test
    fun `Nyx Car aberto do zero com o Bluetooth ligado nao abre sessao`() {
        assertTrue(diary().none { it.contains("aberto do zero") })
        bluetooth(BluetoothAdapter.STATE_TURNING_ON)
        assertNull(started())
    }

    /** O ZLINK instalado, com a entrada da lista de apps. */
    private fun installZlink() {
        val component = ComponentName("com.zjinnova.zlink", "com.zjinnova.android.zlink.features.main.MainActivity")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private companion object {
        const val ZLINK = "com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity"
        const val HOME = "com.premiumcar.launcher/.app.MainActivity"
        const val TERMINATED_CABLE =
            "CarPlay: terminou (cabo desconectado): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente"
    }
}
