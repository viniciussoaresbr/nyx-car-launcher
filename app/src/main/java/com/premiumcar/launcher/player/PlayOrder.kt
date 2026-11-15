package com.premiumcar.launcher.player

import java.util.Random

/**
 * Ordem em que a fila toca (0.4.24): posições da fila, na ordem de reprodução. Lógica pura,
 * testada na JVM.
 *
 * Sem aleatório, a ordem natural. Com aleatório, uma permutação sorteada a partir de uma
 * semente, com a música âncora (a que estava tocando quando a ordem foi sorteada) na frente:
 * cada música toca uma vez antes de repetir, e "anterior" volta à que tocou antes.
 *
 * A semente e a âncora ficam guardadas ([PlayerMemory]): depois de ligar a central, a mesma
 * pasta dá a mesma ordem, e o aleatório continua de onde parou, sem repetir as que já
 * tocaram. Por isso o sorteio usa `java.util.Random`, cuja sequência para uma semente é fixada
 * pela especificação do Java e não muda entre versões do Android ou do Kotlin.
 */
object PlayOrder {

    fun natural(size: Int): IntArray = IntArray(size) { it }

    /** Permutação de `0 until size` sorteada com [seed], com [anchor] na frente (se estiver na fila). */
    fun shuffled(size: Int, seed: Long, anchor: Int): IntArray {
        val order = natural(size)
        val random = Random(seed)
        for (i in size - 1 downTo 1) { // Fisher-Yates
            val j = random.nextInt(i + 1)
            val swap = order[i]
            order[i] = order[j]
            order[j] = swap
        }
        val at = order.indexOf(anchor)
        if (at > 0) {
            System.arraycopy(order, 0, order, 1, at) // os anteriores andam uma casa, na mesma ordem
            order[0] = anchor
        }
        return order
    }
}
