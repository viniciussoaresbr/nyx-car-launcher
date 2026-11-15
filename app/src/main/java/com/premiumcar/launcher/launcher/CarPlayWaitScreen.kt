package com.premiumcar.launcher.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.TextView
import androidx.core.net.toUri
import com.premiumcar.launcher.R
import java.lang.reflect.Method

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
 * - (0.4.9) Pode ficar reservada ([park]): transparente e sem toque. A mesma janela e a mesma
 *   tela inflada voltam a aparecer, sem criar nada de novo.
 * - (0.4.17) Reservada no tamanho da tela, mas fora dela, já desenhada com a próxima mensagem:
 *   para aparecer, só muda de lugar e de transparência, sem redimensionar nem redesenhar. Na
 *   0.4.16 ela ficava com 1 px e precisava ser redimensionada e desenhada na hora do fim.
 * - (0.4.19) Ao sair da reserva já com a mensagem certa, vai para o lugar na hora, sem esperar o
 *   próximo quadro da tela ([placeNow]).
 * - Usada sempre na mesma thread (a do CarPlay, em CarPlayReturn; a principal na prévia).
 */
class CarPlayWaitScreen(context: Context) : CarPlayWaitView {

    private val context = ContextThemeWrapper(context.applicationContext, R.style.Theme_NyxCar)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var attached = false
    private var showing = false

    /** A mensagem já escrita na tela inflada: reescrever a mesma obrigaria a desenhar de novo. */
    private var bound: CarPlayWaitState? = null

    /** Quando a última volta da reserva pôs a janela no lugar na hora (0.4.19); 0 = só no quadro. */
    private var placedNowAt = 0L

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
        // Já desenhada com esta mensagem: pode ir para o lugar na hora, sem desenhar de novo.
        val ready = !bindOnce(view, state)
        val fromPark = parked
        placedNowAt = 0L
        if (!place(view, fullParams())) return
        showing = true
        if (fromPark && ready) placedNowAt = placeNow(view)
    }

    /**
     * Aplica agora a mudança da janela (0.4.19; corrigido na 0.4.20) e devolve quando ela ficou
     * no lugar (0 = não deu). Depois de `updateViewLayout`, o Android só põe a janela no lugar no
     * próximo quadro da tela: no teste da 0.4.18, 11 a 37 ms depois do pedido, com a espera do
     * ZLINK à vista. Aqui o mesmo passo do quadro (`ViewRootImpl.doTraversal`, oculto no SDK) é
     * chamado na hora, por reflexão, na thread da janela, só até o Android 8.1 (o da central),
     * que não restringe essa reflexão.
     *
     * O desenho fica para o quadro seguinte: fora de um quadro, o Android 8.1 recusa desenhar
     * (`Choreographer.getFrameTimeNanos` lança erro), e na 0.4.19 esse erro interrompia o passo
     * no meio, depois de a janela já estar no lugar. Agora, logo antes do desenho, um
     * `OnPreDrawListener` devolve `false`: o Android cancela o desenho e agenda outro quadro,
     * que desenha a mesma mensagem já à vista. Se não der, fica o quadro normal.
     */
    private fun placeNow(view: View): Long {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) return 0L
        val root = view.parent ?: return 0L
        var placedAt = 0L
        val observer = view.viewTreeObserver
        val skipDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                runCatching { view.viewTreeObserver.removeOnPreDrawListener(this) }
                if (placedAt != 0L) return true
                placedAt = SystemClock.uptimeMillis()
                return false
            }
        }
        val done = runCatching {
            val method = traversal ?: root.javaClass.getDeclaredMethod("doTraversal").also {
                it.isAccessible = true
                traversal = it
            }
            observer.addOnPreDrawListener(skipDraw)
            method.invoke(root)
        }.isSuccess
        runCatching { view.viewTreeObserver.removeOnPreDrawListener(skipDraw) }
        if (done && placedAt != 0L) return placedAt
        // Não chegou ao fim: pede a mudança de novo, para o quadro normal aplicá-la inteira.
        runCatching { windowManager?.updateViewLayout(view, fullParams()) }
        return 0L
    }

    override fun park(next: CarPlayWaitState) {
        val view = inflated()
        bindOnce(view, next) // reservada, a troca de texto é desenhada fora da vista
        if (parked) return
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        if (place(view, parkedParams())) showing = false
    }

    override fun afterNextFrame(action: (FrameMarks) -> Unit) {
        val looper = Looper.myLooper()
        val view = root
        if (!attached || looper == null || view == null) {
            action(FrameMarks())
            return
        }
        val handler = Handler(looper)
        val placedAt = placedNowAt
        var frameAt = 0L
        var layoutAt = 0L
        // Chamado depois de o Android pôr a janela no lugar, logo antes do desenho (medição).
        val laidOut = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (layoutAt == 0L) layoutAt = SystemClock.uptimeMillis()
                runCatching { view.viewTreeObserver.removeOnPreDrawListener(this) }
                return true
            }
        }
        val once = object : Runnable {
            private var done = false
            override fun run() {
                if (done) return
                done = true
                handler.removeCallbacks(this)
                runCatching { view.viewTreeObserver.removeOnPreDrawListener(laidOut) }
                action(if (placedAt != 0L) FrameMarks(frameAt, placedAt, immediate = true) else FrameMarks(frameAt, layoutAt))
            }
        }
        runCatching { view.viewTreeObserver.addOnPreDrawListener(laidOut) }
        // Postada de dentro do quadro, a mensagem só roda depois de a janela ser desenhada nele
        // (o Android segura as mensagens comuns até o desenho). A outra garante a ação mesmo se
        // nenhum quadro vier (tela apagada, por exemplo).
        Choreographer.getInstance().postFrameCallback {
            frameAt = SystemClock.uptimeMillis()
            handler.post(once)
        }
        handler.postDelayed(once, FRAME_FALLBACK_MS)
    }

    /** Escreve a mensagem se ela mudou; devolve `true` quando mudou (a janela precisa ser desenhada). */
    private fun bindOnce(view: View, state: CarPlayWaitState): Boolean {
        if (bound == state) return false
        bind(view, state)
        bound = state
        return true
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

    private fun fullParams() = params(extraFlags = 0)

    /**
     * Reservada (0.4.17): do tamanho da tela, mas fora dela, à direita (nenhum toque cai nela),
     * com transparência total (o Android nem a compõe) e sem receber toques.
     */
    private fun parkedParams() = params(extraFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        val metrics = context.resources.displayMetrics
        x = metrics.widthPixels + metrics.heightPixels
        alpha = 0f
    }

    // Sempre TRANSLUCENT e do mesmo tamanho: trocar o formato obrigaria o Android a recriar a
    // janela, e trocar o tamanho, a desenhá-la de novo.
    private fun params(extraFlags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "Nyx Car — CarPlay"
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
    }

    companion object {

        private const val PREVIEW_MS = 5_000L

        /** `ViewRootImpl.doTraversal`, achado uma vez por processo ([placeNow]). */
        private var traversal: Method? = null

        /** Sem quadro novo em 100 ms (tela apagada, por exemplo), a ação de [afterNextFrame] roda assim mesmo. */
        private const val FRAME_FALLBACK_MS = 100L

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
                    CarPlayWaitState.CONNECTING_IPHONE -> R.string.carplay_wait_status_iphone_cable
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
