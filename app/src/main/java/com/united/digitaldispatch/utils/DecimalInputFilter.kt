package com.united.digitaldispatch.utils

import android.text.InputFilter
import android.text.Spanned

/** Allows only numbers like 12345 or 123.4 (max [maxIntDigits] before and [maxDecimals] after the dot). */
class DecimalInputFilter(
    maxIntDigits: Int,
    maxDecimals: Int
) : InputFilter {

    private val pattern = Regex("^\\d{0,$maxIntDigits}(\\.\\d{0,$maxDecimals})?$")

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {

        val result = StringBuilder(dest)
            .replace(dstart, dend, source.subSequence(start, end).toString())
            .toString()

        return if (pattern.matches(result)) null else ""
    }
}