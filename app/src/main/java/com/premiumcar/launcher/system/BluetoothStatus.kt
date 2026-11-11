package com.premiumcar.launcher.system

import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Estado do Bluetooth segundo o Android.
 *
 * Nesta central o Bluetooth padrão do Android está desativado (`ro.atc.disable_android_bt=1`)
 * e quem cuida do Bluetooth é o app do fabricante. Por isso o Android pode não saber o estado
 * real: a Home só mostra o indicador quando o Android informa "ligado", em vez de exibir um
 * "desligado" possivelmente errado.
 */
object BluetoothStatus {

    enum class State { ON, OFF, UNKNOWN }

    fun read(context: Context): State = try {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        when {
            adapter == null -> State.UNKNOWN
            adapter.isEnabled -> State.ON
            else -> State.OFF
        }
    } catch (e: SecurityException) {
        State.UNKNOWN
    } catch (e: RuntimeException) {
        State.UNKNOWN
    }
}
