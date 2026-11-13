package com.premiumcar.launcher.launcher

import java.util.Locale
import java.util.zip.CRC32

/**
 * Uma tela do ZLINK vista pelo serviço de acessibilidade (0.4.11), só a estrutura: quantos
 * elementos visíveis, de que tipo, com que nome interno, se têm texto e se são tocáveis. O
 * **conteúdo** dos textos nunca é guardado (pode ter o nome do iPhone, por exemplo).
 *
 * [signature]: resumo curto (8 caracteres) da estrutura; a mesma tela dá sempre o mesmo resumo,
 * mesmo com outro texto escrito nela.
 */
data class ZlinkScreen(
    val signature: String,
    val elements: Int,
    val texts: Int,
    val touchable: Int,
    val ids: List<String>,
) {

    /** Um elemento visível: tipo (ex.: `TextView`), nome interno (se houver), tem texto, é tocável. */
    data class Element(val type: String, val id: String?, val hasText: Boolean, val touchable: Boolean)

    /**
     * O ZLINK está mostrando a imagem do CarPlay: elemento `phone_content…` visível. No teste de
     * 12/11/2026 (0.4.11), todas as telas do CarPlay tinham `phone_content_event_delegate`, e
     * nenhuma tela de espera ou de ajustes tinha.
     */
    val projection: Boolean get() = ids.any { it.startsWith(PROJECTION_PREFIX) }

    /** O ZLINK mostra que está conectando (elemento `state_connecting`, na mesma gravação). */
    val connecting: Boolean get() = CONNECTING_ID in ids

    companion object {

        private const val PROJECTION_PREFIX = "phone_content"
        private const val CONNECTING_ID = "state_connecting"

        fun of(elements: List<Element>): ZlinkScreen {
            val key = elements.map { element ->
                buildString {
                    append(element.type)
                    element.id?.let { append('#').append(it) }
                    if (element.hasText) append("+t")
                    if (element.touchable) append("+c")
                }
            }.sorted().joinToString("|")
            val crc = CRC32().apply { update(key.toByteArray(Charsets.UTF_8)) }.value
            return ZlinkScreen(
                signature = String.format(Locale.ROOT, "%08x", crc),
                elements = elements.size,
                texts = elements.count { it.hasText },
                touchable = elements.count { it.touchable },
                ids = elements.mapNotNull { it.id }.distinct().sorted(),
            )
        }
    }
}

/**
 * Telas do ZLINK (0.4.11): aprende quais são de espera e percebe quando, durante o CarPlay, o
 * ZLINK volta para uma delas, o que quer dizer que o CarPlay terminou. Nas gravações, o ZLINK
 * troca de tela alguns segundos antes de religar o Bluetooth da central, o aviso usado até a
 * 0.4.10. Lógica pura, testada na JVM; quem lê a tela é o [ZlinkScreenService].
 *
 * - **Telas de espera:** toda tela do ZLINK vista com o Bluetooth da central **ligado**. Nesta
 *   central, o CarPlay sempre desliga o Bluetooth (HARDWARE.md, seção 5), então com ele ligado
 *   não há CarPlay na tela. Nos [LEARN_PAUSE_MS] depois do fim de uma sessão nada é aprendido,
 *   para a imagem do CarPlay que ainda estiver na tela não entrar como espera.
 * - **Tela do CarPlay:** com o Bluetooth desligado pelo CarPlay, uma tela que não é de espera
 *   e fica pelo menos [STABLE_MS].
 * - **Fim:** depois da tela do CarPlay, o ZLINK mostra uma tela de espera conhecida ([Result.END]).
 * - **Imagem do CarPlay (0.4.12):** se a tela do CarPlay tem a imagem do CarPlay
 *   ([ZlinkScreen.projection]), uma tela sem ela, mesmo nunca vista, é um fim provável
 *   ([Result.MAYBE_END]): quem chama confere a tela de novo logo depois e confirma com
 *   [confirmEnd], que aprende essa tela como espera. Assim o fim já vale na primeira vez.
 * - **Na dúvida, nada acontece** (tela nunca vista num ZLINK sem a imagem marcada, ou tela do
 *   CarPlay igual à de espera): o aviso do Bluetooth religando continua valendo.
 */
class ZlinkScreens(wait: Collection<String> = emptyList(), carPlay: Collection<String> = emptyList()) {

    enum class Kind { WAIT, CARPLAY, UNKNOWN }

    enum class Result {
        NONE,

        /** O ZLINK voltou para uma tela de espera conhecida: o CarPlay terminou. */
        END,

        /** A imagem do CarPlay sumiu numa tela nunca vista: conferir de novo e confirmar. */
        MAYBE_END,
    }

    /** Em que situação a tela foi vista. */
    enum class Mode {
        /** Bluetooth da central ligado: não há CarPlay na tela (a tela é aprendida como espera). */
        NO_CARPLAY,

        /** Bluetooth desligado pelo CarPlay, com a sessão registrada: vale para o fim. */
        CARPLAY,

        /** Bluetooth desligado sem sessão registrada: só acompanha, sem aprender nem avisar. */
        UNSURE,
    }

    private val waitScreens = LinkedHashSet(wait)
    private val carPlayScreens = LinkedHashSet(carPlay)
    private var current: String? = null
    private var currentSince = 0L
    private var currentMode = Mode.NO_CARPLAY
    private var carPlaySeen = false
    private var currentProjection = false
    private var projectionSeen = false
    private var learnAfter = 0L

    /** Algo novo foi aprendido desde o último [saved]. */
    var changed = false
        private set

    val waits: Set<String> get() = waitScreens.toSet()
    val carPlays: Set<String> get() = carPlayScreens.toSet()

    fun saved() {
        changed = false
    }

    fun kind(signature: String): Kind = when (signature) {
        in waitScreens -> Kind.WAIT
        in carPlayScreens -> Kind.CARPLAY
        else -> Kind.UNKNOWN
    }

    /**
     * Tela do ZLINK em [now], vista na situação [mode]; [projection]: ela tem a imagem do
     * CarPlay. Devolve se o CarPlay terminou ([Result]).
     */
    fun update(signature: String, now: Long, mode: Mode, projection: Boolean = false): Result {
        if (signature == current && mode == currentMode) return Result.NONE
        noteCarPlay(now)
        current = signature
        currentSince = now
        currentMode = mode
        currentProjection = projection
        if (mode == Mode.NO_CARPLAY) {
            // A imagem do CarPlay nunca vira espera.
            if (now >= learnAfter && !projection && signature !in carPlayScreens) remember(waitScreens, signature)
            return Result.NONE
        }
        if (mode != Mode.CARPLAY) return Result.NONE
        if (projection) projectionSeen = true
        if (!carPlaySeen) return Result.NONE
        if (signature in waitScreens) {
            carPlaySeen = false // um fim por sessão
            return Result.END
        }
        return if (projectionSeen && !projection) Result.MAYBE_END else Result.NONE
    }

    /**
     * Conferida de novo, a tela [signature] continua sem a imagem do CarPlay: é o fim, e ela é
     * uma tela de espera (fica aprendida). Devolve `false` se o fim já tinha sido dado.
     */
    fun confirmEnd(signature: String): Boolean {
        if (!carPlaySeen) return false
        carPlaySeen = false
        current = signature
        currentProjection = false
        remember(waitScreens, signature)
        return true
    }

    /** A central desligou o Bluetooth com o ZLINK na tela: o CarPlay começou (a tela atual conta a partir daqui). */
    fun carPlayStarted(now: Long) {
        carPlaySeen = false
        projectionSeen = false
        currentMode = Mode.CARPLAY
        currentSince = now
        if (currentProjection) projectionSeen = true
    }

    /**
     * A sessão terminou (por qualquer aviso). Se a única tela vista no CarPlay ainda está lá, ela
     * é a do CarPlay: fica guardada, para nunca ser aprendida como espera.
     */
    fun sessionEnded(now: Long) {
        if (!carPlaySeen) noteCarPlay(now)
        carPlaySeen = false
        projectionSeen = false
        currentMode = Mode.UNSURE // até a próxima tela, vista já com o Bluetooth religado
        learnAfter = now + LEARN_PAUSE_MS
    }

    /** A tela atual, vista no CarPlay, não é de espera e ficou o bastante: é a do CarPlay. */
    private fun noteCarPlay(now: Long) {
        val screen = current ?: return
        if (currentMode != Mode.CARPLAY || screen in waitScreens || now - currentSince < STABLE_MS) return
        // Num ZLINK que marca a imagem do CarPlay, uma tela sem ela nunca é a do CarPlay.
        if (projectionSeen && !currentProjection) return
        carPlaySeen = true
        remember(carPlayScreens, screen)
    }

    private fun remember(set: LinkedHashSet<String>, signature: String) {
        if (set.remove(signature)) {
            set.add(signature) // a mais recente fica por último (as antigas saem primeiro)
            return
        }
        set.add(signature)
        while (set.size > MAX_SCREENS) set.remove(set.first())
        changed = true
    }

    companion object {
        /** Tempo mínimo da tela do CarPlay antes de a volta à espera valer como fim. */
        const val STABLE_MS = 1_500L

        /** Depois do fim de uma sessão, nada é aprendido por este tempo. */
        const val LEARN_PAUSE_MS = 3_000L

        const val MAX_SCREENS = 40
    }
}
