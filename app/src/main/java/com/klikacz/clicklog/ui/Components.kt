package com.klikacz.clicklog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticEntity

@Composable
fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, ok: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val color = when (ok) {
            true -> Color(0xFF2E7D32)
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurface
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** One line of the diagnostic log, compact enough for a live list. */
@Composable
fun DiagnosticRow(e: DiagnosticEntity, screenOffLabel: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimeFormat.timeWithMillis(e.tsMs), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            Text(e.channel.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        val summary = when (e.channel) {
            DiagnosticChannel.ACTIVITY_KEY, DiagnosticChannel.ACCESSIBILITY_KEY, DiagnosticChannel.MEDIA_BUTTON ->
                "${e.action} keycode=${e.keycode} scancode=${e.scancode}"
            DiagnosticChannel.LAUNCH_INTENT, DiagnosticChannel.MAIN_INTENT, DiagnosticChannel.VENDOR_BROADCAST ->
                "intent=${e.intentAction}"
            DiagnosticChannel.APP, DiagnosticChannel.CLICK -> e.extrasJson ?: ""
        }
        Text(summary + if (!e.screenOn) "  [$screenOffLabel]" else "", style = MaterialTheme.typography.bodySmall)
        if (e.channel != DiagnosticChannel.APP && e.channel != DiagnosticChannel.CLICK && !e.extrasJson.isNullOrBlank()) {
            Text(e.extrasJson, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        }
    }
}
