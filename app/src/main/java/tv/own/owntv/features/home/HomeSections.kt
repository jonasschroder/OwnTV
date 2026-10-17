package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao

internal enum class HomeSection { LIVE, FAVORITES, CONTINUE, SPORT, MOVIES, SERIES, TWITCH, APPS, NOW }
internal class HomeSections(context: Context, private val profiles: ProfileDao) {
    private val writes = Mutex()
    private val prefs = context.getSharedPreferences("mintv-home-sections", 0)
    suspend fun hidden(profile: Long): Set<HomeSection> = withContext(Dispatchers.IO) {
        val owner = profiles.getById(profile)?.createdAt ?: return@withContext emptySet()
        runCatching {
            val raw = prefs.getString("profile:$profile", null) ?: return@withContext emptySet()
            require(raw.length <= 4096)
            val o = JSONObject(raw); require(o.getLong("owner") == owner)
            val a = o.getJSONArray("hidden")
            (0 until minOf(a.length(), HomeSection.entries.size)).mapNotNull { i -> HomeSection.entries.firstOrNull { it.name == a.optString(i) } }.toSet()
        }.getOrDefault(emptySet())
    }
    suspend fun order(profile: Long): List<HomeSection> = withContext(Dispatchers.IO) {
        val owner = profiles.getById(profile)?.createdAt ?: return@withContext defaultHomeOrder
        runCatching {
            val raw = prefs.getString("profile:$profile", null) ?: return@withContext defaultHomeOrder
            require(raw.length <= 4096)
            val o = JSONObject(raw); require(o.getLong("owner") == owner)
            val a = o.optJSONArray("order")
            normalizedHomeOrder(a?.let { (0 until minOf(it.length(), HomeSection.entries.size)).mapNotNull { i ->
                HomeSection.entries.firstOrNull { section -> section.name == a.optString(i) }
            } }.orEmpty())
        }.getOrDefault(defaultHomeOrder)
    }
    suspend fun saveOrder(profile: Long, order: List<HomeSection>) = withContext(Dispatchers.IO) {
        val owner = profiles.getById(profile)?.createdAt ?: return@withContext
        writes.withLock {
            val previous = readOwned(profile, owner)
            check(prefs.edit().putString("profile:$profile", previous.put("owner", owner)
            .put("order", JSONArray(normalizedHomeOrder(order).map { it.name })).toString()).commit())
        }
    }
    private fun readOwned(profile: Long, owner: Long): JSONObject = runCatching {
        val raw = prefs.getString("profile:$profile", null) ?: return@runCatching JSONObject()
        require(raw.length <= 4096)
        JSONObject(raw).takeIf { it.optLong("owner", -1) == owner } ?: JSONObject()
    }.getOrElse { JSONObject() }
    suspend fun save(profile: Long, hidden: Set<HomeSection>) = withContext(Dispatchers.IO) {
        val owner = profiles.getById(profile)?.createdAt ?: return@withContext
        writes.withLock {
            check(prefs.edit().putString("profile:$profile", readOwned(profile, owner).put("owner", owner)
            .put("hidden", JSONArray(hidden.map { it.name })).toString()).commit())
        }
    }
}

/** Maps stable section IDs to real lazy items as optional rows arrive/disappear. */
internal fun homeSectionIndices(visible: List<Boolean>): List<Int> {
    var item = 0
    return visible.map { if (it) item++ else -1 }
}

/** Stable names preserve legacy visibility; the live hero and companion pair have fixed anchors. */
internal val defaultHomeOrder = listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.TWITCH,
    HomeSection.FAVORITES, HomeSection.CONTINUE, HomeSection.NOW, HomeSection.SERIES, HomeSection.MOVIES, HomeSection.APPS)
internal fun normalizedHomeOrder(saved: List<HomeSection>): List<HomeSection> =
    listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.TWITCH) +
        (saved + defaultHomeOrder).distinct().filter { it !in setOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.TWITCH) }
