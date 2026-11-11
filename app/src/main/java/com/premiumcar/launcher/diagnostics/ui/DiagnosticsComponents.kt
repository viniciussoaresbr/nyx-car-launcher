package com.premiumcar.launcher.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premiumcar.launcher.design.PcColors
import com.premiumcar.launcher.diagnostics.DiagnosticTab
import com.premiumcar.launcher.diagnostics.EntryLevel
import com.premiumcar.launcher.diagnostics.InfoEntry
import com.premiumcar.launcher.diagnostics.InfoSection

@Composable
internal fun SideRail(selected: DiagnosticTab, version: String, onSelect: (DiagnosticTab) -> Unit) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(PcColors.Surface)
            .padding(vertical = 20.dp),
    ) {
        Text(
            "Nyx Car",
            color = PcColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Diagnóstico · v$version",
            color = PcColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DiagnosticTab.entries.forEach { tab ->
                RailItem(tab.title, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun RailItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (selected) PcColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (selected) PcColors.Accent else Color.Transparent),
        )
        Text(
            title,
            color = if (selected) PcColors.TextPrimary else PcColors.TextSecondary,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
internal fun TopBar(
    title: String,
    status: String?,
    actionsEnabled: Boolean,
    onSaveToDownloads: () -> Unit,
    onSaveAs: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PcColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (status != null) {
                Text(status, color = PcColors.TextSecondary, fontSize = 14.sp, maxLines = 2)
            }
        }
        Button(onClick = onSaveToDownloads, enabled = actionsEnabled) { Text("Salvar em Download") }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onSaveAs, enabled = actionsEnabled) { Text("Salvar em…") }
    }
}

/** Atalho para a tela escondida de opções do desenvolvedor (necessária para o ADB via Wi-Fi). */
@Composable
internal fun DeveloperOptionsCard(onOpen: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(PcColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            "ADB via Wi-Fi: opções do desenvolvedor",
            color = PcColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Abre a tela padrão do Android, que neste firmware não aparece no menu. Abrir não altera nada. " +
                "Lá, se a chave principal estiver desligada, ligue-a e confirme; depois ligue apenas " +
                "\"Depuração USB\". Não mexa nas demais opções. Ao voltar, toque em \"Atualizar status\".",
            color = PcColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpen) { Text("Abrir opções do desenvolvedor") }
            OutlinedButton(onClick = onRefresh) { Text("Atualizar status") }
        }
    }
}

@Composable
internal fun LoadingMessage() {
    // Texto estático de propósito: nada de animações contínuas (plano, seção 7).
    Text(
        "Coletando informações do sistema…",
        color = PcColors.TextSecondary,
        fontSize = 16.sp,
        modifier = Modifier.padding(20.dp),
    )
}

private enum class Position { SINGLE, FIRST, MIDDLE, LAST }

private sealed interface Line {
    class Title(val text: String) : Line
    class Item(val entry: InfoEntry, val position: Position) : Line
    class Note(val text: String) : Line
}

/** Lista preguiçosa de seções: só compõe as linhas visíveis (há abas com centenas de itens). */
@Composable
internal fun SectionList(sections: List<InfoSection>, header: (LazyListScope.() -> Unit)? = null) {
    val lines = remember(sections) { flatten(sections) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        header?.invoke(this)
        items(lines.size) { index ->
            when (val line = lines[index]) {
                is Line.Title -> SectionTitle(line.text)
                is Line.Item -> EntryRow(line.entry, line.position)
                is Line.Note -> NoteText(line.text)
            }
        }
    }
}

private fun flatten(sections: List<InfoSection>): List<Line> = buildList {
    sections.forEach { section ->
        add(Line.Title(section.title))
        val last = section.entries.lastIndex
        section.entries.forEachIndexed { index, entry ->
            val position = when {
                last == 0 -> Position.SINGLE
                index == 0 -> Position.FIRST
                index == last -> Position.LAST
                else -> Position.MIDDLE
            }
            add(Line.Item(entry, position))
        }
        section.note?.let { add(Line.Note(it)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = PcColors.Accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

private val corner = 12.dp

@Composable
private fun EntryRow(entry: InfoEntry, position: Position) {
    val shape = when (position) {
        Position.SINGLE -> RoundedCornerShape(corner)
        Position.FIRST -> RoundedCornerShape(topStart = corner, topEnd = corner)
        Position.LAST -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        Position.MIDDLE -> RectangleShape
    }
    val valueColor = when (entry.level) {
        EntryLevel.HIGHLIGHT -> PcColors.Accent
        EntryLevel.WARNING -> PcColors.Warning
        EntryLevel.NORMAL -> PcColors.TextPrimary
    }
    // O espaço de 1 dp entre linhas deixa o fundo aparecer como divisória.
    val gap = if (position == Position.MIDDLE || position == Position.LAST) 1.dp else 0.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = gap)
            .background(PcColors.Surface, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            entry.label,
            color = PcColors.TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.weight(0.4f).padding(end = 12.dp),
        )
        Text(
            entry.value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = if (entry.level == EntryLevel.NORMAL) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun NoteText(text: String) {
    Text(
        text,
        color = PcColors.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
}
