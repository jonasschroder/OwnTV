package tv.own.owntv.features.live

import android.content.Context
import tv.own.owntv.features.catalog.readCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.model.MediaType
import kotlinx.coroutines.flow.first

internal data class SwedishChoices(val showUhd: Boolean = false, val showVariants: Boolean = false,
    val order: List<String> = emptyList(), val hidden: Set<String> = emptySet(), val preferred: Map<String, String> = emptyMap())
internal data class SwedishLibrary(val groups: List<SwedishGroup>, val bounded: Boolean = false)

/** Derived compact index from existing Room tables. No stream URLs, DB migration or catalog mutation. */
internal class SwedishChannelStore(context: Context, private val db: OwnTVDatabase, private val customize: CustomizationStore) {
    private val prefs = context.getSharedPreferences("mintv-swedish-channels", 0)
    suspend fun choices(profile: Long): SwedishChoices = withContext(Dispatchers.IO) {
        val owner = db.profileDao().getById(profile)?.createdAt ?: return@withContext SwedishChoices()
        runCatching {
            val raw = prefs.getString("profile:$profile", null) ?: return@withContext SwedishChoices()
            require(raw.length <= 65_536)
            val o = JSONObject(raw); require(o.getLong("owner") == owner)
            fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until minOf(a.length(), 4096)).map(a::getString) }.orEmpty()
            val preferred = o.optJSONObject("preferred")
            SwedishChoices(o.optBoolean("uhd"), o.optBoolean("variants"), list("order"), list("hidden").toSet(),
                preferred?.keys()?.asSequence()?.take(4096)?.associateWith { preferred.getString(it) }.orEmpty())
        }.getOrDefault(SwedishChoices())
    }
    suspend fun save(profile: Long, value: SwedishChoices) = withContext(Dispatchers.IO) {
        val owner = db.profileDao().getById(profile)?.createdAt ?: return@withContext
        val raw = JSONObject().put("owner", owner).put("uhd", value.showUhd).put("variants", value.showVariants)
            .put("order", JSONArray(value.order)).put("hidden", JSONArray(value.hidden.toList()))
            .put("preferred", JSONObject(value.preferred)).toString()
        require(raw.length <= 65_536); check(prefs.edit().putString("profile:$profile", raw).commit())
    }
    suspend fun library(profile: Long, sources: List<Long>, choices: SwedishChoices): SwedishLibrary = withContext(Dispatchers.IO) {
        val permitted = db.sourceDao().observeForProfile(profile).first().filter { it.syncLive }.map { it.id }.toSet()
        val scopedSources = sources.filter { it in permitted }
        if (scopedSources.isEmpty()) return@withContext SwedishLibrary(emptyList())
        val cust = customize.observe(profile, MediaType.LIVE).first()
        val categories = db.categoryDao().observe(scopedSources, MediaType.LIVE).first()
        val forbidden = categories.filter { CustomizeKeys.category(it) in cust.hiddenCategories ||
            !tv.own.owntv.core.content.AdultCategoryClassifier.allows(profile, it.id, db.profileDao(), db.categoryDao()) }.map { it.id }.toSet()
        val candidates = ArrayList<SwedishVariant>()
        var last = 0L; var scanned = 0; var ended = false
        val marks = scopedSources.joinToString(",") { "?" }
        while (scanned < 100_000 && candidates.size < 4096) {
            currentCoroutineContext().ensureActive()
            var count = 0
            db.readCatalog(
                "SELECT id,sourceId,remoteId,name,number,categoryId,epgChannelId FROM channels WHERE sourceId IN ($marks) AND id > ? ORDER BY id LIMIT 400",
                (scopedSources + last).toTypedArray()) { cursor ->
                while (cursor.moveToNext()) {
                    last = cursor.getLong(0); count++; scanned++
                    val raw = cursor.getString(3)
                    val stable = "${cursor.getLong(1)}:${if (cursor.isNull(2)) raw else cursor.getString(2)}"
                    if (stable in cust.hiddenItems || !cursor.isNull(5) && cursor.getLong(5) in forbidden) continue
                    val recognized = SwedishChannels.recognize(raw) ?: continue
                    val epg = if (cursor.isNull(6)) null else cursor.getString(6)
                    // Only explicit regional/time-shift/audio EPG evidence refines a broadcaster.
                    // Different arbitrary provider EPG namespaces do not prove distinct services.
                    val distinct = epg?.lowercase(java.util.Locale.ROOT)?.takeIf {
                        Regex("(?:stockholm|skane|skåne|goteborg|göteborg|\\+1|english|norsk|dansk)").containsMatchIn(it)
                    }
                    val name = recognized.copy(identity = recognized.identity + distinct?.let { ":$it" }.orEmpty())
                    if (candidates.size < 4096) candidates += SwedishVariant(last, cursor.getLong(1),
                        "${cursor.getLong(1)}:${if (cursor.isNull(2)) raw else cursor.getString(2)}", raw, name,
                        if (cursor.isNull(4)) null else cursor.getInt(4))
                }
            }
            if (count < 400) { ended = true; break }
        }
        SwedishLibrary(SwedishChannels.group(candidates, choices.order, choices.hidden), !ended)
    }
}
