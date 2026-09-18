package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local (offline-first) copy of an organization/location record.
 *
 * This is intentionally a trimmed-down version of the old
 * `OrganizationalMasterData` model (WH / PPD apps) — only the fields the
 * unified login + dashboard actually need. Once the sync API is ready,
 * this table gets populated from the same "organizationCode /
 * organizationType" master data the old apps synced.
 *
 * organizationType is one of: TAP, PPD, GLT, WH  -> this is what the
 * Dashboard uses to decide which cards (Dispatch/Receipt/PSW/GTD) to show.
 */
@Entity(tableName = "organization_table")
data class OrganizationEntity(
    @PrimaryKey
    val organizationCode: String,
    val organizationName: String? = null,
    val organizationType: String? = null, // TAP / PPD / GLT / WH
    val organizationAddress: String? = null,
    val variety: String? = null,
    val syncId: String? = null,
    val syncPassword: String? = null
)
