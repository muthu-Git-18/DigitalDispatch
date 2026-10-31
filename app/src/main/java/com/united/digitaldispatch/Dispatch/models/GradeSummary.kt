package com.united.digitaldispatch.Dispatch.models

/** One row of the grade-wise summary. */
data class GradeSummary(
    val grade: String,
    val bales: Int,
    /** sum of marked weight */
    val qty: Double,
    /** sum of dispatch weight */
    val dispatchQty: Double,
    /** sum of (marked weight x price) - used for TAP */
    val value: Double,
    /** value / qty - used for TAP */
    val avgRate: Double
)

object DispatchSummaryCalc {

    fun byGrade(bales: List<BaleDetail>): List<GradeSummary> =
        bales
            .groupBy { it.grade.ifBlank { "-" } }
            .map { (grade, list) ->

                val qty = list.sumOf { it.markedWeight.toDoubleOrNull() ?: 0.0 }
                val dispatchQty = list.sumOf { it.dispatchWeight.toDoubleOrNull() ?: 0.0 }
                val value = list.sumOf {
                    (it.markedWeight.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0)
                }

                GradeSummary(
                    grade = grade,
                    bales = list.size,
                    qty = qty,
                    dispatchQty = dispatchQty,
                    value = value,
                    avgRate = if (qty > 0) value / qty else 0.0
                )
            }
            .sortedBy { it.grade }
}