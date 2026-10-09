package com.klikacz.clicklog.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.klikacz.clicklog.clicks.ClickRepository
import com.klikacz.clicklog.data.ClickEntity
import com.klikacz.clicklog.data.DiagnosticDao
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.di.IoDispatcher
import com.klikacz.clicklog.ui.TimeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Builds `clicks.csv` and the diagnostic log text (spec §2.2, §3.4). */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: ClickRepository,
    private val diagnosticDao: DiagnosticDao,
) {
    suspend fun clicksCsv(currentSessionOnly: Boolean): String = withContext(io) {
        val rows = if (currentSessionOnly) repository.listCurrentSession() else repository.listAll()
        buildClicksCsv(rows)
    }

    suspend fun diagnosticLogText(): String = withContext(io) {
        buildDiagnosticText(diagnosticDao.listAll())
    }

    /** Writes [content] to a [Uri] obtained from `ACTION_CREATE_DOCUMENT`. */
    suspend fun writeTo(uri: Uri, content: String) = withContext(io) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
            ?: error("Cannot open $uri")
    }

    /** Writes [content] to the cache dir and returns a share Intent (share sheet). */
    suspend fun shareIntent(fileName: String, mimeType: String, content: String): Intent = withContext(io) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val CLICKS_FILE = "clicks.csv"
        const val DIAG_FILE = "diagnostic_log.txt"
        const val CSV_MIME = "text/csv"
        const val TEXT_MIME = "text/plain"

        fun buildClicksCsv(rows: List<ClickEntity>): String = buildString {
            append("id,session_id,wall_time_iso,wall_time_ms,uptime_ms,source\n")
            for (r in rows) {
                append(r.id).append(',')
                append(r.sessionId).append(',')
                append(TimeFormat.iso(r.wallTimeMs)).append(',')
                append(r.wallTimeMs).append(',')
                append(r.uptimeMs ?: "").append(',')
                append(r.source.name).append('\n')
            }
        }

        fun buildDiagnosticText(rows: List<DiagnosticEntity>): String = buildString {
            append("# ClickLog diagnostic log\n")
            append("# id\tts_iso\tts_ms\tchannel\tkeycode\tscancode\taction\tintent_action\tscreen_on\textras_json\n")
            for (r in rows) {
                append(r.id).append('\t')
                append(TimeFormat.iso(r.tsMs)).append('\t')
                append(r.tsMs).append('\t')
                append(r.channel.name).append('\t')
                append(r.keycode ?: "").append('\t')
                append(r.scancode ?: "").append('\t')
                append(r.action ?: "").append('\t')
                append(r.intentAction ?: "").append('\t')
                append(r.screenOn).append('\t')
                append(r.extrasJson ?: "").append('\n')
            }
        }
    }
}
