package tv.own.owntv.features.live.source

import tv.own.owntv.features.live.*

internal enum class ChannelSourceIntent { LOGICAL, PROVIDER_EXACT, GUIDE_PROGRAMME, RECORDING, VERIFIED_BROADCAST, UNKNOWN }
internal enum class ChannelSourceStatus { SAVED, AUTOMATIC, SAVED_MISSING, UNAVAILABLE, EXACT }
internal data class ChannelSourceResolution(val selected: SwedishVariant?, val alternatives: List<SwedishVariant>,
    val status: ChannelSourceStatus)

/** No fuzzy matching. The caller supplies an already access-filtered Swedish group or exact rows.
 * Event actions cannot gain equivalence from a channel name. A verified broadcast's candidates
 * must ALL already have fixture/broadcaster evidence; this resolver never creates that evidence.
 */
internal object ChannelSourceResolver {
    fun alternatives(group: SwedishGroup): List<SwedishVariant> = group.variants
        .filter { it.name.identity == group.identity }
        .groupBy { it.stableKey }.values.mapNotNull { rows -> rows.distinctBy { it.id }.singleOrNull() }

    fun resolve(group: SwedishGroup, choices: SwedishChoices, intent: ChannelSourceIntent = ChannelSourceIntent.LOGICAL,
        exact: String? = null): ChannelSourceResolution {
        val rows = alternatives(group)
        val logical = intent == ChannelSourceIntent.LOGICAL || intent == ChannelSourceIntent.VERIFIED_BROADCAST
        // OTHER contains unverified provider decorations, regions, audio and time-shift services.
        // Keep those exact, even if stripping a quality token happens to produce the same name.
        val equivalent = rows.isNotEmpty() && rows.all { it.name.collection != SwedishCollection.OTHER }
        if (!logical || !equivalent) return ChannelSourceResolution(rows.singleOrNull { it.stableKey == exact },
            emptyList(), ChannelSourceStatus.EXACT)
        val saved = choices.preferred[group.identity]
        val selected = group.copy(variants = rows).preferred(saved, choices.showUhd)
        return ChannelSourceResolution(selected, rows, when {
            selected == null -> ChannelSourceStatus.UNAVAILABLE
            saved == selected.stableKey -> ChannelSourceStatus.SAVED
            saved != null -> ChannelSourceStatus.SAVED_MISSING
            else -> ChannelSourceStatus.AUTOMATIC
        })
    }
}
