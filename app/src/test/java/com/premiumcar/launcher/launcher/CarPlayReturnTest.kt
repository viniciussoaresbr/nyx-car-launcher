package com.premiumcar.launcher.launcher

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Looper
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.system.Background
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.Executor

/** Volta ao Nyx Car quando o CarPlay termina (0.4.5), com avisos do sistema simulados. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class CarPlayReturnTest {

    private val app = RuntimeEnvironment.getApplication()
    private var top: String? = ZLINK

    @Before
    fun setUp() {
        Background.executor = Executor { it.run() }
        PlayerLog.resetForTests()
        CarPlayReturn.resetForTests()
        CarPlayReturn.topActivity = { top }
        CarPlayReturn.install(app)
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

    private fun started(): Intent? = shadowOf(app).nextStartedActivity

    private fun diary() = PlayerLog.read(app).map { it.second }

    @Test
    fun `cabo desconectado com o ZLINK na tela traz o Nyx Car de volta`() {
        usb(connected = true)
        usb(connected = false)
        assertNull("Espera a confirmação (mau contato não conta)", started())
        advance(CarPlaySession.END_GRACE_MS + 100)
        val intent = started()!!
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(diary().toString(), diary().contains("CarPlay: terminou (cabo desconectado): o Nyx Car voltou para a frente"))
    }

    @Test
    fun `mau contato curto no cabo nao faz nada`() {
        usb(connected = true)
        usb(connected = false)
        advance(500)
        usb(connected = true)
        advance(CarPlaySession.END_GRACE_MS + 100)
        assertNull(started())
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
        assertNull("Espera a confirmação", started())
        advance(CarPlaySession.END_GRACE_MS + 100)
        assertEquals(MainActivity::class.java.name, started()?.component?.className)
        assertTrue(diary().contains("CarPlay: terminou (Bluetooth da central ligou de novo): o Nyx Car voltou para a frente"))
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

    private companion object {
        const val ZLINK = "com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity"
    }
}
