package com.premiumcar.launcher.diagnostics

/**
 * Relaciona o nível de API (fonte confiável, usado pelo próprio sistema) com a
 * versão comercial do Android. O texto da versão pode ser alterado pelo fabricante;
 * o nível de API, não.
 */
object AndroidVersions {
    private val names = mapOf(
        21 to "5.0", 22 to "5.1", 23 to "6.0", 24 to "7.0", 25 to "7.1",
        26 to "8.0", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11",
        31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15",
        36 to "16", 37 to "17",
    )

    fun nameFor(sdk: Int): String = names[sdk] ?: "desconhecido"

    /** Compara a versão principal declarada (ex.: "13", "8.1.0") com a esperada para a API. */
    fun isConsistent(declaredRelease: String, sdk: Int): Boolean {
        val expected = names[sdk] ?: return true
        val declaredMajor = declaredRelease.trim().substringBefore('.').toIntOrNull() ?: return true
        val expectedMajor = expected.takeWhile { it.isDigit() }.toIntOrNull() ?: return true
        return declaredMajor == expectedMajor
    }
}
