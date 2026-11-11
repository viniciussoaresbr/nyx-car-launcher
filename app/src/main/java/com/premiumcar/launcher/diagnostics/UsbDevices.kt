package com.premiumcar.launcher.diagnostics

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Aparelhos USB conectados à central (modo host), para os testes do CarPlay pelo cabo.
 *
 * Somente leitura: listar os aparelhos não exige permissão, não abre conexão com eles e
 * não interfere no ZLINK. O número de série nunca é lido.
 */
object UsbDevices {

    /** Identificador USB da Apple (iPhone, iPad, iPod). */
    const val APPLE_VENDOR_ID = 0x05AC

    data class Device(
        val vendorId: Int,
        val productId: Int,
        val deviceClass: Int,
        val interfaces: Int,
        val product: String?,
        val path: String,
    ) {
        val isApple get() = vendorId == APPLE_VENDOR_ID
    }

    fun from(device: UsbDevice) = Device(
        vendorId = device.vendorId,
        productId = device.productId,
        deviceClass = device.deviceClass,
        interfaces = device.interfaceCount,
        product = runCatching { device.productName }.getOrNull(),
        path = device.deviceName,
    )

    /** Aparelhos conectados agora, ou `null` se o serviço USB não existe neste firmware. */
    fun read(context: Context): List<Device>? {
        val manager = context.getSystemService(UsbManager::class.java) ?: return null
        return runCatching { manager.deviceList.values.map(::from).sortedBy { it.path } }.getOrNull()
    }

    fun describe(device: Device): String = listOfNotNull(
        if (device.isApple) "Apple" else null,
        device.product?.trim()?.takeIf { it.isNotEmpty() },
        "%04x:%04x".format(device.vendorId, device.productId),
        "classe ${className(device.deviceClass)}",
        "${device.interfaces} ${if (device.interfaces == 1) "interface" else "interfaces"}",
        device.path,
    ).joinToString(" · ")

    private fun className(code: Int): String = when (code) {
        0x00 -> "definida por interface"
        0x01 -> "áudio"
        0x02 -> "comunicação"
        0x03 -> "HID"
        0x08 -> "armazenamento"
        0x09 -> "hub"
        0x0E -> "vídeo"
        0xEF -> "miscelânea"
        0xFF -> "do fabricante"
        else -> "0x%02x".format(code)
    }
}
