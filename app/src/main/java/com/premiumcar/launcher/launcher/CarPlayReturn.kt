package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.premiumcar.launcher.app.MainActivity
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.player.PlayerLog
import com.premiumcar.launcher.system.BluetoothStatus
import com.premiumcar.launcher.system.FastProperty
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
 * - **Saída sem o ZLINK no meio (0.4.15):** quando a Home é chamada de volta, ela vem sem
 *   animação e a tela do Nyx Car só sai com a Home já desenhada ([onLauncherShown]). Se a
 *   conexão caiu e o ZLINK voltar sozinho logo depois de fechado, ele é coberto e sai de novo.
 * - **Imagem do CarPlay (0.4.12):** a imagem do CarPlay sumindo da tela do ZLINK já vale como
 *   fim na primeira vez (conferida 100 ms depois); e, pelo cabo, o tempo máximo espera mais
 *   enquanto o ZLINK mostra "conectando" (até 1 min).
 * - **Cobrir primeiro (0.4.17):** no fim, a janela do Nyx Car entra antes de tudo, já desenhada
 *   e reservada fora da tela; a Home só é chamada depois do quadro dela ([coverThenHome]). Com o
 *   serviço ligado, o Bluetooth religando com a imagem do CarPlay ainda na tela do ZLINK não é
 *   fim, e a imagem do CarPlay na tela sem a sessão registrada volta a vigiar o fim.
 * - **Só a janela antes do quadro (0.4.18):** no fim, guardar e registrar a tela do ZLINK fica
 *   para depois do quadro da janela; a thread do CarPlay tem prioridade de tela urgente; e a
 *   gravação mede cada etapa, do aviso do ZLINK até a janela desenhada.
 * - **Sem esperar o quadro (0.4.19):** a janela sai da reserva e vai para o lugar na hora
 *   ([CarPlayWaitScreen]); durante o CarPlay, a thread principal, que recebe os avisos do
 *   serviço de acessibilidade, também tem prioridade de tela urgente ([boostMainThread]).
 * - **Fim pelo som (0.4.21):** sem acessibilidade nem captura da tela (decisão do usuário), a
 *   música do CarPlay parando como no fim ([CarPlaySound]) põe "CarPlay desconectado" na tela na
 *   hora, junto com a troca da tela do ZLINK. A Home só volta quando o Bluetooth ou o cabo
 *   confirmarem; sem isso em [SOUND_CONFIRM_MS], a tela sai e o CarPlay continua à vista. O
 *   Bluetooth religando com a música ainda tocando não é fim. Sem música, vale o Bluetooth.
 * - **"Conectando" sem o tráfego do vídeo (0.4.21):** fecha [PREPARING_MAX_MS] depois de o
 *   Bluetooth desligar, em vez de esperar o tempo máximo.
 * - **CarPlay depois do tempo máximo (0.4.23):** o Bluetooth da central desligando vale como
 *   começo do CarPlay com qualquer app na tela, para o fim ser percebido. E, a pedido do
 *   usuário, se isso acontecer até [LATE_START_MS] depois de o tempo máximo tirar o ZLINK da
 *   frente, ou com uma tela do Nyx Car à vista (também "CarPlay desconectado", num fim falso),
 *   o ZLINK volta coberto por "Conectando".
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

    /** Depois de fechar uma conexão que caiu, por quanto tempo o ZLINK voltando sozinho é coberto e tirado. */
    internal const val DISMISS_GUARD_MS = 5_000L

    /** Um aviso da tela do ZLINK mais antigo que isto não entra na medição do tempo até cobrir. */
    private const val MAX_TIMING_MS = 5_000L

    /** Uma notificação do ZLINK até este tempo antes do fim entra no diário (medição). */
    private const val LEAD_MS = 30_000L

    /** Na gravação, quantos tipos de aviso do ZLINK são contados durante o CarPlay (0.4.18). */
    private const val MAX_EVENT_KINDS = 12

    /** Na gravação: um aviso do ZLINK depois deste tempo sem nenhum conta como "depois de uma pausa" (0.4.19). */
    private const val QUIET_MS = 1_000L

    /**
     * Fim pelo som (0.4.21): nos testes, o Bluetooth religou 0,6 a 1,8 s e o cabo caiu 0,8 a 1,1 s
     * depois de a música parar. Sem um dos dois neste tempo, não era o fim: a tela sai.
     */
    internal const val SOUND_CONFIRM_MS = 3_000L

    /** Fim pelo som confirmado: "CarPlay desconectado" fica pelo menos isto com a Home já por baixo. */
    private const val SOUND_HOLD_MIN_MS = 800L

    /**
     * "Conectando" sem o tráfego do vídeo (0.4.21): fecha este tempo depois de o Bluetooth desligar.
     * Nos testes, o vídeo foi percebido 0,3 a 2,2 s depois, e a imagem do CarPlay estava na tela
     * 1,4 a 2,3 s depois; no teste da 0.4.19, sem música, o tráfego não passou do limite e a tela
     * ficou 10 s por cima do CarPlay.
     */
    internal const val PREPARING_MAX_MS = 4_000L

    /**
     * Depois de o tempo máximo tirar o ZLINK da frente, por quanto tempo o CarPlay entrando traz o
     * ZLINK de volta (0.4.23, pedido do usuário). No teste da 0.4.22, o CarPlay sem fio entrou 5 a
     * 29 s depois do tempo máximo.
     */
    internal const val LATE_START_MS = 60_000L

    /** Propriedade da central que marca uma chamada (1 durante uma ligação pelo CarPlay). */
    private const val CALL_STATE = "sys.nonsystem.callstate"


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

    /** O player do Nyx Car está tocando (o som dele não é o do CarPlay); trocado nos testes. */
    internal var ownPlayerPlaying: () -> Boolean = { MusicPlayer.isPlaying }

    /** A central marca uma chamada (ligação pelo CarPlay); trocado nos testes. */
    internal var callActive: () -> Boolean = { FastProperty.get(CALL_STATE) == "1" }

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

    /** Até quando o CarPlay entrando traz o ZLINK de volta, depois do tempo máximo (0 = não). */
    private var lateUntil = 0L

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
    private var dismissUntil = 0L

    // Medição do tempo até cobrir (0.4.17; por etapas na 0.4.18), só na thread do CarPlay: o
    // primeiro aviso ainda não lido e as marcas da última leitura.
    private var pendingEvent: ScreenEvent? = null
    private var lastRead: ScreenRead? = null

    /** Na gravação: tipos de aviso do ZLINK durante o CarPlay e quantas vezes (0.4.18). */
    private val carPlayEvents = LinkedHashMap<String, Int>()

    /** Na gravação: os mesmos, só os que vieram depois de [QUIET_MS] sem avisos (0.4.19). */
    private val carPlayFirstEvents = LinkedHashMap<String, Int>()
    private var lastEventAt = 0L

    // Fim pelo som (0.4.21), só na thread do CarPlay.
    private var sound = CarPlaySound()
    private var playbackCallback: Any? = null // AudioManager.AudioPlaybackCallback (Android 8+)

    /** Quando a música parou como no fim e "CarPlay desconectado" entrou, à espera da confirmação (0 = não). */
    private var soundEndAt = 0L

    /** O Bluetooth religou com a música tocando e o fim foi desmentido: se ela parar agora, é o fim. */
    private var soundRefuted = false

    /** O player do Nyx Car estava tocando no aviso anterior do som. */
    private var ownWasPlaying = false


    /**
     * Um aviso do ZLINK (0.4.18): quando ele o mandou ([eventAt]) e quando chegou ao Nyx Car
     * ([receivedAt]), no relógio `SystemClock.uptimeMillis`. [kind]: tipo de aviso, tipo do
     * elemento e o que mudou (só a estrutura; nunca textos), para a gravação.
     */
    private class ScreenEvent(val eventAt: Long, val receivedAt: Long, val kind: String?)

    /** Uma leitura da tela do ZLINK: o aviso que a pediu, quando começou e quando terminou. */
    private class ScreenRead(val event: ScreenEvent?, val startAt: Long, val readAt: Long)

    /** O CarPlay está em uso (sessão com o Bluetooth da central desligado); lido de qualquer thread, para a medição. */
    val carPlayRunning: Boolean get() = session.active && session.bluetoothOff

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) listenToSound(appContext)
        handler.post { resumeAfterRestart(appContext) }
    }

    /**
     * O Nyx Car foi aberto do zero com o CarPlay em uso (0.4.25). No teste da 0.4.24, a central
     * fechou o Nyx Car duas vezes 10 a 20 s depois de o CarPlay entrar; a sessão ficou só na
     * memória do processo fechado, e o fim seguinte passou sem o Nyx Car voltar. O Bluetooth da
     * central desligado é o CarPlay em uso (ela só o desliga para ele): a sessão volta a valer.
     */
    private fun resumeAfterRestart(context: Context) {
        if (session.active || BluetoothStatus.read(context) != BluetoothStatus.State.OFF) return
        session.resumeCarPlay()
        screens.carPlayStarted(SystemClock.uptimeMillis())
        log("Nyx Car aberto do zero com o Bluetooth da central desligado (CarPlay em uso): o fim volta a ser vigiado")
    }

    /**
     * Fim pelo som (0.4.21): o Android avisa, na thread do CarPlay, que tipos de som estão tocando
     * (sem dizer qual app nem o conteúdo). Não precisa de permissão.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun listenToSound(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                onSound(configs.map { CarPlaySound.kindOf(it.audioAttributes.usage) })
            }
        }
        if (runCatching { audio.registerAudioPlaybackCallback(callback, handler) }.isFailure) return
        playbackCallback = callback
        handler.post {
            runCatching { audio.activePlaybackConfigurations }.getOrNull()
                ?.let { configs -> onSound(configs.map { CarPlaySound.kindOf(it.audioAttributes.usage) }) }
        }
    }

    private fun startThread(): Looper {
        val started = HandlerThread("nyx-carplay").also { it.start() }
        thread = started
        // Prioridade de tela urgente (0.4.18; era a de tela, a das animações, desde a 0.4.9): no fim
        // do CarPlay, o processador está disputado e o quadro da janela atrasava. Se o sistema
        // recusar, fica a de tela; se recusar também, a normal.
        Handler(started.looper).post {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }
                .recoverCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }
        }
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
        // CarPlay sem fio já em uso (0.4.25): no teste da 0.4.24, o iPhone apareceu no USB 0,1 s
        // depois de o vídeo do CarPlay sem fio chegar; a espera do cabo tomou o lugar, o cabo não
        // completou a conexão e o Nyx Car fechou o ZLINK com o CarPlay na tela.
        if (!badContact && session.active && session.bluetoothOff && !session.cableConnected) {
            handler.removeCallbacks(cableLinkMissing)
            log("iPhone apareceu no USB com o CarPlay sem fio em uso: a sessão continua, sem a tela de espera do cabo")
            return
        }
        log(if (badContact) "iPhone voltou ao USB logo depois de sair (mau contato): a sessão continua" else "iPhone apareceu no USB (Apple)")
        // Pelo cabo, o ZLINK abre sozinho ~3 s depois: a tela de espera do Nyx Car já entra agora.
        startWait(WaitMode.CABLE, CarPlayWaitState.CONNECTING_IPHONE)
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
                session.bluetooth(on = false)
                if (!before && session.bluetoothOff) carPlayStarted()
            }
            BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_ON ->
                if (session.bluetooth(on = true)) {
                    when {
                        carPlayOnScreen() -> {
                            session.cancelEnd()
                            log("Bluetooth da central ligou, mas a imagem do CarPlay continua na tela do ZLINK: não é o fim, a sessão continua")
                        }
                        // Nos fins de verdade, a música para 0,6 a 1,8 s antes de o Bluetooth religar (0.4.21).
                        soundStillPlaying() -> {
                            session.cancelEnd()
                            soundRefuted = true
                            log("Bluetooth da central ligou, mas a música do CarPlay continua tocando: não é o fim, a sessão continua")
                        }
                        else -> endSignal()
                    }
                }
        }
    }

    /**
     * O Bluetooth da central desligou: o CarPlay entrou. (0.4.23) Com outro app na tela, a
     * sessão vale do mesmo jeito, para o fim ser percebido quando o ZLINK for aberto; e, se o
     * tempo máximo tirou o ZLINK da frente há pouco ou uma tela do Nyx Car está à vista, ele
     * volta coberto por "Conectando" ([bringCarPlayBack]).
     */
    private fun carPlayStarted() {
        soundRefuted = false
        boostMainThread()
        screens.carPlayStarted(SystemClock.uptimeMillis())
        stopWatch() // o CarPlay já está entrando: a vigia não precisa continuar
        val afterTimeout = SystemClock.uptimeMillis() < lateUntil
        lateUntil = 0L
        val top = readTop()
        when {
            isZlink(top) -> log("Bluetooth da central desligou com o ZLINK na tela")
            bringCarPlayBack(top, afterTimeout) -> return
            else -> log(
                "Bluetooth da central desligou com ${TopActivity.packageOf(top) ?: "?"} na tela: " +
                    "o CarPlay entrou por trás e o fim dele passa a ser vigiado",
            )
        }
        // Durante "Conexão interrompida", o iPhone voltou: a conexão segue.
        if (hold == Hold.INTERRUPTED) startWait(if (session.cableConnected) WaitMode.CABLE else WaitMode.WIRELESS)
        if (waitMode != null) preparing() else if (waitView?.visible != true) releaseWindow()
    }

    /**
     * O CarPlay entrou com outro app na tela (0.4.23, pedido do usuário). O ZLINK volta, coberto
     * por "Conectando" até a imagem do CarPlay chegar, quando:
     * - o tempo máximo o tirou da frente há menos de [LATE_START_MS] ([afterTimeout]): no teste
     *   da 0.4.22, o CarPlay sem fio entrou 5 a 29 s depois, escondido atrás do Nyx Car;
     * - uma tela do Nyx Car está à vista: "Aguardando o celular", "Conexão interrompida" ou
     *   "Conectando" (o usuário esperava o CarPlay), ou "CarPlay desconectado" (fim falso: no
     *   teste de 13/11/2026, a central religou o Bluetooth e o desligou de novo 1,4 s depois, com
     *   o CarPlay continuando atrás do Nyx Car).
     *
     * Primeiro a janela, depois o ZLINK por baixo dela, como na volta da Home (0.4.17). Devolve
     * `true` quando o ZLINK foi chamado.
     */
    private fun bringCarPlayBack(top: String?, afterTimeout: Boolean): Boolean {
        val context = app ?: return false
        val view = waitView ?: return false
        val why = when {
            afterTimeout -> "depois do tempo máximo"
            !view.visible -> return false
            hold == Hold.DISCONNECTED -> "de novo logo depois do fim"
            else -> "com o Nyx Car esperando o celular"
        }
        if (!view.canShow()) return false
        val intent = AppLauncher(context).resolve(CarApps.CARPLAY) ?: return false
        val mode = if (session.cableConnected) WaitMode.CABLE else WaitMode.WIRELESS
        startWait(
            mode,
            CarPlayWaitState.PREPARING,
            "o CarPlay entrou $why, com ${TopActivity.packageOf(top) ?: "?"} na tela: " +
                "o ZLINK volta para a frente, coberto por \"Conectando\" (${mode.label})",
        )
        view.afterNextFrame {
            if (runCatching { context.startActivity(intent) }.isFailure) log("não deu para abrir o ZLINK")
        }
        preparing()
        return true
    }

    /**
     * Com o serviço de acessibilidade ligado, o ZLINK está na tela mostrando a imagem do CarPlay
     * (0.4.17). Nos fins de verdade, a tela do ZLINK muda 1,0 a 1,4 s antes de o Bluetooth religar.
     */
    private fun carPlayOnScreen(): Boolean =
        isZlink(readTop()) && runCatching { readScreenNow() }.getOrNull()?.projection == true

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
            log("CarPlay tocado sem celular conectado: \"Aguardando o celular\" no lugar da tela do ZLINK")
        }
        return true
    }

    /** A Home saiu da tela: se for o ZLINK abrindo sozinho, a tela de espera entra (vigia curta). */
    fun onLauncherPaused() = onCarPlayThread { watchZlink(WATCH_AFTER_PAUSE_MS, fromHome = true) }

    /**
     * A Home voltou e já está desenhada (botão Home, Voltar ou a volta automática; a MainActivity
     * chama dois quadros depois do `onResume`, 0.4.15): a tela de espera sai sem o ZLINK aparecer
     * no meio. "Aguardando o celular" e as mensagens de fim ficam até o tempo delas.
     */
    fun onLauncherShown() = onCarPlayThread {
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
        if (SystemClock.uptimeMillis() < dismissUntil && !session.bluetoothOff) {
            // A conexão caiu, o ZLINK foi fechado e voltou sozinho logo depois: sai de novo.
            waitView?.takeIf { it.canShow() }?.show(CarPlayWaitState.INTERRUPTED, ::onPhoneWaitTap)
            returnHome()
            log("o ZLINK voltou sozinho logo depois de fechado: coberto e o Nyx Car de novo na frente")
            return
        }
        if (session.active && ready) return
        if (BluetoothStatus.read(context) == BluetoothStatus.State.OFF) return
        startWait(if (session.cableConnected) WaitMode.CABLE else WaitMode.WIRELESS)
    }

    // ---------- Tela de espera (B1) ----------

    private fun startWait(
        mode: WaitMode,
        state: CarPlayWaitState = mode.state,
        event: String = "tela de espera do Nyx Car aberta (${mode.label})",
    ) {
        val view = waitView ?: return
        ready = false
        lateUntil = 0L
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
        dismissUntil = 0L
        view.show(state, ::onConnectingTap, ::onConnectingLongPress)
        handler.postDelayed(timeout, mode.timeoutMs)
        log(event)
    }

    /** O CarPlay está entrando (Bluetooth da central desligou): começa a procurar o vídeo. */
    private fun preparing() {
        val mode = waitMode ?: return
        waitView?.show(CarPlayWaitState.PREPARING, ::onConnectingTap, ::onConnectingLongPress)
        detector = VideoStartDetector(mode.thresholdBytesPerSecond)
        handler.removeCallbacks(sample)
        handler.post(sample)
        handler.removeCallbacks(preparingOver)
        handler.postDelayed(preparingOver, PREPARING_MAX_MS)
    }

    /**
     * O tráfego do vídeo não passou do limite (0.4.21): com o CarPlay parado na tela inicial e sem
     * música, ele pode nunca passar. O Bluetooth desligado já diz que o CarPlay entrou, e a imagem
     * dele já está na tela: "Conectando" fecha. Com o serviço de acessibilidade mostrando o ZLINK
     * ainda em "conectando", espera mais um pouco.
     */
    private val preparingOver: Runnable = Runnable {
        if (detector == null || waitMode == null) return@Runnable
        if (lastZlinkScreen?.connecting == true && screenService != null) {
            handler.postDelayed(preparingOver, SCREEN_CONFIRM_MS * 5)
            return@Runnable
        }
        detector = null
        ready = true
        closeWait()
        log("o tráfego do vídeo não passou do limite ${seconds(PREPARING_MAX_MS)} depois de o Bluetooth desligar: tela de espera fechada (o CarPlay já está na tela)")
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

    private fun videoStarted(byScreen: Boolean = false) {
        detector = null
        ready = true
        val after = seconds(SystemClock.uptimeMillis() - waitStartedAt)
        log(
            if (byScreen) {
                "imagem do CarPlay na tela do ZLINK $after depois de a tela de espera abrir, sem o tráfego do vídeo: ela fecha em 2 s"
            } else {
                "vídeo do CarPlay chegou $after depois de a tela de espera abrir: ela fecha em 2 s"
            },
        )
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(preparingOver)
        handler.postDelayed(closeNow, READY_HIDE_DELAY_MS)
    }

    /**
     * Tempo máximo sem o vídeo. Se o CarPlay nem começou (a central não desligou o Bluetooth), o
     * ZLINK está parado na espera dele: a tela e o ZLINK saem (0.4.9), e o CarPlay que entrar até
     * [LATE_START_MS] depois traz o ZLINK de volta (0.4.23). Se começou, só o vídeo não foi
     * percebido: a tela sai e mostra o CarPlay.
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
            guardAfterDismiss()
            lateUntil = SystemClock.uptimeMillis() + LATE_START_MS
            log("tempo máximo (${seconds(elapsed)}) e o CarPlay não começou: tela de espera e ZLINK fechados, Nyx Car na frente")
        } else {
            closeWait()
            log("tempo máximo (${seconds(elapsed)}) sem o vídeo do CarPlay: tela de espera fechada")
        }
    }

    /** Pelo cabo, o iPhone apareceu no USB mas a central não virou aparelho dele: a conexão caiu. */
    private val cableLinkMissing = Runnable {
        // Com o Bluetooth da central desligado, o CarPlay já entrou (sem fio): não é queda (0.4.25).
        if (waitMode == WaitMode.CABLE && !session.cableConnected && !session.bluetoothOff) {
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
        log(if (opened) "ZLINK aberto mesmo sem celular (toque longo)" else "não deu para abrir o ZLINK")
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
        val bySound = soundEndAt
        soundRefuted = false
        clearEventCounts()
        logLeads(signal.label, now)
        screens.sessionEnded(now)
        saveScreens()
        val warnedAt = screenEndAt
        screenEndAt = 0L
        if (bySound != 0L && hold == Hold.DISCONNECTED) {
            soundConfirmed(signal.label, now, bySound)
            return
        }
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

    private fun showDisconnected(onDrawn: (FrameMarks) -> Unit = {}) {
        hold = Hold.DISCONNECTED
        waitingPhone = false
        waitView?.show(CarPlayWaitState.DISCONNECTED, ::onHoldTap)
        coverThenHome(onDrawn)
        handler.postDelayed(holdOver, DISCONNECTED_SHOW_MS)
    }

    // ---------- Fim pelo som (0.4.21) ----------

    /** Tipos de som tocando agora (aviso do Android; nos testes, chamada direto). */
    internal fun onSound(kinds: List<CarPlaySound.Kind>) {
        val now = SystemClock.uptimeMillis()
        val ownNow = runCatching { ownPlayerPlaying() }.getOrDefault(false)
        val own = ownWasPlaying || ownNow
        ownWasPlaying = ownNow
        val change = sound.update(kinds, now, runCatching { callActive() }.getOrDefault(false))
        if (soundEndAt != 0L && sound.mediaPlaying) soundNotEnded("a música voltou a tocar")
        if (!session.active) return
        when (change) {
            CarPlaySound.Change.NONE -> Unit
            CarPlaySound.Change.AFTER_CALL -> log("a música do CarPlay parou no fim de uma ligação ou da Siri: não é o fim")
            CarPlaySound.Change.TOO_SHORT -> log("a música do CarPlay parou logo depois de começar: não conta como fim")
            CarPlaySound.Change.ENDED ->
                if (own) log("o som parou, mas o player do Nyx Car estava tocando: não conta como fim do CarPlay") else soundEnded(now)
        }
    }

    private fun soundStillPlaying(): Boolean = sound.mediaPlaying && !runCatching { ownPlayerPlaying() }.getOrDefault(false)

    /**
     * A música do CarPlay parou como no fim: "CarPlay desconectado" entra na hora, por cima do
     * ZLINK, mas a Home só volta quando o Bluetooth ou o cabo confirmarem ([endSignal]).
     */
    private fun soundEnded(now: Long) {
        if (!session.bluetoothOff) {
            // O Bluetooth já tinha religado com a música tocando (fim desmentido): agora ela parou.
            if (soundRefuted && session.soundStopped()) {
                soundRefuted = false
                endSignal()
            }
            return
        }
        if (hold != null || screenEndAt != 0L || soundEndAt != 0L) return
        val view = waitView
        if (view == null || !view.canShow() || view.visible) return
        if (!isZlink(readTop())) {
            log("a música do CarPlay parou como no fim, com outro app na tela: nada muda até o Bluetooth ou o cabo")
            return
        }
        soundEndAt = now
        hold = Hold.DISCONNECTED
        waitingPhone = false
        view.show(CarPlayWaitState.DISCONNECTED, ::onHoldTap)
        handler.postDelayed(soundCheck, SOUND_CONFIRM_MS)
        view.afterNextFrame { marks ->
            logSoundTiming(now, marks, SystemClock.uptimeMillis())
            log("a música do CarPlay parou como no fim: \"CarPlay desconectado\" na tela; o Nyx Car volta quando o Bluetooth ou o cabo confirmarem")
        }
    }

    /** Fim pelo som confirmado pelo Bluetooth ou pelo cabo: a Home volta por baixo da mensagem. */
    private fun soundConfirmed(label: String, now: Long, soundAt: Long) {
        stopTimers()
        waitMode = null
        detector = null
        ready = false
        coverThenHome()
        handler.postDelayed(holdOver, maxOf(SOUND_HOLD_MIN_MS, DISCONNECTED_SHOW_MS - (now - soundAt)))
        log("terminou ($label) ${seconds(now - soundAt)} depois de a música do CarPlay parar: o Nyx Car voltou para a frente")
    }

    private val soundCheck = Runnable {
        soundNotEnded("nem o Bluetooth nem o cabo confirmaram o fim em ${seconds(SOUND_CONFIRM_MS)}")
    }

    /** Não era o fim: a tela do Nyx Car sai e o CarPlay volta a ficar à vista. */
    private fun soundNotEnded(reason: String) {
        if (soundEndAt == 0L || hold != Hold.DISCONNECTED) return
        handler.removeCallbacks(soundCheck)
        soundEndAt = 0L
        hold = null
        releaseWindow()
        log("a música do CarPlay parou, mas $reason: a tela do Nyx Car saiu e o CarPlay continua")
    }

    /** Medição do fim pelo som, só na gravação: do aviso do som até a janela desenhada. */
    private fun logSoundTiming(soundAt: Long, marks: FrameMarks, drawnAt: Long) {
        if (!CarTestRecorder.running) return
        fun at(time: Long) = if (time >= soundAt) "${time - soundAt}" else "?"
        val steps = listOf("janela no lugar ${at(marks.layoutAt)}", "quadro ${at(marks.frameAt)}", "desenhada ${at(drawnAt)}")
            .let { if (marks.immediate) it else listOf(it[1], it[0], it[2]) }
        val placed = if (marks.immediate) " (na hora, sem esperar o quadro)" else ""
        CarTestLog.add("Nyx Car — CarPlay: medição do fim pelo som, em ms depois do aviso do som: ${steps.joinToString(" · ")}$placed")
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

    /**
     * A tela do ZLINK mudou (aviso do serviço, na thread principal): lê a estrutura na thread do
     * CarPlay. Para a medição: [eventAt], quando o ZLINK mandou o aviso, e [receivedAt], quando
     * ele chegou (relógio do sistema); [kind], o tipo do aviso (só na gravação).
     */
    fun onZlinkScreenChanged(windowId: Int, eventAt: Long = 0L, receivedAt: Long = 0L, kind: String? = null) {
        screenWindow = windowId
        onCarPlayThread {
            countEvent(kind, eventAt)
            if (pendingEvent == null) pendingEvent = ScreenEvent(eventAt, receivedAt, kind)
            if (screenReadPending) return@onCarPlayThread
            screenReadPending = true
            handler.post(readScreen)
        }
    }

    /**
     * Na gravação, com o CarPlay na tela: conta o tipo de aviso, para comparar com o do fim
     * (0.4.18). E à parte os que vêm depois de uma pausa (0.4.19): durante o CarPlay, o ZLINK
     * avisa a cada ~6 s, começando por `ImageView`; o do fim é um `ViewGroup`.
     */
    private fun countEvent(kind: String?, eventAt: Long) {
        val afterQuiet = lastEventAt == 0L || eventAt - lastEventAt >= QUIET_MS
        if (eventAt > 0L) lastEventAt = eventAt
        if (kind == null || !session.active || !session.bluetoothOff) return
        add(carPlayEvents, kind)
        if (afterQuiet) add(carPlayFirstEvents, kind)
    }

    private fun add(counts: MutableMap<String, Int>, kind: String) {
        if (kind in counts || counts.size < MAX_EVENT_KINDS) counts[kind] = (counts[kind] ?: 0) + 1
    }

    private val readScreen = Runnable {
        screenReadPending = false
        val event = pendingEvent
        pendingEvent = null
        val startAt = SystemClock.uptimeMillis()
        val screen = runCatching { readScreenNow() }.getOrNull() ?: return@Runnable
        lastRead = ScreenRead(event, startAt, SystemClock.uptimeMillis())
        onZlinkScreen(screen)
    }

    /** A imagem do CarPlay sumiu numa tela nunca vista: se continuar sem ela, é o fim. */
    private val confirmScreenEnd = Runnable {
        val again = runCatching { readScreenNow() }.getOrNull() ?: return@Runnable
        if (again.projection) {
            onZlinkScreen(again) // a imagem voltou: não era o fim
            return@Runnable
        }
        lastZlinkScreen = again
        if (screens.confirmEnd(again.signature)) screenEnded(::saveScreens)
    }

    /** Uma tela do ZLINK lida agora (nos testes, chamada direto). */
    internal fun onZlinkScreen(screen: ZlinkScreen) {
        val context = app ?: return
        val now = SystemClock.uptimeMillis()
        resumeIfCarPlayOnScreen(context, screen, now)
        val mode = when {
            session.active && session.bluetoothOff -> ZlinkScreens.Mode.CARPLAY
            // Bluetooth desligado sem a sessão registrada: pode ser o CarPlay, não aprende nada.
            BluetoothStatus.read(context) == BluetoothStatus.State.OFF -> ZlinkScreens.Mode.UNSURE
            else -> ZlinkScreens.Mode.NO_CARPLAY
        }
        val result = screens.update(screen.signature, now, mode, screen.projection)
        lastZlinkScreen = screen
        if (screen.projection && mode == ZlinkScreens.Mode.CARPLAY) {
            boostMainThread()
            // Esperando o vídeo (0.4.20): com o CarPlay parado na tela inicial dele, o tráfego pode
            // não passar do limite. No teste de 13/11/2026, a tela "Conectando" ficou 10 s por
            // cima do CarPlay, até o tempo máximo. A imagem do CarPlay sem "conectando" na tela do
            // ZLINK também vale: ela aparece 1,5 a 2 s depois de o Bluetooth desligar.
            if (detector != null && !screen.connecting) videoStarted(byScreen = true)
        }
        if (result == ZlinkScreens.Result.END) {
            handler.removeCallbacks(confirmScreenEnd)
            // Primeiro a janela (0.4.18): guardar e registrar a tela só depois do quadro dela.
            screenEnded {
                saveScreens()
                logScreen(screen)
            }
            return
        }
        saveScreens()
        logScreen(screen)
        when (result) {
            ZlinkScreens.Result.END -> Unit
            ZlinkScreens.Result.MAYBE_END -> {
                handler.removeCallbacks(confirmScreenEnd)
                handler.postDelayed(confirmScreenEnd, SCREEN_CONFIRM_MS)
            }
            ZlinkScreens.Result.NONE -> if (screen.projection) handler.removeCallbacks(confirmScreenEnd)
        }
    }

    /**
     * A imagem do CarPlay está na tela do ZLINK, com o Bluetooth da central desligado, mas a
     * sessão não está registrada (0.4.17). No teste de 13/11/2026, depois de um fim falso pelo
     * Bluetooth, o ZLINK voltou sozinho com o CarPlay e o fim de verdade passou sem o Nyx Car
     * cobrir a espera dele (7 s na tela). Com uma tela de espera ou mensagem do Nyx Car em
     * andamento, quem cuida é o caminho normal (o aviso do Bluetooth desligando).
     */
    private fun resumeIfCarPlayOnScreen(context: Context, screen: ZlinkScreen, now: Long) {
        if (!screen.projection || waitMode != null || hold != null) return
        if (session.active && session.bluetoothOff) return
        if (BluetoothStatus.read(context) != BluetoothStatus.State.OFF) return
        session.resumeCarPlay()
        screens.carPlayStarted(now)
        stopWatch()
        if (waitView?.visible != true) releaseWindow()
        log("imagem do CarPlay na tela do ZLINK sem a sessão registrada: o fim volta a ser vigiado")
    }

    /**
     * Durante o CarPlay, o ZLINK voltou para uma tela de espera: o CarPlay terminou, antes de a
     * central religar o Bluetooth. "CarPlay desconectado" entra na hora e o Nyx Car volta. A
     * sessão só é dada por encerrada no aviso do Bluetooth ou do cabo, que vem depois.
     *
     * Antes do quadro da janela, só o indispensável (0.4.18): [afterCover] (guardar e registrar a
     * tela do ZLINK) e os registros do diário vêm depois dele.
     */
    private fun screenEnded(afterCover: () -> Unit = {}) {
        if (!isZlink(readTop()) || hold != null) {
            afterCover()
            return
        }
        val now = SystemClock.uptimeMillis()
        screenEndAt = now
        stopTimers()
        waitMode = null
        detector = null
        ready = false
        val read = lastRead
        val view = waitView
        val records = { marks: FrameMarks ->
            logCoverTiming(read, now, marks, SystemClock.uptimeMillis())
            afterCover()
            logLeads("tela do ZLINK", now)
            log("a tela do ZLINK voltou para a espera (o CarPlay terminou): \"CarPlay desconectado\" na tela e o Nyx Car voltou para a frente")
            logCarPlayEvents(read?.event?.kind)
        }
        if (view != null && view.canShow()) {
            showDisconnected(records)
        } else {
            bringHome()
            records(FrameMarks())
        }
    }

    /**
     * Medição (0.4.17; por etapas na 0.4.18), só na gravação: do aviso do ZLINK (a tela dele
     * trocou) até a janela do Nyx Car desenhada, que é o tempo em que a espera do ZLINK aparece.
     * Cada etapa em ms depois do aviso: o aviso chegou ao Nyx Car, a leitura da tela começou e
     * terminou, a janela foi pedida, a thread da janela começou o quadro, o Android pôs a janela
     * no lugar e o quadro terminou. Com a prioridade da thread do CarPlay.
     */
    private fun logCoverTiming(read: ScreenRead?, requestedAt: Long, marks: FrameMarks, drawnAt: Long) {
        if (!CarTestRecorder.running || read == null) return
        val event = read.event ?: return
        val eventAt = event.eventAt
        if (eventAt <= 0L || read.readAt < eventAt || drawnAt - eventAt > MAX_TIMING_MS) return
        fun at(time: Long) = if (time >= eventAt) "${time - eventAt}" else "?"
        val steps = listOf(
            "aviso recebido ${at(event.receivedAt)}",
            "leitura ${at(read.startAt)} a ${at(read.readAt)}",
            "janela pedida ${at(requestedAt)}",
            "janela no lugar ${at(marks.layoutAt)}",
            "quadro ${at(marks.frameAt)}",
            "desenhada ${at(drawnAt)}",
        ).let { if (marks.immediate) it else listOf(it[0], it[1], it[2], it[4], it[3], it[5]) }
        val priority = runCatching { Process.getThreadPriority(Process.myTid()) }.getOrNull()
        val main = runCatching { Process.getThreadPriority(Process.myPid()) }.getOrNull()
        val placed = if (marks.immediate) " (na hora, sem esperar o quadro)" else ""
        CarTestLog.add(
            "Nyx Car — CarPlay: medição do fim, em ms depois do aviso do ZLINK: ${steps.joinToString(" · ")}$placed" +
                " (prioridade: thread do CarPlay ${priority ?: "?"}, principal ${main ?: "?"})",
        )
    }

    /**
     * Thread principal com prioridade de tela urgente durante o CarPlay (0.4.19). É nela que o
     * Android entrega os avisos do serviço de acessibilidade: no teste da 0.4.18, o aviso do fim
     * chegou 277 ms atrasado uma vez (nas outras, 2 a 9 ms), com o processador disputado. O
     * Android põe a prioridade dela de volta no normal quando o Nyx Car sai da frente, por isso
     * ela é conferida a cada tela do CarPlay lida. Com o Nyx Car na frente, a do Android (maior)
     * fica.
     *
     * (0.4.22) Só com o serviço de acessibilidade ligado, o único motivo do aumento. Sem ele, a
     * thread principal não tem nada do CarPlay para fazer, e o que ela fizer não pode disputar o
     * processador com o ZLINK: no teste da 0.4.21, a gravação a manteve ocupada por ~11 s logo
     * depois de o Bluetooth desligar, quando o CarPlay está começando.
     */
    private fun boostMainThread() {
        if (screenService == null) return
        runCatching {
            val main = Process.myPid() // a thread principal tem o número do processo
            if (Process.getThreadPriority(main) > Process.THREAD_PRIORITY_URGENT_DISPLAY) {
                Process.setThreadPriority(main, Process.THREAD_PRIORITY_URGENT_DISPLAY)
            }
        }
    }

    /**
     * Na gravação (0.4.18): os tipos de aviso do ZLINK durante este CarPlay e o que trouxe o fim.
     * Se o do fim nunca aparecer com o CarPlay na tela, ele sozinho pode cobrir a espera, sem
     * esperar a leitura da tela.
     */
    private fun logCarPlayEvents(endKind: String?) {
        if (CarTestRecorder.running && carPlayEvents.isNotEmpty()) {
            fun list(counts: Map<String, Int>) = counts.entries.joinToString(", ") { "${it.key} ×${it.value}" }.ifEmpty { "nenhum" }
            CarTestLog.add(
                "Nyx Car — CarPlay: avisos do ZLINK durante o CarPlay (com o do fim): ${list(carPlayEvents)}; " +
                    "depois de ${seconds(QUIET_MS)} sem avisos: ${list(carPlayFirstEvents)}; o do fim: ${endKind ?: "?"}",
            )
        }
        clearEventCounts()
    }

    private fun clearEventCounts() {
        carPlayEvents.clear()
        carPlayFirstEvents.clear()
    }

    /**
     * Medição (0.4.13): se uma notificação do ZLINK mudou pouco antes deste fim, registra quanto
     * antes. A do som (0.4.14) saiu na 0.4.18: ele para só 8 a 17 ms antes do aviso da tela do
     * ZLINK e também some no fim de uma ligação ou da Siri, com o CarPlay continuando.
     */
    private fun logLeads(label: String, now: Long) {
        val changedAt = ZlinkNotifications.lastChangeAt
        if (changedAt != 0L && now - changedAt <= LEAD_MS) {
            log("uma notificação do ZLINK mudou ${seconds(now - changedAt)} antes deste fim ($label)")
        }
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
        val event = lastRead?.event?.kind?.let { "; aviso: $it" }.orEmpty()
        CarTestLog.add(
            "ZLINK (acessibilidade): tela ${screen.signature} ($kind) — ${screen.elements} elementos, " +
                "${screen.texts} com texto, ${screen.touchable} tocáveis$marks$event; nomes: $ids",
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
        log("conexão interrompida antes de o CarPlay aparecer ($reason): aguardando o celular por ${seconds(INTERRUPTED_HOLD_MS)}")
    }

    /** Fim do tempo da mensagem: se o ZLINK ainda estiver na frente, ele sai e o Nyx Car volta. */
    private val holdOver = Runnable {
        val kind = hold ?: return@Runnable
        hold = null
        val zlink = isZlink(readTop())
        if (zlink) returnHome() else closeWait()
        if (kind == Hold.INTERRUPTED) {
            guardAfterDismiss()
            log(
                if (zlink) {
                    "o celular não voltou: tela de espera e ZLINK fechados, Nyx Car na frente"
                } else {
                    "o celular não voltou: tela de espera fechada"
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
        coverThenHome()
        handler.postDelayed(closeNow, RETURN_HIDE_MS)
    }

    /**
     * Chama a Home de volta (0.4.17): com a janela do Nyx Car à vista, só depois do próximo
     * quadro dela. Pedir a Home ao Android leva dezenas de milissegundos e, na 0.4.16, a janela
     * (desenhada nesta mesma thread) esperava esse pedido, com a espera do ZLINK à mostra.
     */
    private fun coverThenHome(onDrawn: (FrameMarks) -> Unit = {}) {
        val view = waitView
        if (view == null || !view.visible) {
            onDrawn(FrameMarks())
            bringHome()
            return
        }
        view.afterNextFrame { marks ->
            onDrawn(marks)
            bringHome()
        }
    }

    private fun bringHome() {
        val context = app ?: return
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            // Sem animação (0.4.15): a Home aparece inteira de uma vez, por baixo da tela do Nyx Car.
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        } catch (e: RuntimeException) {
            log("não deu para voltar ao Nyx Car (${e.javaClass.simpleName})")
        }
    }

    /**
     * Depois de fechar uma conexão que caiu (0.4.15): por [DISMISS_GUARD_MS], se o ZLINK voltar
     * sozinho para a frente, ele é coberto e sai de novo ([zlinkAppeared]). O iPhone voltando
     * pelo USB ou o CarPlay entrando cancelam isso.
     */
    private fun guardAfterDismiss() {
        dismissUntil = SystemClock.uptimeMillis() + DISMISS_GUARD_MS
        watchZlink(DISMISS_GUARD_MS)
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
        // Já desenhada com a mensagem mais provável: no CarPlay, o fim; antes dele, a conexão.
        val next = if (session.active) CarPlayWaitState.DISCONNECTED else CarPlayWaitState.CONNECTING_WIRELESS
        if (reserve && view.canShow()) view.park(next) else view.hide()
    }

    private fun stopTimers() {
        handler.removeCallbacks(confirmScreenEnd)
        handler.removeCallbacks(sample)
        handler.removeCallbacks(preparingOver)
        handler.removeCallbacks(soundCheck)
        soundEndAt = 0L
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (playbackCallback as? AudioManager.AudioPlaybackCallback)?.let { callback ->
                app?.let { runCatching { it.getSystemService(AudioManager::class.java)?.unregisterAudioPlaybackCallback(callback) } }
            }
        }
        playbackCallback = null
        sound = CarPlaySound()
        soundEndAt = 0L
        soundRefuted = false
        ownWasPlaying = false
        lateUntil = 0L
        ownPlayerPlaying = { MusicPlayer.isPlaying }
        callActive = { FastProperty.get(CALL_STATE) == "1" }
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
        dismissUntil = 0L
        pendingEvent = null
        lastRead = null
        clearEventCounts()
        lastEventAt = 0L
    }
}
