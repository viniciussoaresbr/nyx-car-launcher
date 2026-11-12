package com.premiumcar.launcher.system

/**
 * App na tela segundo o firmware: a propriedade `sys.top.activity` da AutoChips (por exemplo,
 * `com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity`), lida com
 * `getprop` como no Nyx Diagnóstico. Somente leitura. Lenta para a thread principal (abre um
 * processo): use em segundo plano.
 */
object TopActivity {

    private const val KEY = "sys.top.activity"

    fun read(): String? = try {
        val process = ProcessBuilder("getprop", KEY).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().also { process.waitFor() }.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    /** Pacote do app na tela, ou `null` se a leitura falhar. */
    fun packageOf(value: String?): String? = value?.substringBefore('/')?.takeIf { it.isNotBlank() }
}
