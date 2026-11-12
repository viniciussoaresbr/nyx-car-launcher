package com.premiumcar.launcher.diagnostics

import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.premiumcar.launcher.diagnostics.ui.DiagnosticsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Teste de fumaça do painel na JVM (sem emulador): abre a DiagnosticsActivity real,
 * espera a coleta terminar, percorre todas as abas e salva uma captura de cada uma.
 *
 * Simula o Android 8.1 (API 27) numa tela de 1280 × 720 px nas duas densidades
 * mais prováveis da central. As capturas ficam em app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27])
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<DiagnosticsActivity>()

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun `todas as abas renderizam em 1280x720 a 160 dpi`() = walkThroughTabs("mdpi")

    @Test
    @Config(qualifiers = "w853dp-h480dp-land-hdpi")
    fun `todas as abas renderizam em 1280x720 a 240 dpi`() = walkThroughTabs("hdpi")

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun `botao abre a tela de opcoes do desenvolvedor`() {
        waitForReport()
        compose.onAllNodesWithText(DiagnosticTab.APPS.title).onFirst().performClick()
        compose.onNodeWithText("Abrir opções do desenvolvedor").performClick()
        val started = Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, started?.action)
    }

    private fun waitForReport() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Nível de API real (SDK_INT)").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun walkThroughTabs(densityName: String) {
        waitForReport()
        DiagnosticTab.entries.forEachIndexed { index, tab ->
            // O título da aba aparece na barra lateral (primeiro nó) e no topo quando selecionada.
            compose.onAllNodesWithText(tab.title).onFirst().performClick()
            compose.waitForIdle()
            assertTrue(
                "aba ${tab.title} sem conteúdo",
                compose.onAllNodesWithText(tab.title).fetchSemanticsNodes().size >= 2,
            )
            val prefix = "build/outputs/roborazzi/${densityName}_${index + 1}_${tab.name.lowercase()}"
            compose.onRoot().captureRoboImage("$prefix.png")
            if (tab == DiagnosticTab.CALIBRATION) {
                // A aba é mais alta que a tela: captura também os blocos de baixo.
                listOf("Tamanhos de texto", "Nenhum toque registrado ainda.").forEachIndexed { part, block ->
                    compose.onNodeWithText(block).performScrollTo()
                    compose.waitForIdle()
                    compose.onRoot().captureRoboImage("${prefix}_${part + 2}.png")
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun `diagnostico nao pula quando o CarPlay esconde a barra de status`() {
        val content = compose.activity.findViewById<android.view.View>(android.R.id.content)
        fun insets(visibleTop: Int, stableTop: Int): android.view.WindowInsets = ReflectionHelpers.callConstructor(
            android.view.WindowInsets::class.java,
            ClassParameter.from(android.graphics.Rect::class.java, android.graphics.Rect(0, visibleTop, 0, 0)),
            ClassParameter.from(android.graphics.Rect::class.java, null),
            ClassParameter.from(android.graphics.Rect::class.java, android.graphics.Rect(0, stableTop, 0, 0)),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
        content.dispatchApplyWindowInsets(insets(visibleTop = 65, stableTop = 65))
        assertEquals(65, content.paddingTop)
        // Saindo do CarPlay direto para o diagnóstico: o primeiro quadro chega sem a barra.
        content.dispatchApplyWindowInsets(insets(visibleTop = 0, stableTop = 65))
        assertEquals("O conteúdo fica no mesmo lugar", 65, content.paddingTop)
    }
}
