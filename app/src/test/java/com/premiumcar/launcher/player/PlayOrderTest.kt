package com.premiumcar.launcher.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Ordem de reprodução do aleatório (0.4.24): lógica pura, na JVM. */
class PlayOrderTest {

    private fun assertPermutation(size: Int, order: IntArray) {
        assertEquals((0 until size).toList(), order.sorted())
    }

    @Test
    fun `sem aleatorio a ordem e a da pasta`() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3), PlayOrder.natural(4))
        assertArrayEquals(IntArray(0), PlayOrder.natural(0))
    }

    @Test
    fun `aleatorio passa por todas as musicas uma vez, com a ancora na frente`() {
        (0 until 30).forEach { anchor ->
            val order = PlayOrder.shuffled(30, seed = 1234L + anchor, anchor = anchor)
            assertPermutation(30, order)
            assertEquals(anchor, order[0])
        }
    }

    @Test
    fun `mesma semente da a mesma ordem, como depois de ligar a central`() {
        val first = PlayOrder.shuffled(50, seed = 42L, anchor = 7)
        assertArrayEquals(first, PlayOrder.shuffled(50, seed = 42L, anchor = 7))
        assertFalse("Outra semente, outra ordem", first.contentEquals(PlayOrder.shuffled(50, seed = 43L, anchor = 7)))
        assertFalse("Embaralhada de verdade", first.contentEquals(PlayOrder.natural(50).also { it[0] = 7; it[7] = 0 }))
    }

    @Test
    fun `ancora fora da fila nao muda o sorteio`() {
        val order = PlayOrder.shuffled(10, seed = 9L, anchor = -1)
        assertPermutation(10, order)
        assertArrayEquals(order, PlayOrder.shuffled(10, seed = 9L, anchor = 99))
    }

    @Test
    fun `filas de uma ou nenhuma musica`() {
        assertArrayEquals(intArrayOf(0), PlayOrder.shuffled(1, seed = 5L, anchor = 0))
        assertArrayEquals(IntArray(0), PlayOrder.shuffled(0, seed = 5L, anchor = 0))
    }
}
