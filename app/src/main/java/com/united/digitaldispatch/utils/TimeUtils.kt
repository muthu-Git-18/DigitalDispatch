package com.united.digitaldispatch.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    // Same patterns as the old app
    private const val SHIPMENT_PATTERN = "yyMMddHHmmss"      // simpleCompetitionYearMonthDateFormat
    private const val SENDER_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss" // simpleYearMonthDateFormat

    fun shipmentDatePart(date: Date = Date()): String =
        SimpleDateFormat(SHIPMENT_PATTERN, Locale.US).format(date)

    fun senderDate(date: Date = Date()): String =
        SimpleDateFormat(SENDER_DATE_PATTERN, Locale.US).format(date)
}