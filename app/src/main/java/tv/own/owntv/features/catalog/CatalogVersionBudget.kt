package tv.own.owntv.features.catalog

import kotlinx.coroutines.withTimeoutOrNull

// A playlist/EPG import may own Room's writer for seconds. Version checking is optional:
// bound that wait and fall back to an uncached reader scan, never to unverified cached rows.
internal const val CATALOG_VERSION_TIMEOUT_MS = 100L
internal suspend fun <T : Any> catalogVersionWithinBudget(read: suspend () -> T): T? =
    withTimeoutOrNull(CATALOG_VERSION_TIMEOUT_MS) { read() }
