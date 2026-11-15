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
        assertFalse(session.bluetooth(on = false))
        assertTrue(session.active)
    }

    @Test
    fun `cabo tirado - Bluetooth religa, USB cai e a sessao termina uma vez`() {
        connectedByCable()
        assertTrue("Fim provável", session.bluetooth(on = true))
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
        session.bluetooth(on = true)
        session.usb(connected = false)
        assertTrue("Cancelou um fim provável", session.appleAttached())
        assertNull(session.confirmEnd())
        assertTrue(session.active)
        // A troca de papéis volta ~2 s depois; mais tarde o CarPlay desliga o Bluetooth de novo.
        session.usb(connected = true)
        session.bluetooth(on = false)
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
    fun `sem fio - Bluetooth desligado e religado encerra`() {
        assertFalse(session.bluetooth(on = false))
        assertTrue(session.bluetoothOff)
        assertTrue(session.active)
        assertFalse("Repetido (desligando → desligado)", session.bluetooth(on = false))
        assertTrue(session.bluetooth(on = true))
        assertEquals(Signal.BLUETOOTH, session.confirmEnd())
        assertFalse(session.active)
    }

    @Test
    fun `Bluetooth desligado de novo depois de um fim e uma sessao nova (0_4_23)`() {
        // Até a 0.4.22, desligar com outro app na tela não contava. No teste de 13/11/2026, a
        // central religou o Bluetooth (fim), o Nyx Car voltou e ela o desligou 1,4 s depois, com o
        // CarPlay continuando atrás do Nyx Car.
        session.bluetooth(on = false)
        assertTrue(session.bluetooth(on = true))
        assertEquals(Signal.BLUETOOTH, session.confirmEnd())
        assertFalse(session.bluetooth(on = false))
        assertTrue(session.active)
        assertTrue(session.bluetooth(on = true))
        assertEquals("O fim desta também é percebido", Signal.BLUETOOTH, session.confirmEnd())
    }

    @Test
    fun `Bluetooth religado com o CarPlay ainda na tela - o fim e desfeito e a sessao continua`() {
        connectedByCable()
        assertTrue(session.bluetooth(on = true))
        session.cancelEnd()
        assertNull(session.confirmEnd())
        assertTrue(session.active)
        // A central desliga de novo; depois, o cabo sai de verdade.
        assertFalse(session.bluetooth(on = false))
        assertTrue(session.bluetoothOff)
        assertTrue(session.usb(connected = false))
        assertEquals(Signal.CABLE, session.confirmEnd())
    }

    @Test
    fun `imagem do CarPlay sem sessao registrada - a sessao volta a valer`() {
        assertFalse("Nyx Car aberto do zero durante o CarPlay", session.active)
        session.resumeCarPlay()
        assertTrue(session.active)
        assertTrue(session.bluetoothOff)
        assertTrue(session.bluetooth(on = true))
        assertEquals(Signal.BLUETOOTH, session.confirmEnd())
    }

    @Test
    fun `Bluetooth atrasado da sessao anterior nao encerra a nova`() {
        connectedByCable()
        session.usb(connected = false)
        assertEquals(Signal.CABLE, session.confirmEnd())
        // O cabo volta antes de o Bluetooth religar: sessão nova.
        session.usb(connected = true)
        assertFalse(session.bluetooth(on = true))
        assertTrue(session.active)
    }
}
