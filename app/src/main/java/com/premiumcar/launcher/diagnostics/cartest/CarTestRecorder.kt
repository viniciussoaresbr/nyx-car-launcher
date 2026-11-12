package com.premiumcar.launcher.diagnostics.cartest

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.premiumcar.launcher.diagnostics.SystemProperties
import com.premiumcar.launcher.diagnostics.UsbDevices

/**
 * Gravação de até 3 minutos para os testes no carro. Registra, com horário:
 * - eventos USB (aparelho/acessório conectado ou removido, estado da porta);
 * - propriedades do sistema que mudarem (ex.: `sys.top.activity`, `sys.usb.config`,
 *   `rw.cp.link.state`), lidas com `getprop` uma vez por segundo;
 * - se há música tocando e as sessões de mídia (se o acesso foi concedido);
 * - avisos de música que players costumam emitir (faixa/estado);
 * - (0.4.0) conexões Bluetooth por perfil, sem nome nem endereço do aparelho; tipos de áudio
 *   tocando (mídia, navegação, chamada…); modo de áudio (normal, em chamada…). Em busca de um
 *   sinal confiável de "CarPlay pronto" (ARCHITECTURE.md, D10).
 * - (0.4.5) tráfego de rede por interface ([NetTraffic]): só faixas de bytes por segundo. E as
 *   decisões da volta automática ao Nyx Car (CarPlayReturn) entram na mesma linha do tempo.
 *
 * Somente leitura e por tempo limitado: começa e termina por ação do usuário (ou ao fim
 * dos 3 minutos). Não envia nada ao iPhone, ao ZLINK ou ao player.
 */
object CarTestRecorder {

    const val DURATION_MS = 180_000L
    private const val POLL_MS = 1_000L

    @Volatile
    var running = false
        private set

    @Volatile
    private var startedAt = 0L

    private var context: Context? = null
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var receiver: BroadcastReceiver? = null

    private var lastProps: Map<String, String>? = null
    private var lastMusic: Boolean? = null
    private var lastSessions: String? = null
    private var lastUsb: List<String>? = null
    private var lastMode: Int? = null
    private var lastPlayback: String? = null
    private var lastNet: Map<String, NetTraffic.Bytes>? = null
    private var lastNetAt = 0L
    private val netLevels = mutableMapOf<String, Int>()
    private var playbackCallback: Any? = null // AudioManager.AudioPlaybackCallback (Android 8+)

    val elapsedMillis: Long get() = if (running) SystemClock.uptimeMillis() - startedAt else 0L

    @Synchronized
    fun start(from: Context) {
        if (running) return
        val app = from.applicationContext
        context = app
        CarTestLog.attach(app)
        lastProps = null
        lastMusic = null
        lastSessions = null
        lastUsb = null
        lastMode = null
        lastPlayback = null
        lastNet = null
        netLevels.clear()
        startedAt = SystemClock.uptimeMillis()
        running = true
        CarTestLog.markOrigin(startedAt)
        CarTestLog.add("${CarTestLog.STARTED} (até ${DURATION_MS / 1000} s)")

        val looperThread = HandlerThread("pc-car-test", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        val handler = Handler(looperThread.looper)
        thread = looperThread
        worker = handler

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                CarTestLog.add(Broadcasts.describe(intent))
            }
        }
        val filter = IntentFilter().apply { Broadcasts.ACTIONS.forEach(::addAction) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(broadcastReceiver, filter, null, handler, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(broadcastReceiver, filter, null, handler)
        }
        receiver = broadcastReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val audio = app.getSystemService(AudioManager::class.java) ?: error("sem serviço de áudio")
                val callback = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = logPlayback(configs)
                }
                audio.registerAudioPlaybackCallback(callback, handler)
                playbackCallback = callback
                handler.post { logPlayback(audio.activePlaybackConfigurations) }
            }.onFailure { CarTestLog.add("Tipos de áudio tocando: indisponível (${it.javaClass.simpleName})") }
        }
        handler.post(poll)
        handler.postDelayed({ stop("tempo esgotado") }, DURATION_MS)
    }

    @Synchronized
    fun stop(reason: String) {
        if (!running) return
        running = false
        receiver?.let { runCatching { context?.unregisterReceiver(it) } }
        receiver = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (playbackCallback as? AudioManager.AudioPlaybackCallback)?.let { callback ->
                runCatching { context?.getSystemService(AudioManager::class.java)?.unregisterAudioPlaybackCallback(callback) }
            }
        }
        playbackCallback = null
        worker?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        worker = null
        // Os últimos valores lidos só são zerados no próximo início: uma leitura que já estava
        // em andamento ao parar não deve registrar tudo de novo como "Início".
        CarTestLog.add("${CarTestLog.ENDED} ($reason)")
    }

    private val poll = object : Runnable {
        override fun run() {
            val app = context
            if (!running || app == null) return
            runCatching { pollProperties() }
            runCatching { pollMusic(app) }
            runCatching { pollSessions(app) }
            runCatching { pollUsb(app) }
            runCatching { pollNetwork() }
            if (!running) return
            CarTestLog.changed() // atualiza o contador de tempo na tela
            worker?.postDelayed(this, POLL_MS)
        }
    }

    private fun pollProperties() {
        val current = SystemProperties.read().visible
        val previous = lastProps
        lastProps = current
        if (previous == null) {
            WATCHED.forEach { key -> log("Início: $key = ${current[key]?.ifEmpty { "(vazio)" } ?: "(não existe)"}") }
            return
        }
        PropertyDiff.diff(previous, current).forEach { log("Propriedade $it") }
    }

    private fun pollMusic(app: Context) {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val active = audio.isMusicActive
        if (active != lastMusic) log("Música tocando: ${if (active) "SIM" else "não"}")
        lastMusic = active
        val mode = audio.mode
        if (mode != lastMode) log("Modo de áudio: ${AudioUsage.mode(mode)}")
        lastMode = mode
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun logPlayback(configs: List<AudioPlaybackConfiguration>) {
        val summary = AudioUsage.summary(configs.map { it.audioAttributes.usage to it.audioAttributes.contentType })
        if (summary != lastPlayback) log("Tipos de áudio tocando: $summary")
        lastPlayback = summary
    }

    private fun pollSessions(app: Context) {
        val summary = MediaSessionAccess.summary(app) ?: "sem acesso (opcional: ver cartão de acesso)"
        if (summary != lastSessions) log("Sessões de mídia: $summary")
        lastSessions = summary
    }

    private fun pollUsb(app: Context) {
        val devices = UsbDevices.read(app)?.map(UsbDevices::describe) ?: listOf("serviço USB indisponível")
        if (devices != lastUsb) {
            log("Lista USB do Android: ${if (devices.isEmpty()) "vazia" else devices.joinToString(" | ")}")
        }
        lastUsb = devices
    }

    private fun pollNetwork() {
        val now = SystemClock.uptimeMillis()
        val current = NetTraffic.read()
        val previous = lastNet
        val seconds = (now - lastNetAt) / 1000.0
        lastNet = current
        lastNetAt = now
        if (previous == null) {
            val names = current.keys.joinToString(", ").ifEmpty { "nenhuma legível" }
            val source = if (current.keys == setOf(NetTraffic.TOTAL)) "só o total do Android" else "por interface"
            log("Início: interfaces de rede ($source): $names")
            return
        }
        NetTraffic.changes(previous, current, seconds, netLevels).forEach(::log)
    }

    /** Registra só se a gravação ainda estiver ativa (a leitura em andamento pode terminar depois do "parar"). */
    private fun log(text: String) {
        if (running) CarTestLog.add(text)
    }

    /** Propriedades mostradas logo no início, mesmo sem mudar. */
    private val WATCHED = listOf("sys.top.activity", "sys.usb.state", "sys.usb.config", "sys.usb.configfs", "rw.cp.link.state")
}

/** Avisos do sistema e de players escutados durante a gravação. */
internal object Broadcasts {

    /** Ação (oculta no SDK) do estado da porta USB quando a central atua como aparelho. */
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    private val MUSIC_ACTIONS = listOf(
        "com.android.music.metachanged", "com.android.music.playstatechanged",
        "com.android.music.playbackcomplete", "com.android.music.queuechanged",
        "com.htc.music.metachanged", "com.htc.music.playstatechanged",
        "com.sec.android.app.music.metachanged", "com.sec.android.app.music.playstatechanged",
        "com.miui.player.metachanged", "com.andrew.apollo.metachanged",
        "com.spotify.music.metadatachanged", "com.spotify.music.playbackstatechanged",
        "fm.last.android.metachanged",
    )

    /**
     * Bluetooth: perfil exibido para cada aviso. Os ocultos no SDK são os do lado "carro"
     * (receber música e ligações do celular). O aparelho (nome e endereço) nunca é lido.
     */
    private val BLUETOOTH_PROFILES: Map<String, String> = mapOf(
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED to "A2DP (enviar áudio)",
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED to "viva-voz (lado celular)",
        "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED" to "A2DP sink (música do celular)",
        "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED" to "AVRCP (controles do celular)",
        "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED" to "HFP (ligações do celular)",
    )
    private val BLUETOOTH_PLAYING: Map<String, String> = mapOf(
        BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED to "A2DP",
        "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED" to "A2DP sink",
    )
    private val BLUETOOTH_AUDIO: Map<String, String> = mapOf(
        BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED to "viva-voz",
        "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED" to "HFP",
    )

    val ACTIONS: List<String> = listOf(
        UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED,
        UsbManager.ACTION_USB_ACCESSORY_ATTACHED, UsbManager.ACTION_USB_ACCESSORY_DETACHED,
        ACTION_USB_STATE,
        AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION,
        AudioManager.ACTION_AUDIO_BECOMING_NOISY,
        BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED,
    ) + BLUETOOTH_PROFILES.keys + BLUETOOTH_PLAYING.keys + BLUETOOTH_AUDIO.keys + MUSIC_ACTIONS

    @Suppress("DEPRECATION") // getParcelableExtra(String): a forma tipada só existe no Android 13+.
    fun describe(intent: Intent): String = runCatching {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val verb = if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) "conectado" else "removido"
                "USB: aparelho $verb — ${device?.let { UsbDevices.describe(UsbDevices.from(it)) } ?: "sem detalhes"}"
            }
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED, UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                val verb = if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) "conectado" else "removido"
                // Só fabricante e modelo; o número de série não é lido.
                "USB: acessório $verb — ${accessory?.let { "${it.manufacturer} ${it.model}" } ?: "sem detalhes"}"
            }
            ACTION_USB_STATE -> {
                val extras = intent.extras
                val on = extras?.keySet()?.filter { extras.get(it) == true }?.sorted().orEmpty()
                "USB (central como aparelho): ${on.joinToString(", ").ifEmpty { "nada ativo" }}"
            }
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                "Áudio: sessão aberta por ${intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?"}"
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION ->
                "Áudio: sessão fechada por ${intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?"}"
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> "Áudio: saída desconectada (becoming noisy)"
            BluetoothAdapter.ACTION_STATE_CHANGED -> "Bluetooth: adaptador ${transition(intent, BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.EXTRA_STATE, ::adapterState)}"
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ->
                "Bluetooth: conexão geral ${transition(intent, BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, BluetoothAdapter.EXTRA_CONNECTION_STATE, ::profileState)}"
            BluetoothDevice.ACTION_ACL_CONNECTED -> "Bluetooth: enlace com um aparelho conectado (ACL)"
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> "Bluetooth: enlace com um aparelho desconectado (ACL)"
            in BLUETOOTH_PROFILES -> "Bluetooth ${BLUETOOTH_PROFILES[intent.action]}: ${transition(intent, ::profileState)}"
            in BLUETOOTH_PLAYING -> "Bluetooth ${BLUETOOTH_PLAYING[intent.action]}: ${transition(intent, ::playingState)}"
            in BLUETOOTH_AUDIO -> "Bluetooth ${BLUETOOTH_AUDIO[intent.action]}: áudio ${transition(intent, ::audioState)}"
            else -> "Aviso de música: ${intent.action} · ${extrasSummary(intent)}"
        }
    }.getOrElse { "Aviso ${intent.action} (detalhes ilegíveis: ${it.javaClass.simpleName})" }

    private fun transition(intent: Intent, name: (Int) -> String) =
        transition(intent, BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.EXTRA_STATE, name)

    private fun transition(intent: Intent, previousKey: String, currentKey: String, name: (Int) -> String): String {
        val current = intent.getIntExtra(currentKey, Int.MIN_VALUE)
        val previous = intent.getIntExtra(previousKey, Int.MIN_VALUE)
        val now = if (current == Int.MIN_VALUE) "?" else name(current)
        return if (previous == Int.MIN_VALUE) now else "${name(previous)} → $now"
    }

    internal fun profileState(state: Int) = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "desconectado"
        BluetoothProfile.STATE_CONNECTING -> "conectando"
        BluetoothProfile.STATE_CONNECTED -> "conectado"
        BluetoothProfile.STATE_DISCONNECTING -> "desconectando"
        else -> "estado $state"
    }

    private fun adapterState(state: Int) = when (state) {
        BluetoothAdapter.STATE_OFF -> "desligado"
        BluetoothAdapter.STATE_TURNING_ON -> "ligando"
        BluetoothAdapter.STATE_ON -> "ligado"
        BluetoothAdapter.STATE_TURNING_OFF -> "desligando"
        else -> "estado $state"
    }

    private fun playingState(state: Int) = when (state) {
        BluetoothA2dp.STATE_PLAYING -> "tocando"
        BluetoothA2dp.STATE_NOT_PLAYING -> "parado"
        else -> profileState(state)
    }

    /** Viva-voz usa 10/11/12; o HFP do lado carro (oculto no SDK) usa 0/1/2. */
    private fun audioState(state: Int) = when (state) {
        0, BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> "desconectado"
        1, BluetoothHeadset.STATE_AUDIO_CONNECTING -> "conectando"
        2, BluetoothHeadset.STATE_AUDIO_CONNECTED -> "conectado"
        else -> "estado $state"
    }

    @Suppress("DEPRECATION") // Bundle.get(String): leitura genérica de extras de outros apps.
    private fun extrasSummary(intent: Intent): String {
        val extras = intent.extras ?: return "sem dados"
        return extras.keySet().sorted().joinToString(", ") { key ->
            "$key=${extras.get(key).toString().take(40)}"
        }
    }
}
