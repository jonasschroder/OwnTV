package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class MatchcenterSection { MATCHES, TABLE, FARJESTAD, SETTINGS }
internal data class MatchcenterNavigation(val section: MatchcenterSection = MatchcenterSection.MATCHES,
    val gameId: String? = null, val channelPicker: Boolean = false) {
    fun back(): MatchcenterNavigation? = when {
        channelPicker -> copy(channelPicker = false)
        gameId != null -> copy(gameId = null)
        else -> null
    }
}

internal fun shortTeam(name: String): String = name.replace(Regex("^(?:IF|HC)\\s+|\\s+(?:BK|HC|IK|Redhawks|Lakers)$"), "").trim()
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("sv-SE")).withZone(Stockholm)
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("sv-SE")).withZone(Stockholm)
internal fun stockholmTime(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms))
internal fun stockholmDate(ms: Long): String = dateFormat.format(Instant.ofEpochMilli(ms))
internal fun stockholmDay(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(Stockholm).toLocalDate()

internal fun updateLabel(ms: Long, now: Long): String = if (stockholmDay(ms) == stockholmDay(now)) stockholmTime(ms)
    else stockholmDate(ms) + " " + stockholmTime(ms)
