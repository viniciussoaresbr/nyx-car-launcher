package com.premiumcar.launcher.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premiumcar.launcher.design.PcColors
import com.premiumcar.launcher.diagnostics.PerformanceSections
import com.premiumcar.launcher.perf.BenchmarkResults
import com.premiumcar.launcher.perf.BenchmarkSpec
import com.premiumcar.launcher.perf.MemorySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PerformancePanel(onRunCompose: () -> Unit, onRunViews: () -> Unit) {
    val context = LocalContext.current
    var memory by remember { mutableStateOf<MemorySnapshot?>(null) }
    var refreshCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshCount) {
        memory = withContext(Dispatchers.Default) { MemorySnapshot.capture() }
    }
    val results = BenchmarkResults.all
    val sections = remember(memory, results.size) {
        PerformanceSections.build(context, memory, results.toList())
    }
    SectionList(sections) {
        item {
            BenchmarkControls(
                onRunCompose = onRunCompose,
                onRunViews = onRunViews,
                onRefreshMemory = { refreshCount++ },
            )
        }
    }
}

@Composable
private fun BenchmarkControls(onRunCompose: () -> Unit, onRunViews: () -> Unit, onRefreshMemory: () -> Unit) {
    val seconds = 2 * BenchmarkSpec.DIRECTION_MILLIS / 1000
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(PcColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            "Comparativo de fluidez: Compose × Views",
            color = PcColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Cada teste abre um processo novo, carrega os ícones instalados e rola uma grade de " +
                "${BenchmarkSpec.ITEM_COUNT} itens por cerca de $seconds s. Não toque na tela durante o teste. " +
                "Execute cada motor 3 vezes, alternando entre eles.",
            color = PcColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRunCompose) { Text("Testar Compose") }
            Button(onClick = onRunViews) { Text("Testar Views") }
            OutlinedButton(onClick = onRefreshMemory) { Text("Atualizar memória") }
        }
    }
}
