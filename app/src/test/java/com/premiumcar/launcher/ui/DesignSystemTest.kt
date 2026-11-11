package com.premiumcar.launcher.ui

import android.os.Looper
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import com.premiumcar.launcher.R
import com.premiumcar.launcher.design.PcPalette
import com.premiumcar.launcher.ui.catalog.DesignCatalogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "pt-rBR-w1280dp-h655dp-land-mdpi")
class DesignSystemTest {

    @Test
    fun `PcPalette espelha exatamente colors xml`() {
        val context = RuntimeEnvironment.getApplication()
        val mirrored = mapOf(
            R.color.pc_background to PcPalette.BACKGROUND,
            R.color.pc_surface to PcPalette.SURFACE,
            R.color.pc_surface_high to PcPalette.SURFACE_HIGH,
            R.color.pc_surface_highest to PcPalette.SURFACE_HIGHEST,
            R.color.pc_outline to PcPalette.OUTLINE,
            R.color.pc_text_primary to PcPalette.TEXT_PRIMARY,
            R.color.pc_text_secondary to PcPalette.TEXT_SECONDARY,
            R.color.pc_text_disabled to PcPalette.TEXT_DISABLED,
            R.color.pc_accent to PcPalette.ACCENT,
            R.color.pc_accent_dim to PcPalette.ACCENT_DIM,
            R.color.pc_accent_container to PcPalette.ACCENT_CONTAINER,
            R.color.pc_on_accent to PcPalette.ON_ACCENT,
            R.color.pc_warning to PcPalette.WARNING,
            R.color.pc_error to PcPalette.ERROR,
            R.color.pc_success to PcPalette.SUCCESS,
        )
        mirrored.forEach { (resource, value) ->
            assertEquals(context.resources.getResourceEntryName(resource), value, context.getColor(resource))
        }
    }

    @Test
    fun `catalogo renderiza todos os componentes`() {
        val activity = Robolectric.buildActivity(DesignCatalogActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val content = activity.findViewById<View>(R.id.catalog_content)
        assertTrue("o catálogo deveria ser mais alto que a tela", content.height > 655)
        content.captureRoboImage("build/outputs/roborazzi/design_catalog.png")
    }
}
