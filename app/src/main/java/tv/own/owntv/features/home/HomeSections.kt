package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao

internal enum class HomeSection { LIVE, FAVORITES, CONTINUE, SPORT, MOVIES, SERIES, TWITCH, APPS }
internal class HomeSections(context: Context, private val profiles: ProfileDao) {
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
    suspend fun save(profile: Long, hidden: Set<HomeSection>) = withContext(Dispatchers.IO) {
        val owner = profiles.getById(profile)?.createdAt ?: return@withContext
        check(prefs.edit().putString("profile:$profile", JSONObject().put("owner", owner)
            .put("hidden", JSONArray(hidden.map { it.name })).toString()).commit())
    }
}

/** Maps stable section IDs to real lazy items as optional rows arrive/disappear. */
internal fun homeSectionIndices(visible: List<Boolean>): List<Int> {
    var item = 0
    return visible.map { if (it) item++ else -1 }
}
