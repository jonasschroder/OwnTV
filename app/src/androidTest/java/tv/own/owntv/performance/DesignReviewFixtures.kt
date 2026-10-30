package tv.own.owntv.performance

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.home.*
import tv.own.owntv.core.i18n.LocaleStore

/** Called only by PerformanceJourneyTest's package/qemu/argument-guarded test, after timings.
 * Existing production Room/cache/preferences owners consume these explicitly synthetic records.
 */
internal suspend fun seedDesignReview(app: Context, db: OwnTVDatabase, settings: SettingsRepository) {
    val profile = 99000L
    check(db.profileDao().getById(profile)?.name == "Synthetic performance profile")
    check(db.channelDao().countForSourceOnce(profile) == 12000)
    if (db.profileDao().getById(99001) == null) db.profileDao().insert(ProfileEntity(id = 99001,
        name = "Synthetic empty profile", avatarColor = 2, createdAt = 99001))
    // Idempotent fixture reset is confined to our synthetic keys after the qemu/profile guards.
    db.epgDao().deleteProgrammesForChannels(profile, (0..54).map { "perf-$it" } + "perf-60")
    val now = System.currentTimeMillis()
    check(LocaleStore.from(app).set("sv"))
    settings.setWeatherEnabled(false)
    val source = db.sourceDao().getById(profile)!!
    db.sourceDao().update(source.copy(syncMovies = true, syncSeries = true, liveEnginePreference = "EXO_FIRST"))
    // 32 additional logical Other services, including one long Swedish name. No stream is played.
    for (i in 23..54) {
        val ch = db.channelDao().getById(9900000L + i)!!
        db.channelDao().updateAll(listOf(ch.copy(name = "SE | Syntetisk svensk kanal $i med en lång kanalrubrik")))
    }
    val regional = db.channelDao().getById(9900060)!!
    db.channelDao().updateAll(listOf(regional.copy(name = "SVT1 Värmland HD")))
    for (i in 0..54) if (i != 5) db.epgDao().upsertProgrammes(listOf(
        EpgProgrammeEntity(sourceId = profile, epgChannelId = "perf-$i", startMs = now - 1_800_000,
            stopMs = now + 1_800_000, title = "Syntetiskt program $i - fördjupning, intervjuer och berättelser från hela Sverige"),
        EpgProgrammeEntity(sourceId = profile, epgChannelId = "perf-$i", startMs = now + 1_800_000,
            stopMs = now + 5_400_000, title = "Syntetiskt nästa program $i")))
    for (i in 0..8) db.favoriteDao().add(FavoriteEntity(profileId = profile, mediaType = MediaType.LIVE, itemId = 9900000L + i))
    db.movieDao().insertAll(listOf(MovieEntity(id = 9910001, sourceId = profile, name = "Syntetisk film - en mycket lång svensk titel med sparad position",
        streamUrl = "http://127.0.0.1:9/movie/1"), MovieEntity(id = 9910002, sourceId = profile,
        name = "Syntetisk film utan omslagsbild", streamUrl = "http://127.0.0.1:9/movie/2")))
    db.seriesDao().insertSeries(listOf(SeriesEntity(id = 9910010, sourceId = profile, name = "Syntetisk serie med lång titel")))
    db.seriesDao().upsertEpisodes(listOf(EpisodeEntity(id = 9910011, seriesId = 9910010, seasonNumber = 1, episodeNumber = 3,
        name = "Syntetiskt avsnitt med sparad position", streamUrl = "http://127.0.0.1:9/episode/3")))
    for ((i, item) in listOf(MediaType.MOVIE to 9910001L, MediaType.EPISODE to 9910011L).withIndex())
        db.progressDao().save(PlaybackProgressEntity(profileId = profile, mediaType = item.first, itemId = item.second,
            positionMs = 18 * 60_000L, durationMs = 48 * 60_000L, updatedAt = now - 120_000 - i))
    GlobalContext.get().get<SportsPreferences>().save(profile, SportPreferences(listOf(SportsCatalog.farjestadId)))
    // Honest unknown broadcast: no external assignment is fabricated, and public lookup is off.
    app.getSharedPreferences("mintv-companion", 0).edit().putBoolean("shl-enabled", true)
        .putBoolean("broadcast-enabled", false).commit().also { check(it) }
    val zone = java.time.ZoneId.of("Europe/Stockholm")
    val dayEnd = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli() - 1_000
    val faceoff = minOf(now + 7_200_000, dayEnd)
    val games = JSONArray().put(JSONObject().put("id", "synthetic-v016-fbk").put("home", "Färjestad BK")
        .put("away", "Skellefteå AIK").put("faceoff", faceoff).put("result", ""))
        .put(JSONObject().put("id", "synthetic-v016-next").put("home", "Brynäs IF").put("away", "Färjestad BK")
            .put("faceoff", now + 90_000_000).put("result", ""))
    File(app.cacheDir, "mintv-shl.json").writeText(JSONObject().put("competition", SportsCatalog.shl.id)
        .put("season", "synthetic-2026").put("games", games).put("at", now).put("sourceAt", now).toString())
    db.historyDao().record(WatchHistoryEntity(profileId = profile, mediaType = MediaType.LIVE, itemId = 9900000))
}
