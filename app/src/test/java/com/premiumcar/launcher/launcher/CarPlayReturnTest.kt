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
        var tap: (() -> Unit)? = null
        var longPress: (() -> Unit)? = null
        override val visible get() = state != null
        override fun canShow() = allowed
        override fun show(state: CarPlayWaitState, onTap: () -> Unit, onLongPress: (() -> Unit)?) {
            this.state = state
            parked = false
            tap = onTap
            longPress = onLongPress
        }
        override fun park() {
            clear()
            parked = true
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
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
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
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
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
        assertEquals("A tela fica até o Nyx Car aparecer", CarPlayWaitState.CONNECTING_CABLE, screen.state)
        CarPlayReturn.onLauncherResumed()
        assertNull(screen.state)
    }

    @Test
    fun `tempo maximo com o CarPlay ja entrando so fecha a tela`() {
        appleAttached()
        usb(connected = true)
        bluetooth(BluetoothAdapter.STATE_TURNING_OFF) // o CarPlay começou; só o vídeo não foi percebido
        advance(CarPlayReturn.WaitMode.CABLE.timeoutMs + 100)
        assertNull(screen.state)
        assertNull("O CarPlay fica na tela", started())
        assertTrue(diary().any { it.contains("tempo máximo (20,0 s) sem o vídeo do CarPlay") })
    }

    @Test
    fun `um toque na tela de espera fecha tambem o ZLINK`() {
        appleAttached()
        screen.tap!!.invoke()
        assertEquals("O Nyx Car volta (pedido do usuário, 0.4.10)", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: tela de espera tocada: ela e o ZLINK fechados, Nyx Car na frente"))
        assertEquals("A tela fica até o Nyx Car aparecer", CarPlayWaitState.CONNECTING_CABLE, screen.state)
        CarPlayReturn.onLauncherResumed()
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
        CarPlayReturn.onLauncherResumed()
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
        assertEquals("A central ainda pode virar aparelho do iPhone", CarPlayWaitState.CONNECTING_CABLE, screen.state)
        advance(200)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        assertNull("Espera o iPhone voltar", started())
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS)
        assertEquals("Nada voltou: o ZLINK sai", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: o iPhone não voltou: tela de espera e ZLINK fechados, Nyx Car na frente"))
        CarPlayReturn.onLauncherResumed()
        assertNull(screen.state)
    }

    @Test
    fun `mau contato - o iPhone volta durante Conexao interrompida e a conexao segue`() {
        appleAttached()
        advance(CarPlayReturn.CABLE_LINK_MS + 100)
        assertEquals(CarPlayWaitState.INTERRUPTED, screen.state)
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
        usb(connected = true)
        advance(CarPlayReturn.INTERRUPTED_HOLD_MS + CarPlayReturn.CABLE_LINK_MS)
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
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
        CarPlayReturn.onLauncherResumed()
        assertEquals("A Home voltando não fecha a espera do iPhone", CarPlayWaitState.WAITING_PHONE, screen.state)
        // O iPhone conecta pelo cabo: a mesma tela passa para "Conectando".
        appleAttached()
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
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

    @Test
    fun `pelo cabo, o ZLINK ainda conectando segura a tela ate 1 min`() {
        appleAttached()
        usb(connected = true)
        CarPlayReturn.onZlinkScreen(connecting("conectando"))
        advance(CarPlayReturn.WaitMode.CABLE.timeoutMs + 100)
        assertEquals(CarPlayWaitState.CONNECTING_CABLE, screen.state)
        assertNull(started())
        assertTrue(diary().any { it.contains("o ZLINK ainda mostra \"conectando\" e o cabo está conectado") })
        advance(CarPlayReturn.MAX_CONNECTING_MS - CarPlayReturn.WaitMode.CABLE.timeoutMs)
        assertEquals("1 min no total: o Nyx Car volta", MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().any { it.startsWith("CarPlay: tempo máximo (60,0 s) e o CarPlay não começou") })
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
