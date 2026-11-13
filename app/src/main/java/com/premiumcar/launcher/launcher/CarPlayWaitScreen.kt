package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.net.toUri
import com.premiumcar.launcher.R

/**
 * Janela "Conectando ao CarPlay" do Nyx Car, sobreposta à tela de espera do ZLINK (B1, 0.4.7;
 * terceira exceção do plano, seção 9). O ZLINK continua na frente, por baixo, e a conexão do
 * CarPlay não é tocada: a janela só cobre o que aparece na tela.
 *
 * - Exige a permissão "Sobrepor a outros apps", dada pelo usuário (reversível) no Nyx
 *   Diagnóstico → Testes no carro. Sem ela, nada aparece e o ZLINK fica como sempre.
 * - Não recebe o foco: Voltar e Home continuam indo para o app de baixo. Um toque responde
 *   (o que faz é decidido em CarPlayReturn).
 * - Sem animação: muda só o texto, conforme a conexão avança.
 * - (0.4.9) Pode ficar reservada ([park]): 1 px no canto, transparente e sem toque. A mesma
 *   janela e a mesma tela inflada voltam a ocupar a tela inteira, sem criar nada de novo.
 * - Usada sempre na mesma thread (a do CarPlay, em CarPlayReturn; a principal na prévia).
 */
class CarPlayWaitScreen(context: Context) : CarPlayWaitView {

    private val context = ContextThemeWrapper(context.applicationContext, R.style.Theme_NyxCar)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var attached = false
    private var showing = false

    override val visible: Boolean get() = attached && showing

    /** A janela existe, mas reservada (invisível). */
    val parked: Boolean get() = attached && !showing

    override fun canShow(): Boolean = Settings.canDrawOverlays(context)

    override fun show(state: CarPlayWaitState, onTap: () -> Unit, onLongPress: (() -> Unit)?) {
        val view = inflated()
        view.setOnClickListener { onTap() }
        if (onLongPress == null) {
            view.setOnLongClickListener(null)
            view.isLongClickable = false
        } else {
            view.setOnLongClickListener {
                onLongPress()
                true
            }
        }
        bind(view, state)
        if (!place(view, fullParams())) return
        showing = true
    }

    override fun park() {
        if (parked) return
        val view = inflated()
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        if (place(view, parkedParams())) showing = false
    }

    override fun hide() {
        val view = root
        if (attached && view != null) runCatching { windowManager?.removeViewImmediate(view) }
        attached = false
        showing = false
    }

    private fun inflated(): View = root ?: createView(context).also { root = it }

    /** Põe a janela com estes parâmetros: cria na primeira vez, depois só ajusta. */
    private fun place(view: View, params: WindowManager.LayoutParams): Boolean {
        val manager = windowManager ?: return false
        return try {
            if (attached) {
                manager.updateViewLayout(view, params)
            } else {
                manager.addView(view, params)
                attached = true
            }
            true
        } catch (e: RuntimeException) {
            hide() // permissão retirada ou firmware recusou: o ZLINK fica como sempre
            false
        }
    }

    // Sempre TRANSLUCENT: trocar o formato obrigaria o Android a recriar a janela.
    private fun fullParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { common() }

    /** Reservada: 1 px no canto, transparência total (o Android nem a desenha) e toques passam direto. */
    private fun parkedParams() = WindowManager.LayoutParams(
        1,
        1,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        common()
        alpha = 0f
    }

    private fun WindowManager.LayoutParams.common() {
        title = "Nyx Car — CarPlay"
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
    }

    companion object {

        private const val PREVIEW_MS = 5_000L

        /** A janela sobreposta não tem pai: inflada sem raiz, de propósito. */
        @SuppressLint("InflateParams")
        fun createView(context: Context): View =
            LayoutInflater.from(context).inflate(R.layout.overlay_carplay, null, false)

        fun bind(view: View, state: CarPlayWaitState) {
            val title = view.findViewById<TextView>(R.id.carplay_wait_title)
            val status = view.findViewById<TextView>(R.id.carplay_wait_status)
            val hint = view.findViewById<TextView>(R.id.carplay_wait_hint)
            title.setText(
                when (state) {
                    CarPlayWaitState.WAITING_PHONE -> R.string.carplay_wait_title_waiting
                    CarPlayWaitState.INTERRUPTED -> R.string.carplay_wait_title_interrupted
                    CarPlayWaitState.DISCONNECTED -> R.string.carplay_wait_title_disconnected
                    else -> R.string.carplay_wait_title_connecting
                },
            )
            hint.setText(
                when (state) {
                    CarPlayWaitState.WAITING_PHONE -> R.string.carplay_wait_hint_waiting
                    CarPlayWaitState.INTERRUPTED -> R.string.carplay_wait_hint_interrupted
                    CarPlayWaitState.DISCONNECTED -> R.string.carplay_wait_hint
                    else -> R.string.carplay_wait_hint_connecting
                },
            )
            status.setText(
                when (state) {
                    CarPlayWaitState.WAITING_PHONE -> R.string.carplay_wait_status_waiting
                    CarPlayWaitState.CONNECTING_CABLE -> R.string.carplay_wait_status_cable
                    CarPlayWaitState.CONNECTING_WIRELESS -> R.string.carplay_wait_status_wireless
                    CarPlayWaitState.PREPARING -> R.string.carplay_wait_status_preparing
                    CarPlayWaitState.INTERRUPTED -> R.string.carplay_wait_status_interrupted
                    CarPlayWaitState.DISCONNECTED -> R.string.carplay_wait_status_disconnected
                },
            )
        }

        @Suppress("DEPRECATION") // TYPE_PHONE: só até o Android 7.1 (a central roda o 8.1).
        private fun overlayType(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        /** Página "Sobrepor a outros apps" do Nyx Car nas Configurações do Android. */
        fun openPermission(context: Context): String {
            val specific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { context.startActivity(specific) }.isSuccess ||
                runCatching { context.startActivity(AppLauncher.detailsIntent(context.packageName)) }.isSuccess
            return if (opened) "Ative \"Sobrepor a outros apps\" para o Nyx Car e volte aqui." else "Não foi possível abrir a permissão."
        }

        /** Mostra a tela por alguns segundos, para conferir o visual sem conectar o iPhone. */
        fun preview(context: Context): String {
            val screen = CarPlayWaitScreen(context)
            if (!screen.canShow()) return "Falta a permissão \"Sobrepor a outros apps\"."
            screen.show(CarPlayWaitState.CONNECTING_CABLE, onTap = { screen.hide() })
            Handler(Looper.getMainLooper()).postDelayed({ screen.hide() }, PREVIEW_MS)
            return "Tela de espera do CarPlay por ${PREVIEW_MS / 1000} s (toque para fechar antes)."
        }
    }
}
