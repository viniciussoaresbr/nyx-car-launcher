package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.TrafficStats
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.system.BluetoothStatus
import com.premiumcar.launcher.system.TopActivity
import java.util.Locale

/**
 * O Nyx Car em volta do CarPlay (ARCHITECTURE.md, D20), pedidos do usuário:
 * - **Volta automática (0.4.5):** quando o CarPlay termina com o ZLINK na tela, o Nyx Car volta
 *   para a frente (sem isso, o ZLINK fica parado na tela de espera).
 * - **Tela de espera do Nyx Car (B1, 0.4.7):** enquanto o CarPlay conecta, uma janela do Nyx Car
 *   ([CarPlayWaitView]) cobre a tela de espera do ZLINK e sai 2 s depois de o vídeo do CarPlay
 *   chegar ([VideoStartDetector]). Pelo cabo, abre quando o iPhone aparece no USB. Sem fio (ou
 *   se o aviso do USB não chegou), quando o ZLINK aparece sozinho: uma vigia lê o app na tela
 *   por pouco tempo, quando a Home sai da tela ou quando o iPhone se conecta pelo Bluetooth ou
 *   o ponto de acesso Wi-Fi liga (0.4.8).
 * - **CarPlay sem iPhone (0.4.8):** tocar em CarPlay no Nyx Car sem nada conectado mostra
 *   "Aguardando o iPhone" em vez da tela parada do ZLINK; tocar e segurar abre o ZLINK.
 * - **Fim e queda (0.4.9):** o CarPlay terminou → "CarPlay desconectado" na hora, por 2 s, com
 *   o Nyx Car voltando por baixo. A conexão caiu antes de o CarPlay aparecer → "Conexão
 *   interrompida" por 5 s; se o iPhone não voltar, a tela e o ZLINK saem e o Nyx Car volta.
 *   Tempo máximo sem o CarPlay começar → a tela e o ZLINK saem.
 * - **Rapidez (0.4.9):** tudo roda numa thread própria, com prioridade de tela, sem passar pela
 *   fila de baixa prioridade nem disputar a thread principal; durante o CarPlay a janela fica
 *   reservada ([CarPlayWaitView.park]), para o Android não deixar o Nyx Car sem processador.
 * - **Toque na tela de espera (0.4.10):** volta ao Nyx Car e tira o ZLINK da frente (pedido do
 *   usuário: num mau contato, não cair na espera do ZLINK); tocar e segurar fecha só a tela.
 * - **Fim pela tela do ZLINK (0.4.11):** com o serviço de acessibilidade ligado pelo usuário
 *   ([ZlinkScreenService]), o ZLINK voltando para a tela de espera durante o CarPlay
 *   ([ZlinkScreens]) mostra "CarPlay desconectado" na hora, segundos antes de a central religar
 *   o Bluetooth. Sem o serviço, ou numa tela nunca vista, vale o Bluetooth, como antes.
 * - **Imagem do CarPlay (0.4.12):** a imagem do CarPlay sumindo da tela do ZLINK já vale como
 *   fim na primeira vez (conferida 100 ms depois); e, pelo cabo, o tempo máximo espera mais
 *   enquanto o ZLINK mostra "conectando" (até 1 min).
 *
 * Escuta só avisos públicos do Android enquanto o processo do app existir: nenhum serviço em
 * segundo plano. Leituras periódicas (app na tela, tráfego de rede) só em janelas curtas. Não
 * envia nada ao ZLINK nem mexe nele. Tudo vai para o diário do app e, durante uma gravação dos
 * testes no carro, também para a linha do tempo.
 */
@SuppressLint("StaticFieldLeak") // guarda só o contexto do app, que vive tanto quanto o processo
object CarPlayReturn {

    /** Ação (oculta no SDK) do estado da porta USB quando a central atua como aparelho. */
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val USB_CONNECTED = "connected"
    private const val APPLE_VENDOR_ID = 0x05AC

    /** Avisos (ocultos no SDK) de conexão do celular pelo Bluetooth (lado carro) e do ponto de acesso Wi-Fi. */
    private const val ACTION_A2DP_SINK = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
    private const val ACTION_HFP_CLIENT = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
    private const val ACTION_WIFI_AP = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_WIFI_AP_STATE = "wifi_state"
    private const val WIFI_AP_ENABLING = 12
    private const val WIFI_AP_ENABLED = 13

    /** Um app aberto pelo usuário no Nyx Car nesse intervalo não conta como "o ZLINK abriu sozinho". */
    private const val USER_LAUNCH_WINDOW_MS = 3_000L
    private const val SAMPLE_MS = 250L

    /** Vigia do ZLINK: leitura do app na tela (100 ms na 0.4.9) e quanto tempo dura em cada caso. */
    private const val WATCH_POLL_MS = 100L
    private const val WATCH_AFTER_PAUSE_MS = 2_500L
    private const val WATCH_AFTER_PHONE_MS = 20_000L

    /** Do vídeo detectado até fechar a tela: 2 s, para o CarPlay já estar desenhado (pedido do usuário, 0.4.8). */
    private const val READY_HIDE_DELAY_MS = 2_000L

    /**
     * Pelo cabo, do iPhone no USB até a central virar aparelho dele ("cabo conectado"): até 3 s
     * nas gravações de 12/11/2026. Sem isso em 6 s, a conexão caiu (cabo tirado ou mau contato).
     */
    internal const val CABLE_LINK_MS = 6_000L

    /** Conexão caiu antes de o CarPlay aparecer: a tela espera o iPhone voltar por este tempo (0.4.9). */
    internal const val INTERRUPTED_HOLD_MS = 5_000L

    /** "CarPlay desconectado" fica na tela por este tempo, com o Nyx Car já voltando por baixo (0.4.9). */
    internal const val DISCONNECTED_SHOW_MS = 2_000L

    /** Quando o Nyx Car é chamado de volta, a tela fecha quando ele aparece, ou depois deste tempo. */
    private const val RETURN_HIDE_MS = 1_500L

    /** "Aguardando o iPhone" fecha sozinha depois de 1 min, se nada conectar. */
    private const val WAITING_PHONE_MS = 60_000L

    /** Na gravação, quantos nomes de elementos de cada tela do ZLINK aparecem. */
    private const val MAX_LOGGED_IDS = 12

    /** A imagem do CarPlay sumiu numa tela nunca vista: a tela é conferida de novo depois disto. */
    internal const val SCREEN_CONFIRM_MS = 100L

    /** Pelo cabo, com o ZLINK mostrando "conectando", a tela de espera fica até este total. */
    internal const val MAX_CONNECTING_MS = 60_000L

    /**
     * Pelo cabo ou sem fio: limite do tráfego do vídeo e tempo máximo da tela de espera. Nas
     * gravações, o vídeo chegou a ~1,2 MB/s pelo cabo (com 294 KB/s logo antes, cedo demais) e a
     * 250 a 430 KB/s sem fio.
     */
    enum class WaitMode(val label: String, val thresholdBytesPerSecond: Long, val timeoutMs: Long, val state: CarPlayWaitState) {
        CABLE("cabo", 512L * 1024, 20_000L, CarPlayWaitState.CONNECTING_CABLE),
        WIRELESS("sem fio", 160L * 1024, 25_000L, CarPlayWaitState.CONNECTING_WIRELESS),
    }

    /** Mensagem de fim na tela (0.4.9). */
    private enum class Hold { INTERRUPTED, DISCONNECTED }

    /** Lê o app na tela (a rápida; o `getprop` só se ela faltar); trocado nos testes. */
    internal var topActivity: () -> String? = TopActivity::read

    /** Leitura rápida do app na tela, usada pela vigia; trocada nos testes. */
    internal var fastTop: () -> String? = TopActivity::readFast

    /** Total de bytes recebidos pelo aparelho; trocado nos testes. */
    internal var traffic: () -> Long = TrafficStats::getTotalRxBytes

    /** A tela de espera; criada na instalação (nos testes, uma falsa). */
    internal var waitView: CarPlayWaitView? = null

    /** Nos testes, tudo roda na thread principal, no relógio do Robolectric. */
    internal var useMainThread = false

    /**
     * Thread própria do CarPlay (0.4.9): recebe os avisos, lê o app na tela e cuida da janela.
     * A thread principal pode estar ocupada desenhando outra tela do app (o Nyx Diagnóstico
     * gravando, por exemplo); a fila de baixa prioridade, com arquivos e outras leituras.
     */
    private var thread: HandlerThread? = null
    private var handler = Handler(Looper.getMainLooper())

    private var session = CarPlaySession()

    /** Só o contexto do app (vive tanto quanto o processo), nunca o de uma tela. */
    private var app: Context? = null

    // Estado da tela, só na thread do CarPlay.
    private var waitMode: WaitMode? = null
    private var hold: Hold? = null
    private var waitingPhone = false
    private var waitStartedAt = 0L
    private var detector: VideoStartDetector? = null
    private var watchUntil = 0L
    private var watchSawOther = false
    private var reserveUntil = 0L
    private var noPermissionLogged = false

    // Lidos também pela thread principal.
    @Volatile
    private var ready = false

    @Volatile
    private var lastUserLaunch: Long? = null

    // Fim pela tela do ZLINK (0.4.11).
    @Volatile
    private var screenService: ZlinkScreenService? = null

    @Volatile
    private var screenWindow = -1
    private var screens = ZlinkScreens()
    private var screenReadPending = false
    private var lastScreenLogged: String? = null
    private var screenEndAt = 0L
    private var screenServiceLogged = false
    private var lastZlinkScreen: ZlinkScreen? = null

    /** Lê a tela do ZLINK agora (a conferência do fim); trocada nos testes. */
    internal var readScreenNow: () -> ZlinkScreen? = { screenService?.readZlink(screenWindow) }

    /** Uma vez por processo (o principal; ver NyxCarApplication). */
    fun install(context: Context) {
        if (app != null) return
        val appContext = context.applicationContext
        app = appContext
        handler = Handler(if (useMainThread) Looper.getMainLooper() else startThread())
        if (waitView == null) waitView = CarPlayWaitScreen(appContext)
        screens = ZlinkScreenStore.load(appContext)
        PlayerLog.attach(appContext)
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(ACTION_A2DP_SINK)
            addAction(ACTION_HFP_CLIENT)
            addAction(ACTION_WIFI_AP)
        }
        // Avisos protegidos do sistema: só o Android pode enviá-los. Entregues na thread do CarPlay.
        ContextCompat.registerReceiver(appContext, receiver, filter, null, handler, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun startThread(): Looper {
        val started = HandlerThread("nyx-carplay").also { it.start() }
        thread = started
        // Prioridade de tela (a mesma das animações do Android); se o sistema recusar, segue a normal.
        Handler(started.looper).post { runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) } }
        return started.looper
    }

    /** Chamadas das telas (thread principal) passam para a thread do CarPlay. */
    private fun onCarPlayThread(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    // ---------- Avisos do Android ----------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_STATE -> onUsb(intent.getBooleanExtra(USB_CONNECTED, false))
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    onBluetooth(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION") // getParcelableExtra(String): a forma tipada só existe no Android 13+.
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device?.vendorId == APPLE_VENDOR_ID) onAppleAttached()
                }
                // CarPlay sem fio pode estar começando, com qualquer app na tela: vigia o ZLINK.
                BluetoothDevice.ACTION_ACL_CONNECTED -> watchZlink(WATCH_AFTER_PHONE_MS, reserve = true)
                ACTION_A2DP_SINK, ACTION_HFP_CLIENT -> {
                    // Só "conectado" (0.4.10): sem o iPhone, a central tenta o HFP a cada ~10 s
                    // ("conectando"), o que mantinha a vigia armada o tempo todo.
                    if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                        watchZlink(WATCH_AFTER_PHONE_MS, reserve = true)
                    }
                }
                ACTION_WIFI_AP -> {
                    val state = intent.getIntExtra(EXTRA_WIFI_AP_STATE, -1)
                    if (state == WIFI_AP_ENABLING || state == WIFI_AP_ENABLED) watchZlink(WATCH_AFTER_PHONE_MS, reserve = true)
                }
            }
        }
    }

    private fun onAppleAttached() {
        val badContact = session.appleAttached()
        log(if (badContact) "iPhone voltou ao USB logo depois de sair (mau contato): a sessão continua" else "iPhone apareceu no USB (Apple)")
        // Pelo cabo, o ZLINK abre sozinho ~3 s depois: a tela de espera do Nyx Car já entra agora.
        startWait(WaitMode.CABLE)
        if (waitMode == WaitMode.CABLE && !session.cableConnected) handler.postDelayed(cableLinkMissing, CABLE_LINK_MS)
    }

    private fun onUsb(connected: Boolean) {
        val wasConnected = session.cableConnected
        if (connected) handler.removeCallbacks(cableLinkMissing)
        if (session.usb(connected)) {
            endSignal()
        } else if (connected && !wasConnected) {
            log("cabo conectado (a central virou aparelho USB do iPhone)")
            if (waitView?.visible != true) releaseWindow() // sessão começou: a janela fica reservada
        }
    }

    private fun onBluetooth(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                val before = session.bluetoothOff
                session.bluetooth(on = false, zlinkOnTop = isZlink(readTop()))
                if (!before && session.bluetoothOff) {
                    log("Bluetooth da central desligou com o ZLINK na tela")
                    screens.carPlayStarted(SystemClock.uptimeMillis())
                    stopWatch() // o CarPlay já está entrando: a vigia não precisa continuar
                    // Durante "Conexão interrompida", o iPhone voltou: a conexão segue.
                    if (hold == Hold.INTERRUPTED) startWait(if (session.cableConnected) WaitMode.CABLE else WaitMode.WIRELESS)
                    if (waitMode != null) preparing() else if (waitView?.visible != true) releaseWindow()
                }
            }
            BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_ON ->
                if (session.bluetooth(on = true, zlinkOnTop = false)) endSignal()
        }
    }

    // ---------- Telas do Nyx Car (chamadas pela MainActivity e pelo AppLauncher) ----------

    /** O usuário abriu um app pelo Nyx Car (inclusive o CarPlay): não é o ZLINK abrindo sozinho. */
    fun markUserLaunch() {
        lastUserLaunch = SystemClock.uptimeMillis()
    }

    /**
     * O usuário tocou em CarPlay no Nyx Car. Sem nada conectado, mostra "Aguardando o iPhone" em
     * vez da tela parada do ZLINK e devolve `true` (o ZLINK não é aberto). Com o CarPlay
     * conectado (Bluetooth da central desligado, cabo conectado ou vídeo já visto) ou sem a
     * permissão de sobrepor, devolve `false` e o ZLINK abre como sempre.
     */
    fun interceptCarPlay(): Boolean {
        val context = app ?: return false
        val view = waitView ?: return false
        if (!view.canShow()) return false
        val connected = session.cableConnected || (session.active && ready) ||
            BluetoothStatus.read(context) == BluetoothStatus.State.OFF
        if (connected) return false
        onCarPlayThread {
            stopTimers()
            waitMode = null
            hold = null
            waitingPhone = true
            view.show(CarPlayWaitState.WAITING_PHONE, ::onPhoneWaitTap, ::openZlinkAnyway)
            handler.postDelayed(closeNow, WAITING_PHONE_MS)
            log("CarPlay tocado sem iPhone conectado: \"Aguardando o iPhone\" no lugar da tela do ZLINK")
        }
        return true
    }

    /** A Home saiu da tela: se for o ZLINK abrindo sozinho, a tela de espera entra (vigia curta). */
    fun onLauncherPaused() = onCarPlayThread { watchZlink(WATCH_AFTER_PAUSE_MS, fromHome = true) }

    /**
     * A Home voltou para a tela (botão Home, Voltar ou a volta automática): a tela de espera sai.
     * "Aguardando o iPhone" e as mensagens de fim ficam até o tempo delas.
     */
    fun onLauncherResumed() = onCarPlayThread {
        if (waitView?.visible != true || waitingPhone || hold != null) return@onCarPlayThread
        if (waitMode != null) log("o Nyx Car voltou para a tela durante a conexão: tela de espera fechada")
        closeWait()
    }

    // ---------- Vigia do ZLINK (0.4.8) ----------

    /**
     * Lê o app na tela a cada 100 ms por [durationMs]; se o ZLINK **chegar** à tela sozinho, a
     * tela de espera entra. Só conta a chegada (0.4.10): o ZLINK que já estava na tela (aberto
     * pelo usuário ou parado na espera dele) não dispara nada. [fromHome]: a Home acabou de sair
     * da tela, então o que vier agora é uma chegada. Com [reserve] (iPhone chegando pelo
     * Bluetooth), a janela fica reservada enquanto isso, para aparecer mais rápido.
     */
    private fun watchZlink(durationMs: Long, reserve: Boolean = false, fromHome: Boolean = false) {
        if (app == null) return
        if (!TopActivity.fast) {
            // Sem a leitura rápida neste firmware: uma conferência só, um pouco depois.
            if (fromHome) handler.postDelayed({ if (isZlink(readTop())) zlinkAppeared() }, 800)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now > watchUntil) watchSawOther = false // vigia nova
        watchSawOther = watchSawOther || fromHome || !isZlink(readTop())
        val until = now + durationMs
        watchUntil = maxOf(watchUntil, until)
        if (reserve) {
            reserveUntil = maxOf(reserveUntil, until)
            if (waitView?.visible != true) releaseWindow()
        }
        handler.removeCallbacks(watch)
        handler.post(watch)
    }

    private val watch = object : Runnable {
        override fun run() {
            if (SystemClock.uptimeMillis() > watchUntil) {
                reserveUntil = 0L
                if (waitView?.visible != true) releaseWindow()
                return
            }
            val top = runCatching { fastTop() }.getOrNull()
            if (isZlink(top) && watchSawOther) {
                stopWatch()
                zlinkAppeared()
                if (waitView?.visible != true) releaseWindow()
            } else {
                if (top != null && !isZlink(top)) watchSawOther = true
                handler.postDelayed(this, WATCH_POLL_MS)
            }
        }
    }

    private fun stopWatch() {
        watchUntil = 0L
        reserveUntil = 0L
        watchSawOther = false
        handler.removeCallbacks(watch)
    }

    /**
     * O ZLINK chegou à tela. A tela de espera entra, a não ser que: o usuário tenha aberto o
     * CarPlay agora, ela já esteja mostrando uma conexão ou uma mensagem de fim, o CarPlay já
     * esteja pronto ou o Bluetooth da central já esteja desligado (CarPlay conectado).
     */
    private fun zlinkAppeared() {
        val context = app ?: return
        val launched = lastUserLaunch
        if (launched != null && SystemClock.uptimeMillis() - launched < USER_LAUNCH_WINDOW_MS) return
        if (waitMode != null || hold != null) return
        if (session.active && ready) return
        if (BluetoothStatus.read(context) == BluetoothStatus.State.OFF) return
        startWait(if (session.cableConnected) WaitMode.CABLE else WaitMode.WIRELESS)
    }

    // ---------- Tela de espera (B1) ----------

    private fun startWait(mode: WaitMode) {
        val view = waitView ?: return
        ready = false
        if (!view.canShow()) {
            if (!noPermissionLogged) log("tela de espera do Nyx Car desligada: falta a permissão \"Sobrepor a outros apps\"")
            noPermissionLogged = true
            return
        }
        stopTimers()
        waitMode = mode
        hold = null
        waitingPhone = false
        detector = null
        screenEndAt = 0L
        waitStartedAt = SystemClock.uptimeMillis()
        view.show(mode.state, ::onConnectingTap, ::onConnectingLongPress)
        handler.postDelayed(timeout, mode.timeoutMs)
        log("tela de espera do Nyx Car aberta (${mode.label})")
    }

    /** O CarPlay está entrando (Bluetooth da central desligou): começa a procurar o vídeo. */
    private fun preparing() {
        val mode = waitMode ?: return
        waitView?.show(CarPlayWaitState.PREPARING, ::onConnectingTap, ::onConnectingLongPress)
        detector = VideoStartDetector(mode.thresholdBytesPerSecond)
        handler.removeCallbacks(sample)
        handler.post(sample)
    }

    private val sample = object : Runnable {
        override fun run() {
            val found = detector ?: return
            if (found.add(SystemClock.uptimeMillis(), runCatching { traffic() }.getOrDefault(-1L))) {
                videoStarted()
            } else {
                handler.postDelayed(this, SAMPLE_MS)
            }
        }
    }

    private fun videoStarted() {
        detector = null
        ready = true
        log("vídeo do CarPlay chegou ${seconds(SystemClock.uptimeMillis() - waitStartedAt)} depois de a tela de espera abrir: ela fecha em 2 s")
        handler.removeCallbacks(timeout)
        handler.postDelayed(closeNow, READY_HIDE_DELAY_MS)
    }

    /**
     * Tempo máximo sem o vídeo. Se o CarPlay nem começou (a central não desligou o Bluetooth), o
     * ZLINK está parado na espera dele: a tela e o ZLINK saem (0.4.9). Se começou, só o vídeo não
     * foi percebido: a tela sai e mostra o CarPlay.
     */
    private val timeout: Runnable = Runnable {
        val mode = waitMode ?: return@Runnable
        val elapsed = SystemClock.uptimeMillis() - waitStartedAt
        // Pelo cabo, com o ZLINK ainda mostrando "conectando" (serviço de acessibilidade), a
        // conexão está andando devagar (no teste de 12/11, o CarPlay entrou 31 s depois do cabo).
        if (mode == WaitMode.CABLE && session.cableConnected && !session.bluetoothOff &&
            lastZlinkScreen?.connecting == true && elapsed + mode.timeoutMs <= MAX_CONNECTING_MS
        ) {
            handler.postDelayed(timeout, mode.timeoutMs)
            log("${seconds(elapsed)} sem o CarPlay, mas o ZLINK ainda mostra \"conectando\" e o cabo está conectado: a tela de espera continua")
            return@Runnable
        }
        if (!session.bluetoothOff && isZlink(readTop())) {
            returnHome()
            log("tempo máximo (${seconds(elapsed)}) e o CarPlay não começou: tela de espera e ZLINK fechados, Nyx Car na frente")
        } else {
            closeWait()
            log("tempo máximo (${seconds(elapsed)}) sem o vídeo do CarPlay: tela de espera fechada")
        }
    }

    /** Pelo cabo, o iPhone apareceu no USB mas a central não virou aparelho dele: a conexão caiu. */
    private val cableLinkMissing = Runnable {
        if (waitMode == WaitMode.CABLE && !session.cableConnected) {
            interrupted("o cabo não completou a conexão em ${seconds(CABLE_LINK_MS)}")
        }
    }

    private val closeNow = Runnable { closeWait() }

    /** "Aguardando o iPhone" → toque: a tela sai (a Home já está por baixo). */
    private fun onPhoneWaitTap() {
        closeWait()
        log("tela de espera fechada com um toque")
    }

    /**
     * "Conectando ao CarPlay" → toque (0.4.10, pedido do usuário): volta ao Nyx Car e tira o
     * ZLINK da frente, em vez de mostrar a espera dele. Se o CarPlay terminar de conectar, tocar
     * em CarPlay no Nyx Car abre o ZLINK direto.
     */
    private fun onConnectingTap() {
        if (isZlink(readTop())) {
            returnHome()
            log("tela de espera tocada: ela e o ZLINK fechados, Nyx Car na frente")
        } else {
            closeWait()
            log("tela de espera fechada com um toque")
        }
    }

    /** "Conectando ao CarPlay" → tocar e segurar: fecha só a tela e mostra o ZLINK. */
    private fun onConnectingLongPress() {
        closeWait()
        log("tela de espera fechada com um toque longo: o ZLINK fica na tela")
    }

    /** "Aguardando o iPhone" → tocar e segurar: abre o ZLINK mesmo assim (por exemplo, para os ajustes dele). */
    private fun openZlinkAnyway() {
        val context = app ?: return
        closeWait()
        markUserLaunch()
        val intent = AppLauncher(context).resolve(CarApps.CARPLAY)
        val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
        log(if (opened) "ZLINK aberto mesmo sem iPhone (toque longo)" else "não deu para abrir o ZLINK")
    }

    // ---------- Fim do CarPlay e queda da conexão (0.4.9) ----------

    /**
     * O cabo caiu ou o Bluetooth da central religou: a sessão terminou. Com o ZLINK na tela, a
     * janela do Nyx Car entra na hora: "Conexão interrompida", se o CarPlay ainda estava
     * conectando, ou "CarPlay desconectado". Com outro app na tela, nada muda.
     */
    private fun endSignal() {
        val signal = session.confirmEnd() ?: return
        val now = SystemClock.uptimeMillis()
        screens.sessionEnded(now)
        saveScreens()
        val warnedAt = screenEndAt
        screenEndAt = 0L
        if (warnedAt != 0L) {
            // A tela do ZLINK já tinha mostrado o fim: só registra quanto antes foi.
            log("terminou (${signal.label}) ${seconds(now - warnedAt)} depois de a tela do ZLINK mostrar o fim")
            ready = false
            if (hold != null) return
            if (!isZlink(readTop())) {
                if (waitView?.visible != true) releaseWindow()
                return
            }
            // O ZLINK voltou para a frente nesse meio-tempo: segue como um fim comum.
        }
        val connecting = waitMode != null && !ready
        stopTimers()
        waitMode = null
        detector = null
        ready = false
        val top = readTop()
        val view = waitView
        when {
            isZlink(top) && view != null && view.canShow() ->
                if (connecting) interrupted(signal.label) else disconnected(signal.label)
            isZlink(top) -> {
                bringHome()
                log("terminou (${signal.label}): o Nyx Car voltou para a frente")
            }
            else -> {
                if (!waitingPhone) closeWait()
                log(
                    if (top == null) {
                        "terminou (${signal.label}); não deu para ler a tela, nada mudou"
                    } else {
                        "terminou (${signal.label}); na tela estava ${TopActivity.packageOf(top)}, nada mudou"
                    },
                )
            }
        }
    }

    /** CarPlay em uso terminou: "CarPlay desconectado" por 2 s, com o Nyx Car voltando por baixo. */
    private fun disconnected(reason: String) {
        showDisconnected()
        log("terminou ($reason): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente")
    }

    private fun showDisconnected() {
        hold = Hold.DISCONNECTED
        waitingPhone = false
        waitView?.show(CarPlayWaitState.DISCONNECTED, ::onHoldTap)
        bringHome()
        handler.postDelayed(holdOver, DISCONNECTED_SHOW_MS)
    }

    // ---------- Fim pela tela do ZLINK (0.4.11) ----------

    /** O serviço de acessibilidade foi ligado (pelo usuário, em Acessibilidade) ou religado pelo Android. */
    fun screenServiceConnected(service: ZlinkScreenService) {
        screenService = service
        onCarPlayThread {
            if (!screenServiceLogged) log("serviço de acessibilidade do ZLINK ligado: o fim do CarPlay é visto pela tela dele")
            screenServiceLogged = true
        }
    }

    fun screenServiceDisconnected(service: ZlinkScreenService) {
        if (screenService === service) screenService = null
    }

    /** A tela do ZLINK mudou (aviso do serviço, na thread principal): lê a estrutura na thread do CarPlay. */
    fun onZlinkScreenChanged(windowId: Int) {
        screenWindow = windowId
        onCarPlayThread {
            if (screenReadPending) return@onCarPlayThread
            screenReadPending = true
            handler.post(readScreen)
        }
    }

    private val readScreen = Runnable {
        screenReadPending = false
        runCatching { readScreenNow() }.getOrNull()?.let(::onZlinkScreen)
    }

    /** A imagem do CarPlay sumiu numa tela nunca vista: se continuar sem ela, é o fim. */
    private val confirmScreenEnd = Runnable {
        val again = runCatching { readScreenNow() }.getOrNull() ?: return@Runnable
        if (again.projection) {
            onZlinkScreen(again) // a imagem voltou: não era o fim
            return@Runnable
        }
        lastZlinkScreen = again
        if (screens.confirmEnd(again.signature)) {
            saveScreens()
            screenEnded()
        }
    }

    /** Uma tela do ZLINK lida agora (nos testes, chamada direto). */
    internal fun onZlinkScreen(screen: ZlinkScreen) {
        val context = app ?: return
        val now = SystemClock.uptimeMillis()
        val mode = when {
            session.active && session.bluetoothOff -> ZlinkScreens.Mode.CARPLAY
            // Bluetooth desligado sem a sessão registrada: pode ser o CarPlay, não aprende nada.
            BluetoothStatus.read(context) == BluetoothStatus.State.OFF -> ZlinkScreens.Mode.UNSURE
            else -> ZlinkScreens.Mode.NO_CARPLAY
        }
        val result = screens.update(screen.signature, now, mode, screen.projection)
        lastZlinkScreen = screen
        saveScreens()
        logScreen(screen)
        when (result) {
            ZlinkScreens.Result.END -> {
                handler.removeCallbacks(confirmScreenEnd)
                screenEnded()
            }
            ZlinkScreens.Result.MAYBE_END -> {
                handler.removeCallbacks(confirmScreenEnd)
                handler.postDelayed(confirmScreenEnd, SCREEN_CONFIRM_MS)
            }
            ZlinkScreens.Result.NONE -> if (screen.projection) handler.removeCallbacks(confirmScreenEnd)
        }
    }

    /**
     * Durante o CarPlay, o ZLINK voltou para uma tela de espera: o CarPlay terminou, antes de a
     * central religar o Bluetooth. "CarPlay desconectado" entra na hora e o Nyx Car volta. A
     * sessão só é dada por encerrada no aviso do Bluetooth ou do cabo, que vem depois.
     */
    private fun screenEnded() {
        if (!isZlink(readTop()) || hold != null) return
        screenEndAt = SystemClock.uptimeMillis()
        stopTimers()
        waitMode = null
        detector = null
        ready = false
        val view = waitView
        if (view != null && view.canShow()) showDisconnected() else bringHome()
        log("a tela do ZLINK voltou para a espera (o CarPlay terminou): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente")
    }

    private fun saveScreens() {
        val context = app ?: return
        if (screens.changed) ZlinkScreenStore.save(context, screens)
    }

    /** Na gravação dos testes: cada tela nova do ZLINK, só a estrutura (nunca os textos). */
    private fun logScreen(screen: ZlinkScreen) {
        if (!CarTestRecorder.running || screen.signature == lastScreenLogged) return
        lastScreenLogged = screen.signature
        val kind = when (screens.kind(screen.signature)) {
            ZlinkScreens.Kind.WAIT -> "espera"
            ZlinkScreens.Kind.CARPLAY -> "CarPlay"
            ZlinkScreens.Kind.UNKNOWN -> "nova"
        }
        val ids = screen.ids.take(MAX_LOGGED_IDS).joinToString(", ").ifEmpty { "nenhum" } +
            if (screen.ids.size > MAX_LOGGED_IDS) " …" else ""
        val marks = listOfNotNull(
            "com a imagem do CarPlay".takeIf { screen.projection },
            "conectando".takeIf { screen.connecting },
        ).joinToString(", ").let { if (it.isEmpty()) "" else "; $it" }
        CarTestLog.add(
            "ZLINK (acessibilidade): tela ${screen.signature} ($kind) — ${screen.elements} elementos, " +
                "${screen.texts} com texto, ${screen.touchable} tocáveis$marks; nomes: $ids",
        )
    }

    /** A conexão caiu antes de o CarPlay aparecer: a tela espera o iPhone voltar por 5 s. */
    private fun interrupted(reason: String) {
        stopTimers()
        waitMode = null
        detector = null
        hold = Hold.INTERRUPTED
        waitingPhone = false
        waitView?.show(CarPlayWaitState.INTERRUPTED, ::onHoldTap)
        handler.postDelayed(holdOver, INTERRUPTED_HOLD_MS)
        log("conexão interrompida antes de o CarPlay aparecer ($reason): aguardando o iPhone por ${seconds(INTERRUPTED_HOLD_MS)}")
    }

    /** Fim do tempo da mensagem: se o ZLINK ainda estiver na frente, ele sai e o Nyx Car volta. */
    private val holdOver = Runnable {
        val kind = hold ?: return@Runnable
        hold = null
        val zlink = isZlink(readTop())
        if (zlink) returnHome() else closeWait()
        if (kind == Hold.INTERRUPTED) {
            log(
                if (zlink) {
                    "o iPhone não voltou: tela de espera e ZLINK fechados, Nyx Car na frente"
                } else {
                    "o iPhone não voltou: tela de espera fechada"
                },
            )
        }
    }

    private fun onHoldTap() {
        handler.removeCallbacks(holdOver)
        log("mensagem de fim fechada com um toque")
        holdOver.run()
    }

    /** O Nyx Car volta para a frente e a tela sai quando ele aparecer (ou logo depois). */
    private fun returnHome() {
        stopTimers()
        waitMode = null
        hold = null
        waitingPhone = false
        detector = null
        bringHome()
        handler.postDelayed(closeNow, RETURN_HIDE_MS)
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

    // ---------- Janela ----------

    private fun closeWait() {
        stopTimers()
        waitMode = null
        hold = null
        waitingPhone = false
        detector = null
        releaseWindow()
    }

    /**
     * Nada para mostrar: a janela fica reservada (invisível) enquanto o CarPlay estiver em uso ou
     * prestes a começar; senão, sai de vez.
     */
    private fun releaseWindow() {
        val view = waitView ?: return
        val reserve = session.active || SystemClock.uptimeMillis() < reserveUntil
        if (reserve && view.canShow()) view.park() else view.hide()
    }

    private fun stopTimers() {
        handler.removeCallbacks(confirmScreenEnd)
        handler.removeCallbacks(sample)
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(closeNow)
        handler.removeCallbacks(cableLinkMissing)
        handler.removeCallbacks(holdOver)
    }

    // ---------- Apoio ----------

    private fun isZlink(top: String?) = TopActivity.packageOf(top) == CarApps.CARPLAY.packageName

    /** App na tela, lido na hora (microssegundos; o `getprop` só se a leitura rápida faltar). */
    private fun readTop(): String? = runCatching { topActivity() }.getOrNull()

    private fun seconds(millis: Long) = String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", millis / 1000.0)

    private fun log(text: String) {
        PlayerLog.add("CarPlay: $text")
        if (CarTestRecorder.running) CarTestLog.add("Nyx Car — CarPlay: $text")
    }

    internal fun resetForTests() {
        app?.let { runCatching { it.unregisterReceiver(receiver) } }
        app = null
        handler.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = Handler(Looper.getMainLooper())
        session = CarPlaySession()
        topActivity = TopActivity::read
        fastTop = TopActivity::readFast
        traffic = TrafficStats::getTotalRxBytes
        waitView?.hide()
        waitView = null
        useMainThread = false
        waitMode = null
        hold = null
        waitingPhone = false
        detector = null
        ready = false
        lastUserLaunch = null
        watchUntil = 0L
        watchSawOther = false
        reserveUntil = 0L
        noPermissionLogged = false
        screenService = null
        screenWindow = -1
        screens = ZlinkScreens()
        screenReadPending = false
        lastScreenLogged = null
        screenEndAt = 0L
        screenServiceLogged = false
        lastZlinkScreen = null
        readScreenNow = { screenService?.readZlink(screenWindow) }
    }
}
