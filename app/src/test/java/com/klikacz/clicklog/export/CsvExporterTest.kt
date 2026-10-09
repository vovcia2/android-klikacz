package com.klikacz.clicklog.export

import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.data.ClickEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CsvExporterTest {

    @Test
    fun `csv has the spec header and one row per click`() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val csv = CsvExporter.buildClicksCsv(
                listOf(
                    ClickEntity(id = 1, sessionId = 7, wallTimeMs = 1_700_000_000_123, uptimeMs = 55_000, source = ClickSourceType.KEY_EVENT),
                    ClickEntity(id = 2, sessionId = 7, wallTimeMs = 1_700_000_001_000, uptimeMs = null, source = ClickSourceType.LAUNCH_INTENT),
                ),
            )
            val lines = csv.trimEnd().lines()
            assertEquals("id,session_id,wall_time_iso,wall_time_ms,uptime_ms,source", lines[0])
            assertEquals("1,7,2023-11-14T22:13:20.123Z,1700000000123,55000,KEY_EVENT", lines[1])
            assertEquals("2,7,2023-11-14T22:13:21.000Z,1700000001000,,LAUNCH_INTENT", lines[2])
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    @Test
    fun `empty export is header only`() {
        val csv = CsvExporter.buildClicksCsv(emptyList())
        assertTrue(csv.startsWith("id,session_id"))
        assertEquals(1, csv.trimEnd().lines().size)
    }
}
