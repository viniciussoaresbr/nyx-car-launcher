package com.premiumcar.launcher.diagnostics

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.diagnostics.cartest.PlayerInfo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gera o relatório em texto e o grava onde o usuário consiga copiá-lo para o pendrive. */
object ReportExporter {

    fun fileName(): String =
        "nyx-car-diagnostico-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    /** No Android 9 ou anterior, gravar em Download exige permissão de armazenamento. */
    fun needsLegacyPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= 28 &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** Arquivo gravado: onde ficou e o tamanho que o sistema confirma. */
    class Saved(val where: String, val bytes: Long)

    /** Grava na pasta pública Download e devolve o caminho para exibir ao usuário. */
    fun saveToDownloads(context: Context, text: String): Saved {
        val name = fileName()
        val bytes = text.toByteArray()
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("o sistema recusou a criação do arquivo")
            // O tamanho registrado pelo MediaStore só se atualiza depois; não dá para conferir aqui.
            writeUri(context, uri, bytes)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return Saved("Download/$name", bytes.size.toLong())
        }
        @Suppress("DEPRECATION")
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        directory.mkdirs()
        val file = File(directory, name)
        FileOutputStream(file).use { writeSynced(it, bytes) }
        val confirmed = file.length()
        check(confirmed == bytes.size.toLong()) { "o arquivo ficou com $confirmed de ${bytes.size} bytes" }
        // Faz o arquivo aparecer imediatamente para gerenciadores de arquivos e MTP.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("text/plain"), null)
        return Saved(file.absolutePath, confirmed)
    }

    /**
     * Grava no local escolhido no seletor (ex.: o pendrive) e devolve o tamanho confirmado.
     * O seletor cria o arquivo vazio antes de o app gravar; por isso a gravação é forçada até
     * o disco e o tamanho é conferido depois.
     */
    fun writeTo(context: Context, uri: Uri, text: String): Long {
        val bytes = text.toByteArray()
        writeUri(context, uri, bytes)
        val confirmed = sizeOf(context, uri)
        check(confirmed == null || confirmed == bytes.size.toLong()) {
            "o arquivo ficou com $confirmed de ${bytes.size} bytes"
        }
        return confirmed ?: bytes.size.toLong()
    }

    private fun writeUri(context: Context, uri: Uri, bytes: ByteArray) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "w")
            ?: error("não foi possível abrir o local escolhido")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { writeSynced(it, bytes) }
    }

    /**
     * Grava e força a escrita física. Sem isso, os dados podem ficar só no cache do sistema e,
     * se o pendrive for removido logo depois, o arquivo aparece no PC com 0 bytes.
     */
    private fun writeSynced(stream: FileOutputStream, bytes: ByteArray) {
        stream.write(bytes)
        stream.flush()
        // Alguns provedores entregam um canal sem suporte a sync; aí vale a gravação comum.
        runCatching { stream.fd.sync() }
    }

    private fun sizeOf(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    fun formatSize(bytes: Long): String =
        if (bytes < 1024) "$bytes bytes" else String.format(Locale.forLanguageTag("pt-BR"), "%.0f KB", bytes / 1024.0)

    /**
     * Relatório montado antes de abrir o seletor de "Salvar em…". Fica num arquivo privado
     * para sobreviver se o Android encerrar o app enquanto o seletor está aberto.
     */
    private fun pendingFile(context: Context) = File(context.filesDir, "relatorio-pendente.txt")

    fun storePending(context: Context, text: String) = pendingFile(context).writeText(text)

    fun takePending(context: Context): String? = pendingFile(context).takeIf { it.exists() }?.readText()

    fun clearPending(context: Context) {
        pendingFile(context).delete()
    }

    /** Seções dos testes no carro lidas agora, ou nulo se a leitura falhar (vale a da abertura). */
    private fun carTests(context: Context): List<InfoSection>? =
        runCatching { PlayerChecks.sections(context) + PlayerInfo.sections(context) + CarTestLog.section() }.getOrNull()

    fun buildText(context: Context, report: DiagnosticReport, performance: List<InfoSection>): String = buildString {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss (zzz)", Locale.forLanguageTag("pt-BR")).format(Date())
        appendLine("NYX CAR LAUNCHER — RELATÓRIO DE DIAGNÓSTICO")
        appendLine("Versão do app: ${AppInfo.versionName(context)}")
        appendLine("Gerado em: $now")
        appendLine("Legenda: [*] destaque · [!] atenção. Identificadores (serial, MAC, IMEI, rede) foram omitidos.")
        DiagnosticTab.entries.forEach { tab ->
            val sections = when (tab) {
                DiagnosticTab.PERFORMANCE -> performance
                // Lidos de novo na hora de salvar (0.4.5): o diário e o player mudam durante os
                // testes, depois de a tela do diagnóstico ter sido aberta.
                DiagnosticTab.CAR_TESTS -> carTests(context) ?: report.sections[tab].orEmpty() + CarTestLog.section()
                else -> report.sections[tab].orEmpty()
            }
            if (sections.isEmpty()) return@forEach
            appendLine()
            appendLine("=".repeat(72))
            appendLine(tab.title.uppercase())
            appendLine("=".repeat(72))
            sections.forEach { section ->
                appendLine()
                appendLine("## ${section.title}")
                section.entries.forEach { entry ->
                    val marker = when (entry.level) {
                        EntryLevel.HIGHLIGHT -> "[*] "
                        EntryLevel.WARNING -> "[!] "
                        EntryLevel.NORMAL -> "    "
                    }
                    appendLine("$marker${entry.label}: ${entry.value.replace("\n", "\n        ")}")
                }
                section.note?.let { appendLine("    Obs.: $it") }
            }
        }
    }
}
