package tv.own.owntv.features.live

/** Only a strict provider-labelled regional service qualifies. No national stream is relabelled.
 * Separate source identities stay separate, including event/EPG source semantics.
 */
internal fun preferVarmland(groups: List<SwedishGroup>, choices: SwedishChoices): List<SwedishGroup> {
    var result = groups
    for (national in listOf("svt1", "svt2")) {
        val regional = groups.singleOrNull { it.identity == "${national}varmland" } ?: continue
        val sources = regional.variants.map { it.sourceId }.distinct()
        val explicitNational = choices.preferred[national] != null
        result = when {
            national in choices.hidden || explicitNational || sources.size != 1 -> result.filterNot { it == regional }
            else -> result.filterNot { it.identity == national }
        }
    }
    return result
}

/** Rank only summaries already read for visible cards. Never fetch an entire EPG to sort. */
internal fun guideBucket(info: SwedishRowInfo?, now: Long): Int = info?.at(now)?.let {
    when { it.now != null -> 0; it.next != null -> 1; else -> 2 }
} ?: 2

/** Freeze existing positions while browsing; add/remove keys without a nearest-neighbour focus jump. */
internal fun stableGuideOrder(groups: List<SwedishGroup>, previous: List<String>, frozen: Boolean,
    info: Map<String, SwedishRowInfo>, now: Long): List<String> {
    val ids = groups.map { it.identity }
    return if (frozen) previous.filter { it in ids } + ids.filterNot { it in previous }
        else groups.sortedBy { guideBucket(info[it.identity], now) }.map { it.identity }
}
