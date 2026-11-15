package com.premiumcar.launcher.launcher

/**
 * O que a tela de espera do Nyx Car mostra (B1, 0.4.7; "Aguardando o celular" na 0.4.8;
 * "Conexão interrompida" na 0.4.9). [CONNECTING_IPHONE] (0.4.15): pelo cabo, quando o USB
 * identificou um aparelho da Apple; nos outros casos o texto fala em "celular", porque um
 * Android também pode se conectar ao ZLINK.
 */
enum class CarPlayWaitState {
    WAITING_PHONE,
    CONNECTING_CABLE,
    CONNECTING_IPHONE,
    CONNECTING_WIRELESS,
    PREPARING,
    INTERRUPTED,
    DISCONNECTED,
}

/**
 * Marcas do quadro em que a janela entrou (0.4.18), no relógio `SystemClock.uptimeMillis`; 0 =
 * não medida. [frameAt]: a thread da janela começou o quadro. [layoutAt]: o Android acabou de
 * pôr a janela no lugar, logo antes do desenho. [immediate] (0.4.19): ela foi posta no lugar na
 * hora, sem esperar o quadro.
 */
data class FrameMarks(val frameAt: Long = 0L, val layoutAt: Long = 0L, val immediate: Boolean = false)

/**
 * Tela de espera do Nyx Car por cima do ZLINK. A real é uma janela sobreposta
 * ([CarPlayWaitScreen]); nos testes, uma falsa.
 */
interface CarPlayWaitView {

    /** A tela está à vista (reservada com [park] não conta). */
    val visible: Boolean

    /** A permissão "Sobrepor a outros apps" foi dada pelo usuário. */
    fun canShow(): Boolean

    /**
     * Mostra (ou atualiza) a tela. [onTap]: o usuário tocou nela. [onLongPress]: tocou e segurou
     * ("Aguardando o iPhone": abre o ZLINK mesmo assim; "Conectando": fecha só a tela).
     */
    fun show(state: CarPlayWaitState, onTap: () -> Unit, onLongPress: (() -> Unit)? = null)

    /**
     * Some da vista, mas a janela fica reservada: transparente e sem receber toques (0.4.9).
     * Usada enquanto o CarPlay está em uso ou prestes a começar. Com ela, o Android trata o Nyx
     * Car como um app à vista: ele recebe processador na hora em que o CarPlay termina, mesmo
     * com o ZLINK na frente, e a tela volta mais rápido, porque a janela já existe.
     *
     * (0.4.17) A janela reservada já fica desenhada com [next], a mensagem mais provável da
     * próxima vez: para aparecer, ela só muda de lugar, sem esperar um desenho novo.
     */
    fun park(next: CarPlayWaitState)

    /**
     * Roda [action] logo depois do próximo quadro desenhado da janela, na mesma thread (0.4.17):
     * para a janela entrar antes de qualquer outra coisa demorada. Sem a janela, roda na hora.
     * (0.4.18) Entrega as marcas desse quadro, para a medição do fim.
     */
    fun afterNextFrame(action: (FrameMarks) -> Unit)

    /** Fecha a janela de vez. */
    fun hide()
}
