package tv.own.owntv.features.home

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class FixtureChannelChoice(val profileId: Long, val fixture: String, val channelId: Long,
    val sourceId: Long, val name: String, val expiresAt: Long) {
    fun usable(profile: Long, game: BroadcastFixture, now: Long, id: Long, source: Long, channelName: String): Boolean =
        profile == profileId && fixture == fixtureKey(game) && now < expiresAt && channelId == id && sourceId == source && name == channelName
}

/** Device-local, profile-scoped explicit choice, never treated as an externally confirmed broadcaster. */
internal class ShlChannelOverrides(context: Context) {
    private val preferences = context.getSharedPreferences("mintv-shl-channel-choices", Context.MODE_PRIVATE)
    fun read(profile: Long, fixture: BroadcastFixture, now: Long): FixtureChannelChoice? = all().singleOrNull {
        it.profileId == profile && it.fixture == fixtureKey(fixture) && now < it.expiresAt
    }
    fun save(choice: FixtureChannelChoice, now: Long) {
        val values = all().filter { it.expiresAt > now && !(it.profileId == choice.profileId && it.fixture == choice.fixture) }.takeLast(15) + choice
        write(values)
    }
    private fun write(values: List<FixtureChannelChoice>) {
        val json = JSONArray().apply { values.forEach {
            put(JSONObject().put("profile", it.profileId).put("fixture", it.fixture).put("channel", it.channelId)
                .put("source", it.sourceId).put("name", it.name).put("expires", it.expiresAt))
        } }.toString()
        preferences.edit { putString("choices", json) }
    }
    fun clear(profile: Long, fixture: BroadcastFixture) {
        val values = all().filterNot { it.profileId == profile && it.fixture == fixtureKey(fixture) }
        write(values)
    }
    private fun all(): List<FixtureChannelChoice> = runCatching {
        val text = preferences.getString("choices", null) ?: return emptyList()
        require(text.length <= 12_000)
        val rows = JSONArray(text); require(rows.length() <= 16)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            FixtureChannelChoice(it.getLong("profile"), it.getString("fixture"), it.getLong("channel"),
                it.getLong("source"), it.getString("name"), it.getLong("expires"))
        } }
    }.getOrDefault(emptyList())
}
