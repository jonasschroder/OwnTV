package tv.own.owntv.features.live

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
