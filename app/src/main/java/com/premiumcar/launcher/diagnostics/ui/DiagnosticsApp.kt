package com.premiumcar.launcher.diagnostics.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.premiumcar.launcher.design.PcColors
import com.premiumcar.launcher.diagnostics.AppInfo
import com.premiumcar.launcher.diagnostics.DeveloperOptions
import com.premiumcar.launcher.diagnostics.DiagnosticReport
import com.premiumcar.launcher.diagnostics.DiagnosticTab
import com.premiumcar.launcher.diagnostics.DiagnosticsCollector
import com.premiumcar.launcher.diagnostics.PerformanceSections
import com.premiumcar.launcher.diagnostics.ReportExporter
import com.premiumcar.launcher.perf.BenchmarkResult
import com.premiumcar.launcher.perf.BenchmarkResults
import com.premiumcar.launcher.perf.ComposeBenchmarkActivity
import com.premiumcar.launcher.perf.MemorySnapshot
import com.premiumcar.launcher.perf.StartupTimeline
import com.premiumcar.launcher.perf.ViewBenchmarkActivity
import com.premiumcar.launcher.ui.catalog.DesignCatalogActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val FIRST_FRAME_TIMEOUT_MS = 3_000L

/** Painel de diagnóstico (Fase 1): coleta, exibe e exporta as informações da central. */
@Composable
fun DiagnosticsApp(activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember { AppInfo.versionName(context) }
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    var tab by rememberSaveable { mutableStateOf(DiagnosticTab.SYSTEM) }
    var status by remember { mutableStateOf<String?>(null) }
    var collectCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(collectCount) {
        if (collectCount == 0) {
            // Coleta só depois do primeiro quadro para não distorcer a medição de inicialização.
            // O limite garante que a coleta aconteça mesmo se o firmware nunca sinalizar o quadro.
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { StartupTimeline.awaitFirstFrame() }
        }
        report = DiagnosticsCollector.collect(activity)
        if (collectCount > 0) status = "Informações atualizadas."
    }

    /** Monta o texto do relatório; chamar fora da thread principal. */
    fun buildText(current: DiagnosticReport, results: List<BenchmarkResult>): String {
        val performance = PerformanceSections.build(context, MemorySnapshot.capture(), results)
        return ReportExporter.buildText(context, current, performance)
    }

    fun failure(error: Throwable) = "Falha ao salvar: ${error.message ?: error}"

    /** Relatório da tela atual, ou nulo (com aviso) se a coleta ainda não terminou. */
    fun currentReport(): DiagnosticReport? = report.also {
        if (it == null) status = "Aguarde a coleta das informações e tente de novo."
    }

    fun saveToDownloads() {
        val current = currentReport() ?: return
        val results = BenchmarkResults.all.toList()
        status = "Salvando relatório…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val saved = ReportExporter.saveToDownloads(context, buildText(current, results))
                    "Relatório salvo em ${saved.where} (${ReportExporter.formatSize(saved.bytes)})."
                }.getOrElse(::failure)
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveToDownloads() else status = "Permissão negada. Use \"Salvar em…\" ou fotografe as telas."
    }
    // O texto é montado ANTES de abrir o seletor e guardado num arquivo do app: se o Android
    // encerrar o app enquanto o seletor está aberto, a gravação ainda acontece na volta.
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            ReportExporter.clearPending(context)
            status = "Salvamento cancelado."
            return@rememberLauncherForActivityResult
        }
        status = "Salvando relatório…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ReportExporter.takePending(context)
                        ?: error("o relatório preparado se perdeu. Apague o arquivo vazio e toque em \"Salvar em…\" de novo")
                    val bytes = ReportExporter.writeTo(context, uri, text)
                    ReportExporter.clearPending(context)
                    "Relatório salvo e conferido (${ReportExporter.formatSize(bytes)}). Já pode remover o pendrive."
                }.getOrElse(::failure)
            }
        }
    }
    fun saveAs() {
        val current = currentReport() ?: return
        val results = BenchmarkResults.all.toList()
        status = "Preparando relatório…"
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching { ReportExporter.storePending(context, buildText(current, results)) }
            }
            prepared.exceptionOrNull()?.let {
                status = failure(it)
                return@launch
            }
            status = try {
                createDocument.launch(ReportExporter.fileName())
                "Escolha onde salvar (ex.: o pendrive) e aguarde a confirmação aqui."
            } catch (e: ActivityNotFoundException) {
                ReportExporter.clearPending(context)
                "Seletor de arquivos indisponível neste sistema. Use \"Salvar em Download\"."
            }
        }
    }
    val benchmark = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val parsed = BenchmarkResult.fromBundle(result.data?.extras)
        status = if (parsed != null) {
            BenchmarkResults.all += parsed
            "Teste ${parsed.engine} concluído."
        } else {
            "Teste interrompido — nenhum resultado."
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(PcColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SideRail(selected = tab, version = version, onSelect = { tab = it })
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(
                title = tab.title,
                status = status,
                actionsEnabled = report != null,
                onSaveToDownloads = {
                    if (ReportExporter.needsLegacyPermission(context)) {
                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveToDownloads()
                    }
                },
                onSaveAs = ::saveAs,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DiagnosticTab.PERFORMANCE -> PerformancePanel(
                        onRunCompose = { benchmark.launch(Intent(context, ComposeBenchmarkActivity::class.java)) },
                        onRunViews = { benchmark.launch(Intent(context, ViewBenchmarkActivity::class.java)) },
                    )
                    DiagnosticTab.CALIBRATION -> CalibrationPanel(
                        onOpenCatalog = { context.startActivity(Intent(context, DesignCatalogActivity::class.java)) },
                    )
                    DiagnosticTab.CAR_TESTS -> CarTestsPanel(
                        sections = report?.sections?.get(tab),
                        onRefresh = {
                            status = "Atualizando…"
                            collectCount++
                        },
                        onStatus = { status = it },
                    )
                    DiagnosticTab.APPS -> {
                        val sections = report?.sections?.get(tab)
                        if (sections == null) {
                            LoadingMessage()
                        } else {
                            SectionList(sections) {
                                item {
                                    DeveloperOptionsCard(
                                        onOpen = { status = DeveloperOptions.open(context) },
                                        onRefresh = {
                                            status = "Atualizando…"
                                            collectCount++
                                        },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val sections = report?.sections?.get(tab)
                        if (sections == null) LoadingMessage() else SectionList(sections)
                    }
                }
            }
        }
    }
}
