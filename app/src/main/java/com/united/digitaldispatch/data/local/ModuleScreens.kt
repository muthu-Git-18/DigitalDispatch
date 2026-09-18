package com.united.digitaldispatch.data.local

/**
 * Single source of truth for "which screens does each GPIL module show".
 *
 * This is exactly the mapping you described:
 *   TAP -> Dispatch
 *   PPD -> GTD Dispatch, Receipt
 *   GLT -> Dispatch, Receipt, PSW Dispatch, PSW Receipt
 *   WH  -> Dispatch, Receipt, PSW Dispatch, PSW Receipt
 *
 * Kept as one map (instead of scattered if/else in the Dashboard) so that
 * when a module's screen list changes, there's exactly one place to edit.
 */
object ModuleType {
    const val TAP = "TAP"
    const val PPD = "PPD"
    const val GLT = "GLT"
    const val WH = "WH"

    val ALL = listOf(TAP, PPD, GLT, WH)
}

enum class DashboardScreen {
    DISPATCH, RECEIPT, PSW_DISPATCH, PSW_RECEIPT, GTD_DISPATCH
}

object ModuleScreens {

    private val map: Map<String, Set<DashboardScreen>> = mapOf(
        ModuleType.TAP to setOf(
            DashboardScreen.DISPATCH
        ),
        ModuleType.PPD to setOf(
            DashboardScreen.GTD_DISPATCH,
            DashboardScreen.RECEIPT
        ),
        ModuleType.GLT to setOf(
            DashboardScreen.DISPATCH,
            DashboardScreen.RECEIPT,
            DashboardScreen.PSW_DISPATCH,
            DashboardScreen.PSW_RECEIPT
        ),
        ModuleType.WH to setOf(
            DashboardScreen.DISPATCH,
            DashboardScreen.RECEIPT,
            DashboardScreen.PSW_DISPATCH,
            DashboardScreen.PSW_RECEIPT
        )
    )

    fun allowedScreens(moduleType: String?): Set<DashboardScreen> =
        map[moduleType?.uppercase()] ?: emptySet()
}
