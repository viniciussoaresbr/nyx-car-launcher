package com.premiumcar.launcher.launcher

import android.app.Activity
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import com.premiumcar.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

/** Tela de espera do CarPlay do Nyx Car (B1, 0.4.7): visual em 1280 × 720 e a janela sobreposta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "pt-rBR-w1280dp-h720dp-land-mdpi")
class CarPlayWaitScreenTest {

    @Test
    fun `cada estado gera captura`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = CarPlayWaitScreen.createView(ContextThemeWrapper(activity, R.style.Theme_NyxCar))
        activity.setContentView(view)
        CarPlayWaitState.entries.forEach { state ->
            CarPlayWaitScreen.bind(view, state)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1280, 720)
            view.captureRoboImage("build/outputs/roborazzi/carplay_wait_${state.name.lowercase()}.png")
        }
    }

    @Test
    fun `janela sobreposta so com a permissao`() {
        val app = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(CarPlayWaitScreen(app).canShow())

        ShadowSettings.setCanDrawOverlays(true)
        val screen = CarPlayWaitScreen(app)
        assertTrue(screen.canShow())
        screen.show(CarPlayWaitState.CONNECTING_CABLE, onTap = {})
        assertTrue(screen.visible)
        screen.show(CarPlayWaitState.PREPARING, onTap = {})
        assertTrue("Atualiza a mesma janela", screen.visible)
        screen.park()
        assertFalse("Reservada não está à vista", screen.visible)
        assertTrue(screen.parked)
        screen.show(CarPlayWaitState.DISCONNECTED, onTap = {})
        assertTrue("Volta da reserva", screen.visible)
        screen.hide()
        assertFalse(screen.visible)
        assertFalse(screen.parked)
        assertEquals("Preparando a tela do CarPlay…", app.getString(R.string.carplay_wait_status_preparing))
    }
}
