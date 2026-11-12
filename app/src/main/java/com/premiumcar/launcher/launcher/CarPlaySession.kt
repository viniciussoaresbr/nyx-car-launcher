package com.premiumcar.launcher.launcher

/**
 * Quando a sessão do CarPlay termina (0.4.5, pedido do usuário: nunca ficar preso na tela do
 * ZLINK). Lógica pura, testada na JVM; quem escuta o Android é [CarPlayReturn].
 *
 * Sinais (HARDWARE.md, seção 5; confirmados nas gravações de 12/11/2026):
 * - **Bluetooth:** ao entrar no CarPlay (pelo cabo ou sem fio), a central desliga o próprio
 *   Bluetooth com o ZLINK na tela (8,4 a 8,9 s depois de o iPhone aparecer no USB). Ao tirar o
 *   cabo, ele volta a ligar: é o primeiro sinal do fim, ~0,5 s antes do USB.
 * - **Cabo:** com o CarPlay pelo cabo, a central é "aparelho" USB do iPhone (estado USB
 *   `connected`). Ao tirar o cabo, esse estado cai.
 *
 * Um sinal de fim só vale se nada indicar reconexão em [END_GRACE_MS]: num mau contato (cabo
 * tirado e posto de volta), o iPhone reaparece no USB ([appleAttached]) ou a porta volta a
 * ficar conectada, e a sessão continua. Cada sessão termina uma vez só.
 */
class CarPlaySession {

    enum class Signal(val label: String) {
        CABLE("cabo desconectado"),
        BLUETOOTH("Bluetooth da central ligou de novo"),
    }

    private var cable = false
    private var ended = true
    private var pending: Signal? = null

    /** O Bluetooth da central foi desligado pelo CarPlay nesta sessão. */
    var bluetoothOff = false
        private set

    /** Há uma sessão em andamento (cabo conectado ou Bluetooth desligado pelo CarPlay). */
    val active: Boolean get() = !ended

    /** O cabo do iPhone está conectado (a central é o "aparelho" USB dele). */
    val cableConnected: Boolean get() = cable

    /**
     * Estado USB da central como aparelho. Devolve `true` quando o cabo acabou de cair: é um
     * fim provável, a confirmar com [confirmEnd] depois de [END_GRACE_MS].
     */
    fun usb(connected: Boolean): Boolean {
        if (connected) {
            if (!cable) {
                // Sessão nova pelo cabo (ou o mesmo cabo de volta): um "Bluetooth ligou" atrasado
                // não pode encerrá-la. Nesta sessão, o Bluetooth só desliga ~6 s depois.
                if (ended) begin()
                bluetoothOff = false
            }
            cable = true
            pending = null
            return false
        }
        if (!cable) return false
        cable = false
        return endLikely(Signal.CABLE)
    }

    /** O iPhone apareceu no USB: se havia um fim provável, era um mau contato (devolve `true`). */
    fun appleAttached(): Boolean {
        val wasPending = pending != null
        pending = null
        return wasPending
    }

    /**
     * Bluetooth da central ligado ou desligado. Desligar só conta com o ZLINK na tela
     * ([zlinkOnTop]): é o CarPlay entrando. Devolve `true` quando ele religa (fim provável).
     */
    fun bluetooth(on: Boolean, zlinkOnTop: Boolean): Boolean {
        if (!on) {
            if (!bluetoothOff && zlinkOnTop) {
                if (ended) begin()
                bluetoothOff = true
            }
            return false
        }
        if (!bluetoothOff) return false
        bluetoothOff = false
        return endLikely(Signal.BLUETOOTH)
    }

    /** Depois da espera: se nada indicou reconexão, a sessão terminou (devolve o primeiro sinal). */
    fun confirmEnd(): Signal? {
        val signal = pending ?: return null
        pending = null
        if (ended) return null
        ended = true
        return signal
    }

    private fun endLikely(signal: Signal): Boolean {
        if (ended) return false
        if (pending == null) pending = signal
        return true
    }

    private fun begin() {
        ended = false
        pending = null
    }

    companion object {
        /**
         * Espera antes de dar a sessão por encerrada. Nas gravações, um cabo posto de volta faz o
         * iPhone reaparecer no USB em ~1 s; um cabo tirado de vez nunca faz.
         */
        const val END_GRACE_MS = 2_000L
    }
}
