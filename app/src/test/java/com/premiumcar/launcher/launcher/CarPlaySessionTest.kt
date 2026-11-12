package com.premiumcar.launcher.launcher

import com.premiumcar.launcher.launcher.CarPlaySession.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quando a sessão do CarPlay termina (0.4.5): sinais reais das gravações de 12/11/2026. */
class CarPlaySessionTest {

    private val session = CarPlaySession()

    /** Cabo conectado e CarPlay entrando: a central desliga o próprio Bluetooth. */
    private fun connectedByCable() {
        assertFalse(session.usb(connected = true))
        assertFalse(session.bluetooth(on = false, zlinkOnTop = true))
        assertTrue(session.active)
    }

    @Test
    fun `cabo tirado - Bluetooth religa, USB cai e a sessao termina uma vez`() {
        connectedByCable()
        assertTrue("Fim provável", session.bluetooth(on = true, zlinkOnTop = false))
        assertTrue(session.usb(connected = false))
        assertEquals("Vale o primeiro sinal", Signal.BLUETOOTH, session.confirmEnd())
        assertFalse(session.active)
        assertNull("Termina uma vez só", session.confirmEnd())
    }

    @Test
    fun `so o USB caindo tambem encerra`() {
        session.usb(connected = true)
        assertTrue(session.usb(connected = false))
        assertEquals(Signal.CABLE, session.confirmEnd())
    }

    @Test
    fun `mau contato - o iPhone reaparece no USB e a sessao continua`() {
        connectedByCable()
        session.bluetooth(on = true, zlinkOnTop = false)
        session.usb(connected = false)
        assertTrue("Cancelou um fim provável", session.appleAttached())
        assertNull(session.confirmEnd())
        assertTrue(session.active)
        // A troca de papéis volta ~2 s depois; mais tarde o CarPlay desliga o Bluetooth de novo.
        session.usb(connected = true)
        session.bluetooth(on = false, zlinkOnTop = true)
        assertTrue(session.bluetoothOff)
    }

    @Test
    fun `mau contato - a porta volta a ficar conectada antes da confirmacao`() {
        session.usb(connected = true)
        session.usb(connected = false)
        session.usb(connected = true)
        assertNull(session.confirmEnd())
        assertTrue(session.active)
    }

    @Test
    fun `iPhone conectando pela primeira vez nao cancela nada`() {
        assertFalse(session.appleAttached())
    }

    @Test
    fun `estado desconectado sem cabo antes nao faz nada`() {
        assertFalse(session.usb(connected = false))
        assertNull(session.confirmEnd())
    }

    @Test
    fun `sem fio - Bluetooth desligado com o ZLINK na tela e religado encerra`() {
        assertFalse(session.bluetooth(on = false, zlinkOnTop = true))
        assertTrue(session.bluetoothOff)
        assertTrue(session.active)
        assertFalse("Repetido (desligando → desligado)", session.bluetooth(on = false, zlinkOnTop = true))
        assertTrue(session.bluetooth(on = true, zlinkOnTop = false))
        assertEquals(Signal.BLUETOOTH, session.confirmEnd())
        assertFalse(session.active)
    }

    @Test
    fun `Bluetooth desligado sem o ZLINK na tela nao conta`() {
        assertFalse(session.bluetooth(on = false, zlinkOnTop = false))
        assertFalse(session.bluetoothOff)
        assertFalse(session.bluetooth(on = true, zlinkOnTop = false))
    }

    @Test
    fun `Bluetooth atrasado da sessao anterior nao encerra a nova`() {
        connectedByCable()
        session.usb(connected = false)
        assertEquals(Signal.CABLE, session.confirmEnd())
        // O cabo volta antes de o Bluetooth religar: sessão nova.
        session.usb(connected = true)
        assertFalse(session.bluetooth(on = true, zlinkOnTop = false))
        assertTrue(session.active)
    }
}
