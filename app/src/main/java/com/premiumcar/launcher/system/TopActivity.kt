package com.premiumcar.launcher.system

/**
 * App na tela segundo o firmware: a propriedade `sys.top.activity` da AutoChips (por exemplo,
 * `com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity`). Somente leitura.
 *
 * - [readFast] (0.4.8): lê direto a propriedade ([FastProperty]), em microssegundos. Pode ser
 *   lida a cada 100 ms.
 * - [read]: a rápida e, se ela falhar, o utilitário `getprop` (abre um processo: ~0,4 s nesta
 *   central; nunca na thread principal).
 */
object TopActivity {

    private const val KEY = "sys.top.activity"

    /** A leitura rápida existe neste aparelho. */
    val fast: Boolean get() = FastProperty.available

    fun readFast(): String? = FastProperty.get(KEY)

    fun read(): String? = readFast() ?: readWithGetprop()

    private fun readWithGetprop(): String? = try {
        val process = ProcessBuilder("getprop", KEY).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().also { process.waitFor() }.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    /** Pacote do app na tela, ou `null` se a leitura falhar. */
    fun packageOf(value: String?): String? = value?.substringBefore('/')?.takeIf { it.isNotBlank() }
}
