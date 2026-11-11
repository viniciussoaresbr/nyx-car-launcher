package com.premiumcar.launcher.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    // Formato típico de /proc/cpuinfo em kernels 3.x ARMv7 (valores ilustrativos).
    private val armCpuinfo = """
        Processor	: ARMv7 Processor rev 3 (v7l)
        processor	: 0
        BogoMIPS	: 26.00
        Features	: half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 idiva idivt
        CPU implementer	: 0x41
        CPU architecture: 7
        CPU part	: 0xc07
        processor	: 1
        CPU part	: 0xc07
        processor	: 2
        CPU part	: 0xc07
        processor	: 3
        CPU part	: 0xc07
        Hardware	: EXEMPLO
    """.trimIndent()

    @Test
    fun `cpuinfo ARM identifica nucleos pelo codigo da peca`() {
        val cpu = CpuInfo.parse(armCpuinfo)
        assertEquals("EXEMPLO", cpu.hardware)
        assertEquals("ARMv7 Processor rev 3 (v7l)", cpu.modelName)
        assertEquals("7", cpu.architecture)
        assertEquals("4× Cortex-A7 (0xc07)", cpu.coreSummary())
    }

    @Test
    fun `cpuinfo sem pecas ARM nao inventa nucleos`() {
        val cpu = CpuInfo.parse("model name\t: Intel(R) Xeon(R)\nflags\t: fpu vme")
        assertEquals("Intel(R) Xeon(R)", cpu.modelName)
        assertNull(cpu.coreSummary())
    }

    @Test
    fun `getprop e lido e identificadores sao omitidos`() {
        val output = """
            [ro.build.version.sdk]: [27]
            [ro.build.version.release]: [13]
            [ro.product.model]: [EXEMPLO]
            [ro.serialno]: [ABC123]
            [ro.boot.serialno]: [ABC123]
            [persist.sys.wifi.mac]: [00:11:22:33:44:55]
            [net.hostname]: [android-1234567890abcdef]
            [vendor.bt.addr]: [aa:bb:cc:dd:ee:ff]
            [ro.custom.algo]: [00:11:22:33:44:55]
            [dalvik.vm.heapsize]: [256m]
            linha inválida
        """.trimIndent()
        val snapshot = SystemProperties.parse(output)
        assertEquals("27", snapshot["ro.build.version.sdk"])
        assertEquals("13", snapshot["ro.build.version.release"])
        assertEquals("256m", snapshot["dalvik.vm.heapsize"])
        assertNull(snapshot["ro.serialno"])
        assertNull(snapshot["ro.boot.serialno"])
        assertNull(snapshot["persist.sys.wifi.mac"])
        assertNull(snapshot["net.hostname"])
        assertNull(snapshot["vendor.bt.addr"])
        assertNull("valor com formato de MAC é omitido", snapshot["ro.custom.algo"])
        assertEquals(6, snapshot.omittedCount)
    }

    @Test
    fun `filtro nao remove chaves comuns por engano`() {
        assertFalse(SystemProperties.isSensitive("ro.build.version.codename", "REL"))
        assertFalse(SystemProperties.isSensitive("ro.telephony.default_network", "9"))
        assertFalse(SystemProperties.isSensitive("ro.build.keys", "release-keys"))
        assertTrue(SystemProperties.isSensitive("persist.vendor.sn", "XYZ"))
        assertTrue(SystemProperties.isSensitive("ro.boot.cid", "11"))
    }

    @Test
    fun `versao declarada e comparada com a API real`() {
        assertFalse(AndroidVersions.isConsistent("13", 27))
        assertTrue(AndroidVersions.isConsistent("8.1.0", 27))
        assertTrue(AndroidVersions.isConsistent("13", 33))
        assertTrue(AndroidVersions.isConsistent("12", 32))
        assertEquals("8.1", AndroidVersions.nameFor(27))
    }

    @Test
    fun `meminfo e convertido em mapa de kB`() {
        val map = SystemInfoCollector.parseMeminfo("MemTotal:        2008760 kB\nSwapTotal:       1004376 kB\n")
        assertEquals(2008760L, map["MemTotal"])
        assertEquals(1004376L, map["SwapTotal"])
    }

    @Test
    fun `categorias de apps usam fragmentos e palavras inteiras`() {
        assertTrue(AppCategory.PROJECTION.matches("ZLINK", "com.exemplo.zlink"))
        assertTrue(AppCategory.BLUETOOTH.matches("BT Music", "com.exemplo.btmusic"))
        assertTrue(AppCategory.MUSIC.matches("BT Music", "com.exemplo.btmusic"))
        assertTrue(AppCategory.RADIO.matches("FM", "com.exemplo.radio"))
        assertFalse(AppCategory.NAVIGATION.matches("Indigo", "com.exemplo.indigo"))
        assertFalse(AppCategory.MUSIC.matches("EngineerMode", "com.mediatek.engineermode"))
        assertFalse(AppCategory.BLUETOOTH.matches("Bootstrap", "com.exemplo.bootstrap"))
    }

    @Test
    fun `relogio perto da data do firmware parece nao acertado`() {
        val hour = 60L * 60 * 1000
        val build = 1_776_394_575_000L // ro.build.date.utc da central (17/04/2026)
        // 11/11/2026: a central voltou para 16/04 21:01 (3 horas antes do build).
        assertTrue(SystemInfoCollector.clockLooksReset(build - 3 * hour, build))
        assertTrue(SystemInfoCollector.clockLooksReset(build + 3 * hour, build))
        assertFalse(SystemInfoCollector.clockLooksReset(1_794_427_911_000L, build)) // 11/11/2026, hora certa
    }
}
