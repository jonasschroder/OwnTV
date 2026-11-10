package tv.own.owntv.features.sporthub

/** Labels are public source names, never IPTV entities or URLs. Legacy club IDs stay separate. */
internal data class HubFollow(val id: String, val name: String, val league: String, val sport: HubSport) {
    fun valid(): Boolean = name.isNotBlank() && name.length <= 160 && name.none(Char::isISOControl) &&
        league.matches(Regex("[a-z0-9.-]{1,80}")) &&
        id.matches(Regex(Regex.escape(sport.preferencePrefix) + ":(?:openliga:[0-9]{1,19}|events24:[0-9a-f]{32})"))
}
