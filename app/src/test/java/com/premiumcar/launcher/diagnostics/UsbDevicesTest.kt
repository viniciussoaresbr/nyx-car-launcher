package com.premiumcar.launcher.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDevicesTest {

    private val iphone = UsbDevices.Device(
        vendorId = 0x05AC, productId = 0x12A8, deviceClass = 0x00, interfaces = 4,
        product = "iPhone", path = "/dev/bus/usb/001/003",
    )
    private val pendrive = UsbDevices.Device(
        vendorId = 0x0781, productId = 0x5567, deviceClass = 0x00, interfaces = 1,
        product = null, path = "/dev/bus/usb/001/002",
    )

    @Test
    fun `iphone e reconhecido pelo identificador da apple`() {
        assertTrue(iphone.isApple)
        assertFalse(pendrive.isApple)
        assertEquals(
            "Apple · iPhone · 05ac:12a8 · classe definida por interface · 4 interfaces · /dev/bus/usb/001/003",
            UsbDevices.describe(iphone),
        )
        assertEquals(
            "0781:5567 · classe definida por interface · 1 interface · /dev/bus/usb/001/002",
            UsbDevices.describe(pendrive),
        )
    }
}
