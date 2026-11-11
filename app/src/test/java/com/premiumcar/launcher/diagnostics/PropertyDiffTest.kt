package com.premiumcar.launcher.diagnostics

import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.PropertyDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyDiffTest {

    @Test
    fun `registra mudancas, novas e removidas, ignorando contadores`() {
        // Valores reais dos relatórios da central: antes e com o CarPlay ativo.
        val before = mapOf(
            "sys.usb.config" to "mtp",
            "sys.top.activity" to "com.premiumcar.launcher/.app.MainActivity",
            "log.mcud.statistics" to "rx:1165,drop:0,err:0",
            "init.svc.hostapd" to "stopped",
        )
        val after = mapOf(
            "sys.usb.config" to "none",
            "sys.top.activity" to "com.premiumcar.launcher/.app.MainActivity",
            "log.mcud.statistics" to "rx:5292,drop:0,err:0",
            "rw.cp.link.state" to "1",
        )
        assertEquals(
            listOf(
                "removida: init.svc.hostapd (era stopped)",
                "nova: rw.cp.link.state = 1",
                "sys.usb.config: mtp → none",
            ),
            PropertyDiff.diff(before, after),
        )
    }

    @Test
    fun `valores vazios ficam legiveis`() {
        assertEquals(listOf("a.b: (vazio) → 1"), PropertyDiff.diff(mapOf("a.b" to ""), mapOf("a.b" to "1")))
    }

    @Test
    fun `segundos com virgula decimal`() {
        assertEquals("12,3 s", CarTestLog.formatSeconds(12_345))
    }
}
