package com.premiumcar.launcher.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premiumcar.launcher.design.PcColors
import com.premiumcar.launcher.diagnostics.InfoSection
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.CarTestRecorder
import com.premiumcar.launcher.diagnostics.cartest.MediaCommands
import com.premiumcar.launcher.diagnostics.cartest.MediaSessionAccess
import com.premiumcar.launcher.launcher.AppLauncher

/** Aba "Testes no carro": gravação (CarPlay/USB, player) e comandos de mídia de teste. */
@Composable
internal fun CarTestsPanel(sections: List<InfoSection>?, onRefresh: () -> Unit, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        CarTestLog.listener = { version++ }
        onDispose { CarTestLog.listener = null }
    }
    val running = remember(version) { CarTestRecorder.running }
    val elapsed = remember(version) { CarTestRecorder.elapsedMillis / 1000 }
    val all = remember(version, sections) { sections.orEmpty() + CarTestLog.section() }
    val hasReceiver = remember(sections) { MediaCommands.playerReceivers(context).isNotEmpty() }
    val sessionsGranted = remember(sections) { MediaSessionAccess.isGranted(context) }
    val stockPlayerDisabled = remember(sections) { AppLauncher(context).isDisabled(MediaCommands.PLAYER_PACKAGE) }

    SectionList(all) {
        item {
            TestCard(
                title = "1. Gravação (até ${CarTestRecorder.DURATION_MS / 1000} s)",
                text = "CarPlay: inicie, conecte o iPhone pelo cabo e espere o CarPlay aparecer; conte os " +
                    "segundos. Player USB: inicie, toque uma música do pendrive, saia do player e volte " +
                    "aqui. A gravação continua com o Nyx Diagnóstico em segundo plano.",
            ) {
                if (running) {
                    Button(onClick = { CarTestRecorder.stop("pelo usuário") }) { Text("Parar gravação") }
                    Text(
                        "Gravando: $elapsed s",
                        color = PcColors.Accent,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                } else {
                    Button(onClick = { CarTestRecorder.start(context) }) { Text("Iniciar gravação") }
                    OutlinedButton(onClick = { CarTestLog.clear() }) { Text("Limpar linha do tempo") }
                }
            }
        }
        item {
            TestCard(
                title = "2. Comandos para o player USB",
                text = "Com uma música do pendrive tocando (ou pausada), toque um botão e observe o " +
                    "player. A linha do tempo registra se havia música tocando antes e 1,5 s depois.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaCommands.Route.entries.forEach { route ->
                        val enabled = when (route) {
                            MediaCommands.Route.SYSTEM -> true
                            MediaCommands.Route.PLAYER_RECEIVER -> hasReceiver
                            MediaCommands.Route.PLAYER_SESSION -> sessionsGranted
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                route.label.replaceFirstChar { it.uppercase() },
                                color = if (enabled) PcColors.TextPrimary else PcColors.TextSecondary,
                                fontSize = 15.sp,
                                modifier = Modifier.width(170.dp),
                            )
                            MediaCommands.Command.entries.forEach { command ->
                                OutlinedButton(enabled = enabled, onClick = { MediaCommands.send(context, route, command) }) {
                                    Text(command.label)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            TestCard(
                title = "3. Acesso às sessões de mídia (opcional)",
                text = "Mostra se o player USB, o Bluetooth e o CarPlay publicam estado e faixa, o que " +
                    "permite sincronizar o cartão de música. Exige ativar \"Nyx Car\" em Acesso a " +
                    "notificações. O app não lê notificações. Desative ao terminar, se preferir. " +
                    "Situação: ${if (sessionsGranted) "concedido" else "não concedido"}.",
            ) {
                Button(onClick = { onStatus(MediaSessionAccess.openSettings(context)) }) { Text("Abrir Acesso a notificações") }
                OutlinedButton(onClick = onRefresh) { Text("Atualizar") }
            }
        }
        item {
            TestCard(
                title = "4. Player original (Música e Vídeo do fabricante)",
                text = "Para ele não abrir sozinho ao conectar o pendrive, desligue \"Reprodução automática\" em " +
                    "Configurações do Veículo → Geral (resolvido assim em 11/11/2026). Desativar o app é opcional e " +
                    "reversível: na página que abre, toque em Desativar, ou em Ativar para voltar. O Vídeo é o mesmo " +
                    "app e também fica desativado. Situação: ${if (stockPlayerDisabled) "desativado" else "ativado"}.",
            ) {
                Button(onClick = {
                    val opened = runCatching {
                        context.startActivity(AppLauncher.detailsIntent(MediaCommands.PLAYER_PACKAGE))
                    }.isSuccess
                    if (!opened) onStatus("Não foi possível abrir a página do player original")
                }) { Text("Abrir página do player original") }
                OutlinedButton(onClick = onRefresh) { Text("Atualizar") }
            }
        }
    }
}

@Composable
private fun TestCard(title: String, text: String, actions: @Composable RowScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(PcColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(title, color = PcColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = PcColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
    }
}
