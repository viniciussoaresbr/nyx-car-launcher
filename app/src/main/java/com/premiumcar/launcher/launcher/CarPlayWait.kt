package com.premiumcar.launcher.launcher

/**
 * O que a tela de espera do Nyx Car mostra (B1, 0.4.7; "Aguardando o iPhone" na 0.4.8;
 * "Conexão interrompida" na 0.4.9).
 */
enum class CarPlayWaitState { WAITING_PHONE, CONNECTING_CABLE, CONNECTING_WIRELESS, PREPARING, INTERRUPTED, DISCONNECTED }

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
     * Some da vista, mas a janela fica reservada: 1 px, transparente e sem receber toques (0.4.9).
     * Usada enquanto o CarPlay está em uso ou prestes a começar. Com ela, o Android trata o Nyx
     * Car como um app à vista: ele recebe processador na hora em que o CarPlay termina, mesmo
     * com o ZLINK na frente, e a tela volta mais rápido, porque a janela já existe.
     */
    fun park()

    /** Fecha a janela de vez. */
    fun hide()
}
