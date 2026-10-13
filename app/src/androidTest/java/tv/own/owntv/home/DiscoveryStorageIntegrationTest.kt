package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.trending.TrendingScheduleStore
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.live.*

/** Production bundled SQLite/Room plus isolated synthetic preferences. No owner's DB/key/provider. */
@RunWith(AndroidJUnit4::class)
class DiscoveryStorageIntegrationTest {
    private class OfflineProvider : MetadataProvider {
        var calls = 0
        override suspend fun trendingPage(type:MetadataType,page:Int):TrendingFeedPage? { calls++;return null }
        override suspend fun searchMovie(title:String,year:Int?,includeAdult:Boolean):List<MetadataSearchResult>? = null
        override suspend fun searchTv(title:String,year:Int?,includeAdult:Boolean):List<MetadataSearchResult>? = null
        override suspend fun movieDetails(tmdbId:Int):MovieDetails? = null
        override suspend fun tvDetails(tmdbId:Int):MovieDetails? = null
        override suspend fun tvSeasonBundle(tvId:Int,seasons:List<Int>):List<SeasonEpisode>? = null
    }
    private suspend fun fixture(test: suspend (Context,OwnTVDatabase,SettingsRepository,CustomizationStore)->Unit) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(app.cacheDir,"v05-fixture-"+java.util.UUID.randomUUID()).apply{mkdirs()}
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=folder
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences(folder.name+name,mode)
        }
        val db=Room.inMemoryDatabaseBuilder(context,OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for(id in listOf(101L,102L)) {
                db.profileDao().insert(ProfileEntity(id=id,name="Synthetic",avatarColor=0,createdAt=id))
                db.sourceDao().insert(SourceEntity(id=id,name="Synthetic",type=SourceType.M3U,url="https://example.invalid"))
                db.sourceDao().link(ProfileSourceCrossRef(id,id))
            }
            // Core's preferencesDataStore delegates are process singletons, not per ContextWrapper.
            // Reset owned synthetic state explicitly so one provider-only test cannot hide another's rankings.
            val settings=SettingsRepository(context,LocaleStore.from(context));settings.setActiveProfile(101)
            settings.setMetadataMode(MetadataMode.PROVIDER_PLUS_TMDB)
            TrendingScheduleStore(context).storeCandidates(TrendingScheduleStore.Candidates(
                settings.metadataConfig().resolvedLanguage,System.currentTimeMillis(),emptyList(),emptyList(),1,1,1,1))
            test(context,db,settings,CustomizationStore(context))
        } finally {db.close()}
    }
    @Test fun bundledDriverSupportsScopedRankingsOfflineResyncAndHiddenContent():Unit=runBlocking {
        fixture {context,db,settings,custom ->
            val a=MovieEntity(id=1,sourceId=101,name="Verified A",remoteId="a",year=2025,addedAt=100,rating=8.5,streamUrl="https://example.invalid/a")
            val b=a.copy(id=2,name="Verified B",remoteId="b",addedAt=200)
            db.movieDao().upsertAll(listOf(a,b,a.copy(id=3,sourceId=102,remoteId="foreign")))
            val now=System.currentTimeMillis()
            db.metadataDao().upsertMatch(MetadataMatchEntity("movie:101:a","movie",11,1.0,now))
            db.metadataDao().upsertMatch(MetadataMatchEntity("movie:101:b","movie",22,1.0,now))
            val candidates=listOf(TrendingCandidate(22,MetadataType.MOVIE,"Verified B",null,2025,null,null,null,8.5,10.0,1),
                TrendingCandidate(11,MetadataType.MOVIE,"Verified A",null,2025,null,null,null,8.5,9.0,2))
            val cacheKey=MetadataRepository.cacheKey(11,settings.metadataConfig().resolvedLanguage)
            db.metadataDao().upsertCache(MetadataCacheEntity(key=cacheKey,tmdbId=11,imdbId=null,type="movie",title="Verified A",year=2025,
                overview=null,posterPath="/synthetic.jpg",backdropPath=null,rating=8.5,genresJson="[\"Drama\"]",castJson=null,trailerKey=null,logoPath=null,updatedAt=now))
            val schedule=TrendingScheduleStore(context)
            schedule.storeCandidates(TrendingScheduleStore.Candidates("",now,candidates,emptyList(),1,1,1,1))
            val provider=OfflineProvider();val repo=DiscoveryRepository(db,settings,custom,provider,schedule)
            assertTrue(repo.refresh());assertEquals(0,provider.calls)
            val row=repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.single{it.kind==DiscoveryKind.TOP}
            assertEquals(listOf(2L,1L),row.cards.map{it.id});assertTrue(repo.available(row.cards[0],101))
            assertEquals("https://image.tmdb.org/t/p/w342/synthetic.jpg",row.cards.single{it.id==1L}.poster)
            settings.setMetadataMode(MetadataMode.PROVIDER)
            val providerOnly=repo.shelf(101,listOf(101),MetadataType.MOVIE)
            assertTrue(providerOnly.rows.none{it.kind in listOf(DiscoveryKind.TOP,DiscoveryKind.GENRE,DiscoveryKind.RECOMMENDED)})
            assertTrue(providerOnly.rows.flatMap{it.cards}.all{it.poster==null})
            settings.setMetadataMode(MetadataMode.PROVIDER_PLUS_TMDB)
            assertTrue(repo.shelf(101,listOf(102),MetadataType.MOVIE).rows.isEmpty())
            custom.setItemHidden(101,MediaType.MOVIE,"101:b","Synthetic",true)
            assertFalse(repo.available(row.cards[0],101))
            assertEquals(listOf(1L),repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.single{it.kind==DiscoveryKind.TOP}.cards.map{it.id})
            // Same ID with a different provider identity must not tune a stale card.
            db.movieDao().upsertAll(listOf(a.copy(remoteId="replacement",name="Different content")))
            assertFalse(repo.available(row.cards[1],101))
            settings.setActiveProfile(102)
            assertTrue(repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.isEmpty())
            assertFalse(repo.available(row.cards[1],101))
            settings.setMetadataMode(MetadataMode.PROVIDER)
            assertTrue(repo.refresh());assertEquals(0,provider.calls)
        }
    }
    @Test fun featuredMetadataIsCachedScopedAndPreservesProviderRatingPreference(): Unit = runBlocking {
        fixture { context, db, settings, custom ->
            val movie = MovieEntity(id = 71, sourceId = 101, name = "Synthetic featured film", remoteId = "hero", year = 2026,
                addedAt = 1, rating = 8.4, streamUrl = "https://example.invalid/synthetic")
            db.movieDao().upsertAll(listOf(movie))
            val now = System.currentTimeMillis()
            db.metadataDao().upsertMatch(MetadataMatchEntity("movie:101:hero", "movie", 11, 1.0, now))
            db.metadataDao().upsertCache(MetadataCacheEntity(MetadataRepository.cacheKey(11, settings.metadataConfig().resolvedLanguage),
                11, null, "movie", "Synthetic featured film", 2026, "Syntetisk svensk handling.", null, "/synthetic.jpg", 7.1,
                "[\"Drama\"]", null, null, null, now))
            val provider = OfflineProvider(); val repo = DiscoveryRepository(db, settings, custom, provider, TrendingScheduleStore(context))
            val hero = repo.shelf(101, listOf(101), MetadataType.MOVIE).hero!!
            assertEquals(71L, hero.card.id); assertEquals("https://image.tmdb.org/t/p/w780/synthetic.jpg", hero.backdrop)
            assertEquals("Syntetisk svensk handling.", hero.overview); assertFalse(hero.ratingFromTmdb); assertEquals(8.4, hero.rating!!, 0.0)
            assertNull(repo.shelf(102, listOf(101), MetadataType.MOVIE).hero)
            settings.setMetadataMode(MetadataMode.TMDB_ONLY)
            val tmdb = repo.shelf(101, listOf(101), MetadataType.MOVIE).hero!!
            assertTrue(tmdb.ratingFromTmdb); assertEquals(7.1, tmdb.rating!!, 0.0)
            settings.setMetadataMode(MetadataMode.PROVIDER)
            val local = repo.shelf(101, listOf(101), MetadataType.MOVIE).hero!!
            assertNull(local.backdrop); assertNull(local.overview); assertFalse(local.ratingFromTmdb)
            custom.setItemHidden(101, MediaType.MOVIE, "101:hero", "Synthetic", true)
            assertNull(repo.shelf(101, listOf(101), MetadataType.MOVIE).hero)
            assertEquals(0, provider.calls)
        }
    }
    @Test fun largePagedCatalogRetainsBoundedShelvesAndReportsMeasuredWork():Unit=runBlocking {
        fixture {context,db,settings,custom ->
            db.categoryDao().upsertAll(listOf(
                CategoryEntity(501,101,MediaType.MOVIE,"MOVIE: IMDB Top 250","top"),
                CategoryEntity(502,101,MediaType.MOVIE,"MOVIE: Oscar Movies","oscar"),
                CategoryEntity(503,101,MediaType.MOVIE,"MOVIE: Disney","disney"),
                CategoryEntity(504,101,MediaType.MOVIE,"MOVIE: Swedish","swedish"),
                CategoryEntity(601,101,MediaType.SERIES,"SERIES: Netflix","netflix"),
                CategoryEntity(602,101,MediaType.SERIES,"SERIES: Disney","disney"),
                CategoryEntity(603,101,MediaType.SERIES,"SERIES: Amazon Prime","amazon")))
            val movies=(1..7000).map { MovieEntity(id=it.toLong(),sourceId=101,categoryId=(501+it%4).toLong(),name="Synthetic film $it",remoteId="movie-$it",year=2025,addedAt=it.toLong(),rating=8.0,streamUrl="https://example.invalid/fixture") }
            val series=(1..1900).map { SeriesEntity(id=it.toLong(),sourceId=101,categoryId=(601+it%3).toLong(),name="Synthetic series $it",remoteId="series-$it",year=2025,addedAt=it.toLong(),rating=8.0) }
            db.movieDao().upsertAll(movies);db.seriesDao().upsertSeries(series)
            val provider=OfflineProvider();val repo=DiscoveryRepository(db,settings,custom,provider,TrendingScheduleStore(context))
            val start=android.os.SystemClock.elapsedRealtime()
            val movieShelf=repo.shelf(101,listOf(101),MetadataType.MOVIE)
            val seriesShelf=repo.shelf(101,listOf(101),MetadataType.TV)
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            assertFalse(movieShelf.bounded);assertFalse(seriesShelf.bounded)
            assertTrue((movieShelf.rows+seriesShelf.rows).all{it.cards.size<=12})
            assertEquals(4,movieShelf.rows.count { it.kind==DiscoveryKind.PROVIDER })
            assertEquals(3,seriesShelf.rows.count { it.kind==DiscoveryKind.PROVIDER })
            assertEquals(0,provider.calls)
            assertEquals(7000L,movieShelf.rows.single{it.kind==DiscoveryKind.RECENT}.cards.first().id)
            assertEquals(1900L,seriesShelf.rows.single{it.kind==DiscoveryKind.RECENT}.cards.first().id)
            val runtime=Runtime.getRuntime()
            println("V06_SYNTHETIC_CATALOG movies=7000 series=1900 providerFolders=7 queryMs=$elapsed heapBytes=${runtime.totalMemory()-runtime.freeMemory()} heapLimitBytes=${runtime.maxMemory()}")
        }
    }
    @Test fun providerFoldersRetainExactMembershipVariantsCustomizationAndOfflineAccess():Unit=runBlocking {
        fixture { context, db, settings, custom ->
            val oscar = CategoryEntity(501,101,MediaType.MOVIE,"MOVIE: Oscar Movies","oscar")
            val top = CategoryEntity(502,101,MediaType.MOVIE,"MOVIE: IMDB Top 250","top")
            val netflix = CategoryEntity(503,101,MediaType.SERIES,"SERIES: Netflix","netflix")
            db.categoryDao().upsertAll(listOf(oscar,top,netflix,top.copy(id=504,sourceId=102,remoteId="foreign")))
            val film = MovieEntity(id=1,sourceId=101,categoryId=501,name="Real film FHD",remoteId="one",year=2025,streamUrl="https://example.invalid/one")
            db.movieDao().upsertAll(listOf(film,film.copy(id=2,name="Real film HD",remoteId="two"),
                film.copy(id=3,categoryId=502,remoteId="three"),film.copy(id=4,categoryId=504,sourceId=102,remoteId="foreign")))
            db.seriesDao().upsertSeries(listOf(SeriesEntity(id=1,sourceId=101,categoryId=503,name="Actual series",remoteId="series")))
            for (remote in listOf("one","two","three")) db.metadataDao().upsertMatch(MetadataMatchEntity("movie:101:$remote","movie",77,1.0,System.currentTimeMillis()))
            settings.setMetadataMode(MetadataMode.PROVIDER)
            val provider=OfflineProvider(); val repo=DiscoveryRepository(db,settings,custom,provider,TrendingScheduleStore(context))
            val rows=repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.filter { it.kind==DiscoveryKind.PROVIDER }
            assertEquals(listOf(502L,501L),rows.map { it.folder!!.id })
            assertEquals(3L,rows[0].cards.single().id)
            assertEquals(listOf(1L,2L),listOf(rows[1].cards.single().id)+rows[1].cards.single().variants.map { it.id })
            assertTrue(repo.folderAvailable(rows[0].folder!!,101,MetadataType.MOVIE))
            assertFalse(repo.folderAvailable(rows[0].folder!!,101,MetadataType.TV))
            assertEquals(503L,repo.shelf(101,listOf(101),MetadataType.TV).rows.single { it.kind==DiscoveryKind.PROVIDER }.folder!!.id)
            custom.renameCategory(101,MediaType.MOVIE,tv.own.owntv.core.customize.CustomizeKeys.category(oscar),"My awards")
            custom.setCategoryOrder(101,MediaType.MOVIE,listOf(tv.own.owntv.core.customize.CustomizeKeys.category(oscar)))
            val reordered=repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.filter { it.kind==DiscoveryKind.PROVIDER }
            assertEquals("My awards",reordered.first().folder!!.name)
            custom.setItemMovedFromOrigin(101,MediaType.MOVIE,"101:three",tv.own.owntv.core.customize.CustomizeKeys.category(top),true)
            assertTrue(repo.shelf(101,listOf(101),MetadataType.MOVIE).rows.none { it.folder?.id==502L })
            assertFalse(repo.available(rows[0].cards.single(),101))
            db.categoryDao().updateAll(listOf(oscar.copy(remoteId="replacement")))
            assertFalse(repo.folderAvailable(reordered.first().folder!!,101,MetadataType.MOVIE))
            settings.setActiveProfile(102)
            assertFalse(repo.available(rows[1].cards.single(),101))
            assertEquals(0,provider.calls)
        }
    }
    @Test fun curatedPreferencesSurviveResyncAndKeepOriginalFavoritesAndAuthorization():Unit=runBlocking {
        fixture {context,db,_,custom ->
            val sd=ChannelEntity(id=1,sourceId=101,name="SE | TV4 SD",remoteId="sd",number=4,streamUrl="https://example.invalid/sd",httpHeaders="X-Synthetic: fixture",catchup=true,catchupDays=3)
            val hd=sd.copy(id=2,name="SE: TV4 FHD",remoteId="fhd",streamUrl="https://example.invalid/fhd")
            val uhd=sd.copy(id=3,name="TV4 UHD SE",remoteId="uhd")
            db.channelDao().insertAll(listOf(sd,hd,uhd,sd.copy(id=4,sourceId=102,remoteId="foreign")))
            db.favoriteDao().add(FavoriteEntity(profileId=101,mediaType=MediaType.LIVE,itemId=3))
            val favorites=db.favoriteDao().getAllOnce()
            val store=SwedishChannelStore(context,db,custom)
            val group=store.library(101,listOf(101),SwedishChoices()).groups.single()
            assertEquals(1L,group.preferred(null,false)!!.id)
            val stored = SwedishChoices(showUhd=true,showVariants=true,order=listOf(group.identity),hidden=setOf("svt2"),preferred=mapOf(group.identity to "101:fhd"))
            store.save(101,stored)
            val other = SwedishChoices(preferred=mapOf(group.identity to "102:foreign"))
            store.save(102,other)
            db.channelDao().upsertAll(listOf(sd.copy(id=900),hd.copy(id=901)))
            val recreated=SwedishChannelStore(context,db,custom)
            val choices=recreated.choices(101)
            assertEquals(stored,choices)
            assertEquals(901L,recreated.library(101,listOf(101),choices).groups.single().preferred(choices.preferred[group.identity],false)!!.id)
            assertEquals(favorites,db.favoriteDao().getAllOnce());assertNotNull(db.channelDao().getById(3))
            val entity=db.channelDao().getById(900)!!
            assertEquals(sd.httpHeaders,entity.httpHeaders);assertEquals(3,entity.catchupDays);assertEquals(4,entity.number)
            assertTrue(recreated.library(101,listOf(102),choices).groups.isEmpty())
            assertEquals(other,recreated.choices(102))
            assertEquals(4L,recreated.library(102,listOf(102),other).groups.single().preferred(other.preferred[group.identity],false)!!.id)
            db.profileDao().delete(db.profileDao().getById(101)!!)
            db.profileDao().insert(ProfileEntity(id=101,name="New owner",avatarColor=0,createdAt=999))
            assertEquals(SwedishChoices(),recreated.choices(101))
        }
    }
}
