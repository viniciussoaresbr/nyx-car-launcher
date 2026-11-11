package com.premiumcar.launcher.perf

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premiumcar.launcher.design.PcColors
import com.premiumcar.launcher.design.NyxCarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Teste de fluidez em Compose: grade de ícones rolada automaticamente.
 * Roda em processo próprio (`:bench_compose`) para medir a partida a frio.
 */
class ComposeBenchmarkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coldStart = StartupTimeline.markActivityCreated("Teste de fluidez (Compose)")
        if (coldStart) StartupTimeline.trackFirstFrame(window.decorView)
        setContent {
            NyxCarTheme {
                ComposeBenchmarkScreen { loaded, frames ->
                    finishBenchmark(ENGINE, coldStart, loaded, frames)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Encerra o processo de teste para que a próxima execução também seja a frio.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        const val ENGINE = "Compose"
    }
}

private class Tile(val label: String, val icon: ImageBitmap)

private val TileShape = RoundedCornerShape(BenchmarkSpec.CORNER_DP.dp)

@Composable
private fun ComposeBenchmarkScreen(onFinished: (LoadedItems, LongArray) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    var loaded by remember { mutableStateOf<LoadedItems?>(null) }
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val iconPx = with(density) { BenchmarkSpec.ICON_SIZE_DP.dp.roundToPx() }
        val result = withContext(Dispatchers.IO) { BenchmarkData.load(context.applicationContext, iconPx) }
        // Os itens repetem os mesmos ícones: converte cada Bitmap uma única vez.
        val images = HashMap<Bitmap, ImageBitmap>()
        tiles = result.items.map { Tile(it.label, images.getOrPut(it.icon) { it.icon.asImageBitmap() }) }
        loaded = result
    }

    LaunchedEffect(loaded) {
        val items = loaded ?: return@LaunchedEffect
        delay(BenchmarkSpec.SETTLE_MILLIS)
        running = true
        val recorder = FrameRecorder().apply { start() }
        val pxPerSecond = with(density) { BenchmarkSpec.SPEED_DP_PER_SECOND.dp.toPx() }
        gridState.autoScroll(pxPerSecond, BenchmarkSpec.DIRECTION_MILLIS, direction = 1f)
        gridState.autoScroll(pxPerSecond, BenchmarkSpec.DIRECTION_MILLIS, direction = -1f)
        onFinished(items, recorder.stop())
    }

    Column(Modifier.fillMaxSize().background(PcColors.Background)) {
        Text(
            text = if (running) {
                "Teste de fluidez · Compose — rolando, não toque na tela"
            } else {
                "Teste de fluidez · Compose — carregando ícones…"
            },
            color = PcColors.TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.padding(16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(BenchmarkSpec.TILE_MIN_WIDTH_DP.dp),
            state = gridState,
            contentPadding = PaddingValues(BenchmarkSpec.PADDING_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(BenchmarkSpec.SPACING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(BenchmarkSpec.SPACING_DP.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tiles.size) { index -> BenchTile(tiles[index]) }
        }
    }
}

@Composable
private fun BenchTile(tile: Tile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(BenchmarkSpec.TILE_HEIGHT_DP.dp)
            .background(PcColors.Surface, TileShape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = tile.icon,
            contentDescription = null,
            modifier = Modifier.size(BenchmarkSpec.ICON_SIZE_DP.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tile.label,
            color = PcColors.TextPrimary,
            fontSize = BenchmarkSpec.LABEL_SP.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rola a grade em velocidade constante, quadro a quadro, durante [durationMillis]. */
private suspend fun LazyGridState.autoScroll(pxPerSecond: Float, durationMillis: Long, direction: Float) {
    scroll {
        val start = withFrameNanos { it }
        val end = start + durationMillis * 1_000_000
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            if (now >= end) break
            scrollBy(pxPerSecond * (now - last) / 1_000_000_000f * direction)
            last = now
        }
    }
}
