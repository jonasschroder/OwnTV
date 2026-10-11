package tv.own.owntv.features.shell.components

/** Budget in Stage pixels: retain stored preferences, reserve toolbar and a usable poster row.
 * Geometry never depends on fetched artwork or metadata. */
internal fun vodBrowseHeroHeight(viewport: Float, requested: Float): Float =
    requested.coerceAtLeast(0f).coerceAtMost(240f).coerceAtMost((viewport - 490f).coerceAtLeast(0f))

internal fun vodBrowseColumns(available: Float, minimumPoster: Float = 196f, gap: Float = 26f): Int =
    ((available + gap) / (minimumPoster + gap)).toInt().coerceIn(1, 7)
