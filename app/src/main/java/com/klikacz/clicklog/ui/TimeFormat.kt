package com.klikacz.clicklog.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormat {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private fun zoned(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

    fun iso(epochMs: Long): String = isoFormatter.format(zoned(epochMs))
    fun timeWithMillis(epochMs: Long): String = timeFormatter.format(zoned(epochMs))
    fun dateTimeWithMillis(epochMs: Long): String = dateTimeFormatter.format(zoned(epochMs))
}
