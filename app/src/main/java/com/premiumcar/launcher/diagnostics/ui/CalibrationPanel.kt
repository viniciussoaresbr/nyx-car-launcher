package com.premiumcar.launcher.diagnostics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premiumcar.launcher.design.PcColors
import kotlin.math.roundToInt

/**
 * Amostras para avaliar o painel real antes do Design System (Fase 2):
 * pretos, contraste, cor de destaque, tamanhos de texto e precisão do toque.
 */
@Composable
internal fun CalibrationPanel(onOpenCatalog: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Block(
            "Design System (Fase 2)",
            "Abre o catálogo com todas as cores, textos, ícones e componentes do launcher, em todos os estados.",
        ) {
            Button(onClick = onOpenCatalog) { Text("Abrir catálogo de componentes") }
        }
        Block(
            "Degraus de cinza próximos do preto",
            "Os 16 quadrados devem ser distinguíveis. Se os primeiros parecerem iguais, o painel esconde " +
                "detalhes escuros e as superfícies da interface precisarão ser mais claras.",
        ) { GrayRamp() }
        Block(
            "Tons da interface (aprovados)",
            "Fundo, superfícies e destaque do Design System. Avalie com o painel na posição de uso, inclusive de dia.",
        ) { TokenRow() }
        Block("Candidatas a cor de destaque", "Qual turquesa/ciano fica mais legível e agradável neste painel?") {
            AccentRow()
        }
        Block("Tamanhos de texto", "A partir de qual tamanho o texto é confortável de ler na posição de dirigir?") {
            TextScale()
        }
        Block("Alvos de toque", "Toque em cada quadrado: o contador confirma se o toque foi reconhecido.") {
            TouchTargets()
        }
        Block(
            "Precisão do toque",
            "Toque no centro de cada alvo. O ponto amarelo mostra onde o sistema registrou o toque.",
        ) { TouchAccuracyPanel() }
    }
}

@Composable
private fun Block(title: String, description: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, color = PcColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = PcColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun GrayRamp() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (step in 0 until 16) {
            val value = step * 4
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(value, value, value)),
                )
                Text("%02X".format(value), color = PcColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private val tokens = listOf(
    "Fundo" to PcColors.Background,
    "Superfície 1" to PcColors.Surface,
    "Superfície 2" to PcColors.SurfaceHigh,
    "Superfície 3" to PcColors.SurfaceHighest,
    "Borda" to PcColors.Outline,
    "Destaque" to PcColors.Accent,
)

private val TokenShape = RoundedCornerShape(12.dp)

@Composable
private fun TokenRow() {
    // IntrinsicSize.Max deixa todos os cartões com a altura do mais alto.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tokens.forEach { (name, color) ->
            val onAccent = color == PcColors.Accent
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 104.dp)
                    .background(color, TokenShape)
                    // Contorno para que o cartão "Fundo" (mesma cor da tela) fique visível.
                    .border(1.dp, PcColors.Outline, TokenShape)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(name, color = if (onAccent) PcColors.OnAccent else PcColors.TextPrimary, fontSize = 15.sp)
                Text("Secundário", color = if (onAccent) PcColors.OnAccent else PcColors.TextSecondary, fontSize = 13.sp)
                Text(hex(color), color = if (onAccent) PcColors.OnAccent else PcColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private val accentCandidates = listOf(
    Color(0xFF2CC8D8),
    Color(0xFF00B4C8),
    Color(0xFF3DDBD9),
    Color(0xFF4FC3F7),
)

@Composable
private fun AccentRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        accentCandidates.forEachIndexed { index, accent ->
            Column(
                Modifier
                    .weight(1f)
                    .background(PcColors.Surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ativo", color = PcColors.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Navegação", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${index + 1} · ${hex(accent)}" + if (index == 0) " (proposta)" else "",
                    color = PcColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private val textSizes = listOf(12, 14, 16, 18, 20, 24, 28, 32, 40, 48, 64, 96)

@Composable
private fun TextScale() {
    val density = LocalDensity.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        textSizes.forEach { size ->
            val px = with(density) { size.sp.toPx() }.roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$size sp · $px px",
                    color = PcColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    if (size >= 40) "15:48" else "Navegação · Música · 15:48",
                    color = PcColors.TextPrimary,
                    fontSize = size.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (size < 40) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "secundário",
                        color = PcColors.TextSecondary,
                        fontSize = size.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

private val touchTargetSizes = listOf(40, 48, 56, 64, 72, 96)

@Composable
private fun TouchTargets() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        touchTargetSizes.forEach { size ->
            key(size) {
                var taps by remember { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .size(size.dp)
                        .background(PcColors.SurfaceHighest, RoundedCornerShape(10.dp))
                        .clickable { taps++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$size\n$taps",
                        color = PcColors.TextPrimary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchAccuracyPanel() {
    var lastTap by remember { mutableStateOf<Offset?>(null) }
    var deviation by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(PcColors.Surface, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val area = Size(size.width.toFloat(), size.height.toFloat())
                        val nearest = targets(area, TARGET_MARGIN.toPx()).minBy { (it - tap).getDistance() }
                        lastTap = tap
                        deviation = (nearest - tap).getDistance()
                    }
                },
        ) {
            val radius = 18.dp.toPx()
            val arm = 28.dp.toPx()
            val stroke = 2.dp.toPx()
            targets(size, TARGET_MARGIN.toPx()).forEach { center ->
                drawCircle(PcColors.Accent, radius = radius, center = center, style = Stroke(stroke))
                drawLine(PcColors.Accent, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth = 1.dp.toPx())
                drawLine(PcColors.Accent, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth = 1.dp.toPx())
            }
            lastTap?.let { drawCircle(PcColors.Warning, radius = 7.dp.toPx(), center = it) }
        }
        val tap = lastTap
        Text(
            if (tap == null) {
                "Nenhum toque registrado ainda."
            } else {
                val deviationDp = with(density) { deviation.toDp().value }.roundToInt()
                "Último toque: x ${tap.x.roundToInt()} px, y ${tap.y.roundToInt()} px · " +
                    "desvio do alvo mais próximo: ${deviation.roundToInt()} px ($deviationDp dp)"
            },
            color = PcColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val TARGET_MARGIN = 36.dp

/** Quatro cantos e o centro da área de teste. */
private fun targets(area: Size, margin: Float) = listOf(
    Offset(margin, margin),
    Offset(area.width - margin, margin),
    Offset(area.width / 2, area.height / 2),
    Offset(margin, area.height - margin),
    Offset(area.width - margin, area.height - margin),
)

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
