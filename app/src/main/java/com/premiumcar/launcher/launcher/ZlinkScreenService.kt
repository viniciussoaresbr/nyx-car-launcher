package com.premiumcar.launcher.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder

/**
 * Serviço de acessibilidade do Nyx Car, restrito ao ZLINK (0.4.11; quarta exceção do plano,
 * seção 9, autorizada pelo usuário em 12/11/2026). Serve para uma coisa: saber na hora quando
 * o ZLINK troca de tela, para o Nyx Car cobrir a tela de espera dele quando o CarPlay termina
 * ([ZlinkScreens], [CarPlayReturn]).
 *
 * - O Android só entrega a ele os avisos do ZLINK (`packageNames` em
 *   `res/xml/zlink_screen_service.xml`), e ele só lê a janela do ZLINK.
 * - Lê só a estrutura ([ZlinkScreen]): tipo e nome interno dos elementos visíveis, se têm
 *   texto e se são tocáveis. O conteúdo dos textos não é guardado nem registrado.
 * - Não toca em nada, não age no ZLINK, não muda o CarPlay.
 * - Quem liga e desliga é o usuário, em Configurações → Acessibilidade ("Nyx Car — fim do
 *   CarPlay"), com o atalho no Nyx Diagnóstico → Testes no carro → cartão 6. Desligado, o fim
 *   do CarPlay continua sendo percebido pelo Bluetooth da central, como na 0.4.10.
 */
class ZlinkScreenService : AccessibilityService() {

    override fun onServiceConnected() {
        CarPlayReturn.screenServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CarApps.CARPLAY.packageName) return
        val receivedAt = SystemClock.uptimeMillis()
        val kind = if (CarTestRecorder.running) eventKind(event.eventType, event.className, event.contentChangeTypes) else null
        CarPlayReturn.onZlinkScreenChanged(event.windowId, event.eventTime, receivedAt, kind)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        CarPlayReturn.screenServiceDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CarPlayReturn.screenServiceDisconnected(this)
        super.onDestroy()
    }

    /**
     * Estrutura da tela do ZLINK na janela [windowId] (a do último aviso), ou na janela ativa;
     * `null` se o ZLINK não estiver na tela. Pode ser chamada de qualquer thread.
     */
    fun readZlink(windowId: Int): ZlinkScreen? {
        val root = runCatching { windows.firstOrNull { it.id == windowId }?.root }.getOrNull()
            ?: runCatching { rootInActiveWindow }.getOrNull()
            ?: return null
        return try {
            if (root.packageName?.toString() == CarApps.CARPLAY.packageName) ZlinkScreen.of(visibleElements(root)) else null
        } finally {
            runCatching { root.recycle() }
        }
    }

    companion object {

        private const val MAX_NODES = 300

        private const val KNOWN_CHANGES = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION

        /**
         * Tipo de um aviso do ZLINK, para a gravação (0.4.18): se é de janela ou de conteúdo, o
         * tipo do elemento que mudou e o que mudou nele. Só a estrutura; os textos nunca são lidos.
         */
        internal fun eventKind(type: Int, className: CharSequence?, changes: Int): String {
            val kind = when (type) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "janela"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "conteúdo"
                else -> "aviso $type"
            }
            val element = className?.toString()?.substringAfterLast('.')?.ifEmpty { null } ?: "?"
            val what = buildList {
                if ((changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0) add("subárvore")
                if ((changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0) add("texto")
                if ((changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0) add("descrição")
                val other = changes and KNOWN_CHANGES.inv()
                if (other != 0) add("outro $other")
            }
            return if (what.isEmpty()) "$kind/$element" else "$kind/$element/${what.joinToString("+")}"
        }

        /** Elementos visíveis, de cima para baixo. O texto só é olhado para saber se existe. */
        private fun visibleElements(root: AccessibilityNodeInfo): List<ZlinkScreen.Element> {
            val elements = ArrayList<ZlinkScreen.Element>()
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.add(root)
            var visited = 0
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                visited++
                if (visited <= MAX_NODES && node.isVisibleToUser) {
                    elements += ZlinkScreen.Element(
                        type = node.className?.toString()?.substringAfterLast('.').orEmpty(),
                        id = node.viewIdResourceName?.substringAfter(":id/"),
                        hasText = !node.text.isNullOrEmpty(),
                        touchable = node.isClickable,
                    )
                    for (index in 0 until node.childCount) runCatching { node.getChild(index) }.getOrNull()?.let(pending::add)
                }
                if (node !== root) runCatching { node.recycle() }
            }
            return elements
        }

        /** O usuário ligou o serviço em Configurações → Acessibilidade. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, ZlinkScreenService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Abre Configurações → Acessibilidade. */
        fun openSettings(context: Context): String {
            val opened = runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            return if (opened) {
                "Ative \"Nyx Car — fim do CarPlay\" e volte aqui."
            } else {
                "Não foi possível abrir a Acessibilidade neste aparelho."
            }
        }
    }
}
