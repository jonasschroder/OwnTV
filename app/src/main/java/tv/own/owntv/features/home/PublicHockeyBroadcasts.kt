package tv.own.owntv.features.home

import java.time.Instant
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

/** Strict spelling normalization only: no arena renames or fuzzy/token-subset aliases. */
internal fun publicWords(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").split(Regex("[^a-z0-9]+" )).filter(String::isNotEmpty).joinToString(" ")
private fun publicContains(text: String, word: String) = word.isNotBlank() && " ${publicWords(text)} ".contains(" ${publicWords(word)} ")
private fun publicMentions(text: String, team: Team) = SportsCatalog.epgAliases(team).any { words ->
    publicContains(text, words.joinToString(" "))
}
internal val publicTvChannels = linkedMapOf("tv4-hockey" to "TV4 Hockey", "tv4-motor" to "TV4 Motor",
    "tv4-sport-live-1" to "TV4 Sport Live 1", "tv4-sport-live-2" to "TV4 Sport Live 2",
    "tv4-sport-live-3" to "TV4 Sport Live 3", "tv4-sport-live-4" to "TV4 Sport Live 4")
internal const val publicTvBase = "https://tvprogramidag.se/api/public/v1/channel/"
internal const val publicTvTtl = 60 * 60_000L
internal data class PublicHockeyProgramme(val channel: String, val title: String, val description: String,
    val start: Long, val end: Long, val generatedAt: Long, val fetchedAt: Long) {
    val expiry get() = minOf(generatedAt, fetchedAt) + publicTvTtl
    fun liveShl() = publicContains(title + " " + description, "SHL") &&
        listOf("studio", "repris", "replay", "highlights", "SDHL", "HockeyAllsvenskan", "U18", "U20", "junior")
            .none { publicContains(title + " " + description, it) }
}

/** Parses only the observed, documented channel contract. Never reads provider stream addresses. */
internal fun parsePublicHockey(slug: String, body: String, now: Long): List<PublicHockeyProgramme> {
    require(slug in publicTvChannels && body.length <= 300_000)
    val root = JSONObject(body); val channel = root.getJSONObject("channel")
    require(channel.getString("slug") == slug && channel.getString("name") == publicTvChannels.getValue(slug))
    val generated = Instant.parse(root.getString("generatedAt")).toEpochMilli()
    require(generated <= now + 5 * 60_000L && generated > now - publicTvTtl)
    val rows = root.getJSONArray("programs"); require(rows.length() <= 100)
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.getJSONObject(i)
        if (row.opt("isLive") != true) return@mapNotNull null
        val title = row.getString("title"); val description = row.optString("description", "").takeUnless { it == "null" }.orEmpty()
        require(title.length <= 240 && description.length <= 4000)
        val start = Instant.parse(row.getString("start")).toEpochMilli(); val end = Instant.parse(row.getString("end")).toEpochMilli()
        require(end > start)
        PublicHockeyProgramme(publicTvChannels.getValue(slug), title, description, start, end, generated, now).takeIf { it.liveShl() }
    }
}

/** Official fixture arena and time must identify one fresh linear service, without conflicts. */
internal fun publicHockeyAssignment(fixture: SportFixture, known: List<SportFixture>, programmes: List<PublicHockeyProgramme>, now: Long): BroadcastAssignment? {
    if (fixture.competition != SportsCatalog.shl || fixture.status != MatchStatus.SCHEDULED || fixture.venue.isNullOrBlank() ||
        fixture.faceoff < now - 4 * 60 * 60_000L || fixture.faceoff > now + 7 * 24 * 60 * 60_000L ||
        known.any { it.id == fixture.id && it != fixture }) return null
    val fresh = programmes.distinct().filter { it.channel in publicTvChannels.values && it.liveShl() && it.fetchedAt <= now && now < it.expiry }
    val matches = fresh.filter { p -> p.start in (fixture.faceoff - 60 * 60_000L)..(fixture.faceoff + 10 * 60_000L) && p.end > fixture.faceoff &&
        publicContains(p.description + " " + p.title, fixture.venue) }
    val channel = matches.map { it.channel }.distinct().singleOrNull() ?: return null
    for (p in matches) {
        if (known.any { other -> other != fixture && other.competition == fixture.competition &&
                publicWords(other.venue.orEmpty()) == publicWords(fixture.venue) && other.faceoff in p.start until p.end }) return null
        if (fresh.any { other -> other.channel == channel && other.start < p.end && other.end > p.start &&
                (other.start != p.start || other.end != p.end || publicWords(other.description) != publicWords(p.description) || publicWords(other.title) != publicWords(p.title)) }) return null
        if (known.filter { it.competition == fixture.competition }.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinctBy { it.id }.any { team ->
                team.id !in setOf(fixture.homeTeam.id, fixture.awayTeam.id) && publicMentions(p.title + " " + p.description, team) }) return null
    }
    val explicitTeams = matches.any { p -> publicMentions(p.title + " " + p.description, fixture.homeTeam) && publicMentions(p.title + " " + p.description, fixture.awayTeam) }
    return BroadcastAssignment(fixture.broadcastFixture(), listOf(BroadcastChannel(channel, true)),
        Instant.ofEpochMilli(matches.maxOf { it.fetchedAt }), Instant.ofEpochMilli(minOf(matches.minOf { it.expiry }, fixture.faceoff + 4 * 60 * 60_000L)),
        "TV-Programidag", if (explicitTeams) BroadcastEvidence.CONFIRMED else BroadcastEvidence.PROBABLE_EXTERNAL)
}

/** Conflicting independent assignments never silently pick one broadcaster. Local EPG wins elsewhere. */
internal fun mergedHockeyAssignment(fixture: BroadcastFixture, public: BroadcastAssignment?, other: BroadcastAssignment?, now: Instant): BroadcastAssignment? {
    val a = BroadcastResolver.usable(fixture, public, now)
    val b = BroadcastResolver.usable(fixture, other, now)?.takeIf { it.channels.any { c -> c.linear && !BroadcastResolver.streamingBrand(c.name) } }
    if (a == null) return b
    if (b == null) return a
    val names = b.channels.filter { it.linear }
    return b.takeIf { names.size == 1 && BroadcastResolver.matches(names.single(), a.channels.single().name) }
}
