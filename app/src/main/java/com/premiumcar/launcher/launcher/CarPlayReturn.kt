package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.system.Background
import com.premiumcar.launcher.system.TopActivity

/**
 * Volta para o Nyx Car quando o CarPlay termina com o ZLINK na tela (0.4.5, pedido do usuário):
 * sem isso, o ZLINK fica parado na tela de espera depois que o iPhone é desconectado.
 *
 * - Escuta só avisos públicos do Android (estado da porta USB, Bluetooth, aparelho USB
 *   conectado) enquanto o processo do app existir: nenhum serviço em segundo plano. Não envia
 *   nada ao ZLINK nem mexe nele; quando volta, abre o Nyx Car como o botão Home faria.
 * - Quem está na tela vem de `sys.top.activity` ([TopActivity]): se for outro app (Maps, por
 *   exemplo), nada muda. Se a leitura falhar, também não.
 * - Quando a sessão termina é decidido por [CarPlaySession]. Tudo vai para o diário do app e,
 *   durante uma gravação dos testes no carro, também para a linha do tempo.
 */
@SuppressLint("StaticFieldLeak") // guarda só o contexto do app, que vive tanto quanto o processo
object CarPlayReturn {

    /** Ação (oculta no SDK) do estado da porta USB quando a central atua como aparelho. */
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val USB_CONNECTED = "connected"
    private const val APPLE_VENDOR_ID = 0x05AC

    /** Lê o app na tela; trocado nos testes. */
    internal var topActivity: () -> String? = TopActivity::read

    private val main = Handler(Looper.getMainLooper())
    private var session = CarPlaySession()

    /** Só o contexto do app (vive tanto quanto o processo), nunca o de uma tela. */
    private var app: Context? = null

    /** Uma vez por processo (o principal; ver NyxCarApplication). */
    fun install(context: Context) {
        if (app != null) return
        val appContext = context.applicationContext
        app = appContext
        PlayerLog.attach(appContext)
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }
        // Avisos protegidos do sistema: só o Android pode enviá-los.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_STATE -> onUsb(intent.getBooleanExtra(USB_CONNECTED, false))
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    onBluetooth(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION") // getParcelableExtra(String): a forma tipada só existe no Android 13+.
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device?.vendorId == APPLE_VENDOR_ID) {
                        val badContact = session.appleAttached()
                        log(if (badContact) "iPhone voltou ao USB logo depois de sair (mau contato): a sessão continua" else "iPhone apareceu no USB (Apple)")
                    }
                }
            }
        }
    }

    private fun onUsb(connected: Boolean) {
        val wasConnected = session.cableConnected
        if (session.usb(connected)) {
            scheduleConfirm()
        } else if (connected && !wasConnected) {
            log("cabo conectado (a central virou aparelho USB do iPhone)")
        }
    }

    /** Fim provável: confirma depois da espera, se nada indicar reconexão até lá. */
    private fun scheduleConfirm() {
        main.removeCallbacks(confirmEnd)
        main.postDelayed(confirmEnd, CarPlaySession.END_GRACE_MS)
    }

    private val confirmEnd = Runnable { session.confirmEnd()?.let(::ended) }

    private fun onBluetooth(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> withTop { top ->
                val before = session.bluetoothOff
                session.bluetooth(on = false, zlinkOnTop = isZlink(top))
                if (!before && session.bluetoothOff) log("Bluetooth da central desligou com o ZLINK na tela")
            }
            BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_ON ->
                if (session.bluetooth(on = true, zlinkOnTop = false)) scheduleConfirm()
        }
    }

    /** Fim da sessão: volta para o Nyx Car só se o ZLINK estiver na tela. */
    private fun ended(signal: CarPlaySession.Signal) = withTop { top ->
        when {
            isZlink(top) -> {
                bringHome()
                log("terminou (${signal.label}): o Nyx Car voltou para a frente")
            }
            top == null -> log("terminou (${signal.label}); não deu para ler a tela, nada mudou")
            else -> log("terminou (${signal.label}); na tela estava ${TopActivity.packageOf(top)}, nada mudou")
        }
    }

    private fun bringHome() {
        val context = app ?: return
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            log("não deu para voltar ao Nyx Car (${e.javaClass.simpleName})")
        }
    }

    private fun isZlink(top: String?) = TopActivity.packageOf(top) == CarApps.CARPLAY.packageName

    /** `getprop` abre um processo: a leitura vai para a thread de segundo plano. */
    private fun withTop(block: (String?) -> Unit) {
        Background.executor.execute {
            val top = runCatching { topActivity() }.getOrNull()
            main.post { block(top) }
        }
    }

    private fun log(text: String) {
        PlayerLog.add("CarPlay: $text")
        if (CarTestRecorder.running) CarTestLog.add("Nyx Car — CarPlay: $text")
    }

    internal fun resetForTests() {
        app?.let { runCatching { it.unregisterReceiver(receiver) } }
        app = null
        main.removeCallbacksAndMessages(null)
        session = CarPlaySession()
        topActivity = TopActivity::read
    }
}
