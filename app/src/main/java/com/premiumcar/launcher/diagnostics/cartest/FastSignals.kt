package com.premiumcar.launcher.diagnostics.cartest

/**
 * Leitura rápida durante a gravação dos testes no carro (0.4.9): as propriedades do app na tela
 * e da porta USB, lidas direto a cada 100 ms, para o horário de cada mudança ser o exato (antes,
 * uma vez por segundo, com o `getprop`).
 *
 * Na 0.4.9 também eram lidos arquivos do sistema sobre o USB da central (`/sys/class/udc`,
 * `/sys/class/android_usb`, modo e energia do controlador `musb-hdrc`) e a lista de interfaces
 * de rede, em busca de um aviso do fim do CarPlay que chegasse antes da tela do ZLINK. No teste
 * de 12/11/2026, nenhum desses arquivos pôde ser lido por um app nesta central e a lista de
 * interfaces veio vazia (ela exige a permissão de internet, que o Nyx Car não tem): a leitura
 * saiu na 0.4.10 (HARDWARE.md, seção 5).
 */
internal object FastSignals {

    /** Propriedades lidas direto, a cada 100 ms, no lugar da leitura de 1 s. */
    val PROPERTIES = listOf("sys.top.activity", "sys.usb.state", "sys.usb.config")
}
