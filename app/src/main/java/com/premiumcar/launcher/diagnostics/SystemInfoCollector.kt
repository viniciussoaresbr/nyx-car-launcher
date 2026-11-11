package com.premiumcar.launcher.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Versão do sistema, data e hora, aparelho, processador, memória, gráficos e armazenamento. */
object SystemInfoCollector {

    fun collect(context: Context, props: PropertySnapshot): List<InfoSection> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return listOf(
            version(am, props),
            clock(context),
            device(props),
            processor(),
            memory(am, props),
            graphics(context.packageManager, am, props),
            storage(),
        )
    }

    private fun version(am: ActivityManager, props: PropertySnapshot) = section("Versão do sistema") {
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        val expected = AndroidVersions.nameFor(sdk)
        add("Android declarado", release)
        highlight("Nível de API real (SDK_INT)", "$sdk — corresponde ao Android $expected")
        if (AndroidVersions.isConsistent(release, sdk)) {
            add("Consistência", "versão declarada e API real coincidem")
        } else {
            warn("Consistência", "INCOERENTE: o sistema se apresenta como Android $release, mas a API real é $sdk (Android $expected)")
        }
        add("API de lançamento (first_api_level)", props["ro.product.first_api_level"])
        add("Patch de segurança", Build.VERSION.SECURITY_PATCH)
        highlight("Kernel", System.getProperty("os.version"))
        add("/proc/version", readText("/proc/version")?.trim())
        add("Máquina virtual", "ART ${System.getProperty("java.vm.version")}")
        add("Treble / VNDK", "${props["ro.treble.enabled"] ?: "—"} / ${props["ro.vndk.version"] ?: "—"}")
        val lowRam = am.isLowRamDevice
        add("Modo low-RAM (Android Go)", if (lowRam) "sim" else "não", if (lowRam) EntryLevel.WARNING else EntryLevel.NORMAL)
        add("Build", Build.DISPLAY)
        add("Fingerprint", Build.FINGERPRINT)
        add("Tipo / tags", "${Build.TYPE} / ${Build.TAGS}")
    }

    /**
     * Relógio da central (0.4.3). Depois de uma reinicialização completa (11/11/2026), o relógio
     * voltou para a data do firmware e ficou errado por cerca de 3 horas, até ser acertado.
     */
    private fun clock(context: Context) = section("Data e hora") {
        val resolver = context.contentResolver
        add("Data e hora automáticas (pela rede)", onOff(Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, -1)))
        add("Fuso horário automático", onOff(Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, -1)))
        add("Fuso horário", TimeZone.getDefault().id)
        add("Sistema ligado há", "${SystemClock.elapsedRealtime() / 60_000} min")
        val firmware = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(Build.TIME))
        if (clockLooksReset(System.currentTimeMillis(), Build.TIME)) {
            warn("Relógio", "parece estar na data do firmware ($firmware): ainda não foi acertado desde que a central partiu do zero")
        } else {
            add("Relógio", "acertado (posterior à data do firmware, $firmware)")
        }
    }

    private fun onOff(value: Int) = when (value) {
        1 -> "ligado"
        0 -> "desligado"
        else -> "não foi possível consultar"
    }

    /**
     * Sem hora certa, o Android parte da data do firmware. Um relógio a menos de [CLOCK_RESET_WINDOW_MS]
     * dela (antes ou depois) provavelmente ainda não foi acertado.
     */
    internal fun clockLooksReset(now: Long, buildTime: Long): Boolean = now < buildTime + CLOCK_RESET_WINDOW_MS

    private const val CLOCK_RESET_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private fun device(props: PropertySnapshot) = section("Aparelho") {
        add("Fabricante / marca", "${Build.MANUFACTURER} / ${Build.BRAND}")
        add("Modelo", Build.MODEL)
        add("Produto / dispositivo", "${Build.PRODUCT} / ${Build.DEVICE}")
        add("Placa / hardware", "${Build.BOARD} / ${Build.HARDWARE}")
        highlight("Plataforma (ro.board.platform)", props["ro.board.platform"])
        if (Build.VERSION.SDK_INT >= 31) add("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        add("Característica do build", props["ro.build.characteristics"])
        highlight("ABIs suportadas", Build.SUPPORTED_ABIS.joinToString())
        add("Executa apps de 64 bits", if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) "não (somente 32 bits)" else "sim")
    }

    private fun processor() = section("Processador") {
        val cpu = CpuInfo.parse(readText("/proc/cpuinfo").orEmpty())
        add("Núcleos ativos", Runtime.getRuntime().availableProcessors())
        add("Núcleos existentes (sysfs)", readText("/sys/devices/system/cpu/possible")?.trim())
        highlight("Núcleos identificados", cpu.coreSummary())
        add("Hardware (cpuinfo)", cpu.hardware)
        add("Modelo (cpuinfo)", cpu.modelName)
        add("Arquitetura", cpu.architecture)
        add("Recursos", cpu.features?.take(240))
        highlight("Frequência máxima", maxFrequencies())
    }

    private fun maxFrequencies(): String? {
        val values = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { index ->
            readText("/sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq")?.trim()?.toLongOrNull()
        }
        return values.distinct().joinToString(" / ") { "${it / 1000} MHz" }.ifEmpty { null }
    }

    private fun memory(am: ActivityManager, props: PropertySnapshot) = section("Memória") {
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val meminfo = parseMeminfo(readText("/proc/meminfo").orEmpty())
        highlight("RAM total", Fmt.bytes(info.totalMem))
        add("Disponível agora", Fmt.bytes(info.availMem))
        add("Limiar de memória baixa", Fmt.bytes(info.threshold))
        add("Sistema em memória baixa agora", if (info.lowMemory) "sim" else "não")
        add("Swap / zRAM", meminfo["SwapTotal"]?.let { Fmt.bytes(it * 1024) })
        highlight("Heap por app (memoryClass)", "${am.memoryClass} MB · grande: ${am.largeMemoryClass} MB")
        add("Heap máximo deste processo", Fmt.bytes(Runtime.getRuntime().maxMemory()))
        add("dalvik.vm.heapgrowthlimit / heapsize", "${props["dalvik.vm.heapgrowthlimit"] ?: "—"} / ${props["dalvik.vm.heapsize"] ?: "—"}")
    }

    private fun graphics(pm: PackageManager, am: ActivityManager, props: PropertySnapshot) = section("Gráficos") {
        val gpu = GpuInfoReader.read()
        add("OpenGL ES (sistema)", am.deviceConfigurationInfo.glEsVersion)
        highlight("GPU (GL_RENDERER)", gpu?.renderer)
        add("Fabricante da GPU", gpu?.vendor)
        add("Versão do driver GL", gpu?.version)
        add("Vulkan", vulkanVersion(pm) ?: "não suportado")
        add("Renderizador da interface (HWUI)", props["debug.hwui.renderer"] ?: "padrão do sistema")
    }

    private fun vulkanVersion(pm: PackageManager): String? {
        if (Build.VERSION.SDK_INT < 24) return null
        val feature = pm.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?: return null
        val v = feature.version
        return "${v ushr 22}.${(v ushr 12) and 0x3ff}.${v and 0xfff}"
    }

    private fun storage() = section("Armazenamento interno") {
        val stat = StatFs(Environment.getDataDirectory().path)
        add("Total", Fmt.bytes(stat.totalBytes))
        add("Livre", Fmt.bytes(stat.availableBytes))
    }

    internal fun parseMeminfo(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val key = line.substringBefore(':', "").trim()
        val value = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
        if (key.isEmpty() || value == null) null else key to value
    }.toMap()

    internal fun readText(path: String): String? = try {
        File(path).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }
}
