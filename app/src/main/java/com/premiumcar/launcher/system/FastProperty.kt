package com.premiumcar.launcher.system

import android.annotation.SuppressLint
import java.lang.reflect.Method

/**
 * Leitura direta de uma propriedade do sistema, como o próprio Android faz, em microssegundos
 * (0.4.8; separada do [TopActivity] na 0.4.9). Somente leitura. No Android 8.1 (a central) isso
 * é permitido a qualquer app; onde não for, [available] é `false` e quem chama usa o `getprop`.
 */
object FastProperty {

    // Função interna do Android, só para leitura.
    @SuppressLint("PrivateApi")
    private val getter: Method? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
    }.getOrNull()

    /** A leitura direta existe neste aparelho. */
    val available: Boolean get() = getter != null

    /** Valor da propriedade, ou `null` se vazia ou se a leitura falhar. */
    fun get(key: String): String? = runCatching { (getter?.invoke(null, key) as? String)?.trim() }.getOrNull()?.ifEmpty { null }
}
