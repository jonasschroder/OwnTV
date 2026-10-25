package tv.own.owntv.features.home

internal const val SMART_SERIES_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
internal data class SmartSourceAccess(val profile: Long, val live: List<Long>, val movies: List<Long>, val series: List<Long>)
internal enum class SmartResumeKind { MOVIE, EPISODE }
internal data class SmartResume(
    val key: String, val kind: SmartResumeKind, val itemId: Long, val seriesId: Long? = null,
    val title: String, val seriesTitle: String? = null, val season: Int? = null, val episode: Int? = null,
    val poster: String? = null, val playedAt: Long, val position: Long, val duration: Long,
    val completed: Boolean = false,
)
internal fun SmartResume.eligible(): Boolean = !completed && position > 0 && duration > 0 &&
    position.toDouble() / duration.toDouble() < .9

/** Exact catalog identity only. No title matching, automatic next episode, or timer-driven promotion. */
internal fun smartResumeOrder(items: List<SmartResume>): List<SmartResume> = items.filter { it.eligible() }
    .sortedWith(compareByDescending<SmartResume> { it.playedAt }.thenBy { it.key }).distinctBy { it.key }

internal fun smartHeroResume(items: List<SmartResume>, livePlayedAt: Long?, now: Long): SmartResume? {
    val latest = smartResumeOrder(items).firstOrNull() ?: return null
    if (livePlayedAt == null) return latest
    return latest.takeIf { it.kind == SmartResumeKind.EPISODE && it.playedAt > livePlayedAt &&
        it.playedAt <= now && now - it.playedAt <= SMART_SERIES_WINDOW_MS }
}

/** Keep the logical key; removed controls fall to their nearest retained neighbour, then first. */
internal fun smartFocusKey(key: String?, previous: List<String>, current: List<String>): String? {
    if (key in current) return key
    val index = previous.indexOf(key)
    if (index >= 0) for (distance in 1..previous.size) {
        listOf(index + distance, index - distance).mapNotNull(previous::getOrNull)
            .firstOrNull { it in current }?.let { return it }
    }
    return current.firstOrNull()
}

internal fun selectSmartHome(profile: Long, hasSources: Boolean, channels: List<SmartChannel>,
    watched: tv.own.owntv.core.database.entity.ChannelEntity?, watchedAt: Long?,
    progress: List<SmartResume>, at: Long): SmartHomeSnapshot {
    if (!hasSources) return SmartHomeSnapshot(profile, false, emptyList(), null, null, null, emptyList())
    val eligible = smartResumeOrder(progress)
    val promoted = smartHeroResume(eligible, watchedAt, at)
    return SmartHomeSnapshot(profile, true, channels, watched ?: if (promoted == null) channels.firstOrNull()?.channel else null,
        watchedAt, promoted, eligible.filter { it.key != promoted?.key }.take(3))
}

/** Stable logical sections; absent rows have no focus target. */
internal fun smartHomeSectionIndices(channels: Boolean, favorites: Boolean, resumes: Boolean): List<Int> {
    var index = 0
    val channelIndex = if (channels) ++index else -1
    val resumeIndex = if (resumes) ++index else -1
    return listOf(0, channelIndex, if (favorites) ++index else -1, resumeIndex)
}

/** A schedule or specific EPG can justify matchday, never a channel name alone. */
internal fun homeMatchdayEligible(game: SportFixture?, preferences: SportPreferences, now: Long,
    evidenceFresh: Boolean, zone: java.time.ZoneId = java.time.ZoneId.of("Europe/Stockholm")): Boolean =
    evidenceFresh && preferences.prominent && game != null && game.follows(preferences) &&
        game.kickoff.atZone(zone).toLocalDate() == java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

/** A match hero must not hide the episode/movie previously promoted out of Continue Watching. */
internal fun smartHomeResumeRows(promoted: SmartResume?, rows: List<SmartResume>, matchday: Boolean): List<SmartResume> =
    if (matchday) smartResumeOrder(listOfNotNull(promoted) + rows).take(3) else rows

/** Keep the exact fixture while browsing; refresh its labels, never redirect a reused/rescheduled ID. */
internal fun frozenHomeFixture(original: SportFixture?, current: List<SportFixture>): SportFixture? = original?.let { saved ->
    current.singleOrNull { it.id == saved.id }?.takeIf {
        it.competition.id == saved.competition.id && it.homeTeam.id == saved.homeTeam.id &&
            it.awayTeam.id == saved.awayTeam.id && it.kickoff == saved.kickoff
    }
}

/** Favourites already visible in Now keep their badge; only additional channels need a second rail. */
internal fun smartHomeExtraFavorites(channels: List<SmartChannel>, favorites: List<SmartChannel>): List<SmartChannel> {
    val shown = channels.mapTo(hashSetOf()) { it.key }
    return favorites.distinctBy { it.key }.filterNot { it.key in shown }
}

/** Resolve only through the existing profile-owned M3 grouping, never by guessed name similarity. */
internal fun smartHomeFavorite(channel: tv.own.owntv.core.database.entity.ChannelEntity, logicalBySource: Map<String, String>): SmartChannel {
    val sourceKey = tv.own.owntv.core.customize.CustomizeKeys.channel(channel)
    return SmartChannel(logicalBySource[sourceKey] ?: sourceKey, channel.name, channel)
}
