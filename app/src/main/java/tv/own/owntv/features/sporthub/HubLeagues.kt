package tv.own.owntv.features.sporthub

import tv.own.owntv.R

/** Reviewed source bindings, not fixtures, results or broadcaster assignments. Seasonal beta
 * bindings are labelled with their actual season; no newer season is silently fabricated.
 * Coverage evidence: docs/sport/v019-data-sources.md. SHL/HA keep their existing owners.
 */
internal object HubLeagues {
    val shl = HubLeague("se.shl", HubSport.ICE_HOCKEY, R.string.hub_league_shl, HubProvider.SWEDISH, HubAccess.EXISTING_READER)
    val ha = HubLeague("se.hockeyallsvenskan", HubSport.ICE_HOCKEY, R.string.hub_league_ha, HubProvider.SWEDISH, HubAccess.EXISTING_READER)
    val nhl = HubLeague("us.nhl", HubSport.ICE_HOCKEY, R.string.hub_league_nhl, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "123176", "NHL 2026/2027", "US", "2026-27")
    val liiga = HubLeague("fi.liiga", HubSport.ICE_HOCKEY, R.string.hub_league_liiga, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "142163", "Liiga 2026-2027 Runkosarja", "FI", "2026-27")
    val del = HubLeague("de.del", HubSport.ICE_HOCKEY, R.string.hub_league_del, HubProvider.OPENLIGA, HubAccess.VERIFIED_PUBLIC, "del")
    val national = HubLeague("ch.national", HubSport.ICE_HOCKEY, R.string.hub_league_national, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "141810", "National League 2026-27", "CH", "2026-27")
    val extraliga = HubLeague("cz.extraliga", HubSport.ICE_HOCKEY, R.string.hub_league_extraliga, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "141861", "Tipsport extraliga 2026/27", "CZ", "2026-27")
    val ice = HubLeague("at.ice", HubSport.ICE_HOCKEY, R.string.hub_league_ice, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "141202", "win2day ICE Hockey League 2026/27", "AT", "2026-27")
    val chl = HubLeague("eu.chl", HubSport.ICE_HOCKEY, R.string.hub_league_chl, HubProvider.OPENLIGA, HubAccess.VERIFIED_PUBLIC, "CHL")
    val premier = HubLeague("gb.premier", HubSport.FOOTBALL, R.string.hub_league_premier, HubProvider.OPENLIGA, HubAccess.VERIFIED_PUBLIC, "pl")
    val allsvenskan = HubLeague("se.allsvenskan", HubSport.FOOTBALL, R.string.hub_league_allsvenskan, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "10", "Allsvenskan", "SE", "2026")
    val international = HubLeague("international.hockey", HubSport.ICE_HOCKEY, R.string.hub_league_international,
        HubProvider.NONE, HubAccess.UNVERIFIED)
    val reviewed = listOf(shl, ha, nhl, liiga, del, national, extraliga, ice, chl, premier, allsvenskan, international)
    fun find(key: String): HubLeague? = reviewed.firstOrNull { it.key == key }
}
