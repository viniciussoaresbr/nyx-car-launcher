package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.app.Notification
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder
import com.premiumcar.launcher.player.PlayerLog
import java.util.Locale
import java.util.zip.CRC32

/**
 * Notificações do ZLINK (0.4.13), em busca de um aviso do fim do CarPlay que não se perca ao
 * desligar o carro: o serviço de acessibilidade (0.4.11) é desligado pelo Android quando a
 * central fecha os apps, mas o "Acesso a notificações" continua dado (HARDWARE.md, seção 5).
 *
 * Só as do ZLINK, e só a forma: número, se é contínua, canal, categoria, quantas ações e um
 * resumo de 8 caracteres do texto, que muda quando o texto muda. O **texto** nunca é guardado
 * nem registrado (pode ter o nome do iPhone). Somente leitura: nada é tocado ou dispensado.
 *
 * **Leitor desligado:** nos testes da 0.4.13 e da 0.4.16, o acesso continuava dado, mas o
 * Android não religou o leitor depois de a central fechar o app (as sessões de mídia funcionam
 * assim mesmo, porque só pedem o acesso). Da 0.4.14 à 0.4.16, o app pedia ao Android para
 * religá-lo (desativando e reativando o componente) e isso não adiantou: o pedido saiu na 0.4.17.
 * O que está aqui só registra algo se o Android religar o leitor por conta própria.
 */
@SuppressLint("StaticFieldLeak") // o leitor de notificações, só enquanto o Android o mantém ligado
object ZlinkNotifications {

    /** A forma de uma notificação, sem o conteúdo. Lógica pura, testada na JVM. */
    data class Shape(
        val id: Int,
        val ongoing: Boolean,
        val channel: String?,
        val category: String?,
        val actions: Int,
        val textHash: String,
    ) {
        fun describe(): String = buildString {
            append("nº ").append(id)
            append(if (ongoing) ", contínua" else ", comum")
            channel?.let { append(", canal ").append(it) }
            category?.let { append(", categoria ").append(it) }
            append(", ").append(actions).append(if (actions == 1) " ação" else " ações")
            append(", texto ").append(textHash)
        }
    }

    /** Resumo dos textos, só para saber se mudaram ("vazio" se não há texto). */
    fun hash(texts: List<CharSequence?>): String {
        val joined = texts.joinToString("\u0000") { it?.toString().orEmpty() }
        if (joined.replace("\u0000", "").isEmpty()) return "vazio"
        val crc = CRC32().apply { update(joined.toByteArray(Charsets.UTF_8)) }.value
        return String.format(Locale.ROOT, "%08x", crc)
    }

    @Volatile
    private var listener: NotificationListenerService? = null
    private val shown = mutableMapOf<String, Shape>()

    /** Quando uma notificação do ZLINK apareceu, mudou ou sumiu pela última vez (relógio do sistema). */
    @Volatile
    var lastChangeAt = 0L
        private set

    @Volatile
    private var connectedLogged = false

    fun connected(service: NotificationListenerService) {
        listener = service
        if (!connectedLogged) PlayerLog.add("Leitor de notificações ligado: as notificações do ZLINK são observadas")
        connectedLogged = true
        synchronized(shown) {
            shown.clear()
            active(service).forEach { shown[it.key] = shape(it) }
        }
    }

    fun disconnected(service: NotificationListenerService) {
        if (listener === service) listener = null
    }

    fun posted(sbn: StatusBarNotification) {
        if (sbn.packageName != CarApps.CARPLAY.packageName) return
        val shape = shape(sbn)
        val before = synchronized(shown) { shown.put(sbn.key, shape) }
        if (before == shape) return
        lastChangeAt = SystemClock.uptimeMillis()
        log(if (before == null) "apareceu — ${shape.describe()}" else "mudou — ${shape.describe()} (antes: texto ${before.textHash})")
    }

    fun removed(sbn: StatusBarNotification) {
        if (sbn.packageName != CarApps.CARPLAY.packageName) return
        val before = synchronized(shown) { shown.remove(sbn.key) }
        lastChangeAt = SystemClock.uptimeMillis()
        log("sumiu — ${(before ?: shape(sbn)).describe()}")
    }

    /** As do ZLINK agora, para o começo de cada gravação ("nenhuma" ou leitor desligado). */
    fun summary(): String {
        val service = listener ?: return "leitor desligado (sem o acesso, ou não religado pelo Android depois de a central fechar o app)"
        val list = active(service).map { shape(it).describe() }
        return if (list.isEmpty()) "nenhuma" else list.joinToString(" | ")
    }

    private fun active(service: NotificationListenerService): List<StatusBarNotification> =
        runCatching { service.activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.packageName == CarApps.CARPLAY.packageName }

    private fun shape(sbn: StatusBarNotification): Shape {
        val notification = sbn.notification
        val extras = notification.extras
        return Shape(
            id = sbn.id,
            ongoing = sbn.isOngoing,
            channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null,
            category = notification.category,
            actions = notification.actions?.size ?: 0,
            textHash = hash(
                listOf(
                    extras?.getCharSequence(Notification.EXTRA_TITLE),
                    extras?.getCharSequence(Notification.EXTRA_TEXT),
                    extras?.getCharSequence(Notification.EXTRA_SUB_TEXT),
                ),
            ),
        )
    }

    private fun log(text: String) {
        if (CarTestRecorder.running) CarTestLog.add("ZLINK (notificação): $text")
    }

    internal fun resetForTests() {
        listener = null
        connectedLogged = false
        synchronized(shown) { shown.clear() }
        lastChangeAt = 0L
    }

    /** Nos testes: uma mudança nas notificações do ZLINK agora. */
    internal fun markChangeForTests(at: Long) {
        lastChangeAt = at
    }
}
