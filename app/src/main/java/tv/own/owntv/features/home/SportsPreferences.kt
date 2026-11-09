package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.features.sporthub.*

internal data class SportProfileSelection(val owner: Long, val value: SportPreferences)

internal object SportPreferencesCodec {
    fun bound(value: SportPreferences, createdAt: Long): String = JSONObject().put("owner", createdAt)
        .put("selection", JSONObject(encode(value))).toString()
    fun forOwner(value: String?, createdAt: Long): SportPreferences = runCatching {
        require(value != null && value.length <= 32_768)
        val root = JSONObject(value); require(root.getLong("owner") == createdAt)
        decode(root.getJSONObject("selection").toString())
    }.getOrDefault(SportPreferences())
    fun encode(value: SportPreferences): String {
        require(value.hubTeams.size <= 64 && value.hubTeams.all { it.valid() })
        require(value.hubProviders.all { it in setOf(HubProvider.OPENLIGA, HubProvider.EVENTS24) })
        return JSONObject().put("teams", JSONArray(value.teamIds.distinct()))
            .put("prominent", value.prominent).put("hideScores", value.hideScores)
            .put("hubTeams", JSONArray(value.hubTeams.distinctBy { it.id }.map {
                JSONObject().put("id", it.id).put("name", it.name).put("league", it.league).put("sport", it.sport.name)
            })).put("hubProviders", JSONArray(value.hubProviders.map { it.name }.sorted())).toString()
            .also { require(it.length <= 32_000) }
    }
    fun decode(value: String?): SportPreferences = runCatching {
        require(value != null && value.length <= 32_768)
        val root = JSONObject(value); val ids = root.getJSONArray("teams"); require(ids.length() <= 256)
        val legacy = SportPreferences((0 until ids.length()).map { ids.getString(it).also { id ->
            require(id.matches(Regex("(?:hockey|football):[a-z0-9-]{1,100}")))
        } }.distinct(), root.optBoolean("prominent", true), root.optBoolean("hideScores", false))
        // Unknown/corrupt optional data must never erase the owner's established Färjestad IDs.
        val teams = runCatching {
            val rows = root.optJSONArray("hubTeams") ?: JSONArray(); require(rows.length() <= 64)
            (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                HubFollow(it.getString("id"), it.getString("name"), it.getString("league"), HubSport.valueOf(it.getString("sport")))
                    .also { follow -> require(follow.valid()) }
            } }.distinctBy { it.id }
        }.getOrDefault(emptyList())
        val providers = runCatching {
            val rows = root.optJSONArray("hubProviders") ?: JSONArray(); require(rows.length() <= 2)
            (0 until rows.length()).map { HubProvider.valueOf(rows.getString(it)).also { p ->
                require(p in setOf(HubProvider.OPENLIGA, HubProvider.EVENTS24))
            } }.toSet()
        }.getOrDefault(emptySet())
        legacy.copy(hubTeams = teams, hubProviders = providers)
    }.getOrDefault(SportPreferences())
}

/** Private profile keys, same small SharedPreferences pattern as fixture choices; no credentials. */
internal class SportsPreferences(context: Context, private val profiles: tv.own.owntv.core.database.dao.ProfileDao) {
    private val preferences = context.getSharedPreferences("mintv-sport-profiles", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()
    suspend fun migrateLegacy(existing: List<tv.own.owntv.core.database.entity.ProfileEntity>): Unit = mutex.withLock { withContext(Dispatchers.IO) {
        if (!preferences.getBoolean("legacy-migrated", false)) {
            val edit = preferences.edit()
            existing.filter { it.id >= 0 }.forEach { profile ->
                if (!preferences.contains("profile:${profile.id}")) edit.putString("profile:${profile.id}",
                    SportPreferencesCodec.bound(SportPreferences(listOf(SportsCatalog.farjestadId)), profile.createdAt))
            }
            check(edit.putBoolean("legacy-migrated", true).commit())
            revision.value++
        }
    } }
    suspend fun read(profileId: Long): SportPreferences = withContext(Dispatchers.IO) {
        val profile = profiles.getById(profileId) ?: return@withContext SportPreferences()
        SportPreferencesCodec.forOwner(preferences.getString("profile:$profileId", null), profile.createdAt)
    }
    suspend fun readSelection(profileId: Long): SportProfileSelection? = mutex.withLock { withContext(Dispatchers.IO) {
        val profile = profiles.getById(profileId) ?: return@withContext null
        SportProfileSelection(profile.createdAt, SportPreferencesCodec.forOwner(preferences.getString("profile:$profileId", null), profile.createdAt))
    } }
    /** Atomic against another picker, and rejects an asynchronously reused profile ID. */
    suspend fun updateSelection(profileId: Long, owner: Long, change: (SportPreferences) -> SportPreferences): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val profile = profiles.getById(profileId)?.takeIf { it.createdAt == owner } ?: return@withContext false
        val previous = SportPreferencesCodec.forOwner(preferences.getString("profile:$profileId", null), profile.createdAt)
        val next = change(previous); require(next.teamIds.size <= 256)
        check(preferences.edit().putString("profile:$profileId", SportPreferencesCodec.bound(next, profile.createdAt)).commit())
        revision.value++; true
    } }
    suspend fun save(profileId: Long, value: SportPreferences): Unit = mutex.withLock { withContext(Dispatchers.IO) {
        require(profileId >= 0 && value.teamIds.size <= 256)
        val profile = profiles.getById(profileId) ?: return@withContext
        check(preferences.edit().putString("profile:$profileId", SportPreferencesCodec.bound(value, profile.createdAt)).commit())
        revision.value++
    } }
    suspend fun remove(profileId: Long): Unit = mutex.withLock { withContext(Dispatchers.IO) {
        check(preferences.edit().remove("profile:$profileId").commit()); revision.value++
    } }
}

/** Keeps competition discovery/provider DTOs outside the generic UI model. */
internal class SportsRepository(private val hockey: ShlRepository) {
    suspend fun cached(competition: Competition): SportSnapshot? = if (!competition.scheduleAvailable) null
        else hockey.cached(competition).let { it?.sportSnapshot(competition) }
    suspend fun refresh(competition: Competition, now: Long): SportSnapshot? = if (!competition.scheduleAvailable) null
        else hockey.refresh(now, competition).sportSnapshot(competition)
    suspend fun standings(competition: Competition, season: String, now: Long): HockeyTable? =
        if (competition.scheduleAvailable) hockey.standings(season, now, competition) else null
    suspend fun cachedStandings(competition: Competition, season: String): HockeyTable? =
        if (competition.scheduleAvailable) hockey.cachedStandings(competition, season) else null
    private fun ShlSnapshot.sportSnapshot(competition: Competition) = SportSnapshot(competition, seasonId,
        games.map { SportsCatalog.fixture(competition, it) }, fetchedAt, sourceUpdatedAt)
}
