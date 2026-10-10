package tv.own.owntv.features.home

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Android persistence/HTTP adapter; the replaceable reader owns validation and request policy. */
internal class ShlRepository(context: Context, client: OkHttpClient) {
    val preferences = context.getSharedPreferences("mintv-companion", Context.MODE_PRIVATE)
    private val reader = HockeyReader(object : HockeyStorage {
        override fun enabled() = preferences.getBoolean("shl-enabled", false)
        override fun blocked() = preferences.getBoolean("shl-access-blocked", false)
        override fun number(key: String): Long = if (key == "hockey-window-count") preferences.getInt(key, 0).toLong() else preferences.getLong(key, 0)
        override fun journal(values: Map<String, Long>) {
            val edit = preferences.edit()
            values.forEach { (key, value) -> if (key == "hockey-window-count") edit.putInt(key, value.toInt()) else edit.putLong(key, value) }
            check(edit.commit())
        }
        override fun text(key: String) = preferences.getString(key, null)
        override fun text(key: String, value: String) { check(preferences.edit().putString(key, value).commit()) }
        override fun block(code: Int?) { check(preferences.edit().putBoolean("shl-access-blocked", true).putLong("shl-blocked-code", code?.toLong() ?: 0).commit()) }
        private fun file(key: String): AtomicFile {
            require(key in listOf("mintv-shl", "mintv-ha", "mintv-shl-table", "mintv-ha-table"))
            return AtomicFile(File(context.cacheDir, "$key.json"))
        }
        @Synchronized override fun cache(key: String): String? = runCatching {
            val cache = file(key); require(cache.baseFile.length() in 1..200_000)
            cache.openRead().bufferedReader().use { it.readText() }
        }.getOrNull()
        @Synchronized override fun cache(key: String, value: String) {
            require(value.toByteArray().size <= 200_000)
            val cache = file(key); val stream = cache.startWrite()
            try { stream.write(value.toByteArray()); cache.finishWrite(stream) }
            catch (error: Exception) { cache.failWrite(stream); throw error }
        }
    }, HockeyHttpPages(client))
    suspend fun cached(competition: Competition = SportsCatalog.shl, now: Long = System.currentTimeMillis()): ShlSnapshot? =
        withContext(Dispatchers.IO) { reader.cached(competition, now) }
    suspend fun cachedStandings(competition: Competition, season: String): HockeyTable? =
        withContext(Dispatchers.IO) { reader.cachedTable(competition, season) }
    suspend fun refresh(now: Long, competition: Competition = SportsCatalog.shl): ShlSnapshot =
        withContext(Dispatchers.IO) { reader.refresh(competition, now) }
    suspend fun standings(season: String, now: Long, competition: Competition = SportsCatalog.shl): HockeyTable =
        withContext(Dispatchers.IO) { reader.standings(competition, season, now) }
    fun diagnostic(competition: Competition, table: Boolean = false) = reader.diagnostic(competition, table)
    fun lastResponse(competition: Competition) = reader.lastResponse(competition)
}

internal class HockeyHttpPages(client: OkHttpClient) : HockeyPages {
    // Shares Core's pool/dispatcher. Never follows redirects or forwards credentials to another host.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    override suspend fun read(path: String, limit: Int): CompanionResponse {
        require(path == "/robots.txt" || path == "/" || path.matches(Regex("/ScheduleAndResults/(?:Schedule|Standings)/\\d{1,9}")))
        return client.companionResponse(Request.Builder().url("https://stats.swehockey.se$path")
            .header("User-Agent", "MinTV-ExperimentalSHL/0.2").build(), limit)
    }
}

/** Shared persistent two-league budget; a backwards clock cannot open a fresh window. */
internal data class HockeyRequestBudget(val windowAt: Long = 0L, val count: Int = 0) {
    fun reserve(now: Long): HockeyRequestBudget {
        check(now >= 0 && windowAt in 0..now && count in 0..12)
        val expired = windowAt == 0L || now - windowAt >= 2 * 60 * 60_000L
        val used = if (expired) 0 else count
        check(used < 12)
        return HockeyRequestBudget(if (expired) now else windowAt, used + 1)
    }
}
