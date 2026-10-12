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
            val settings=SettingsRepository(context,LocaleStore.from(context));settings.setActiveProfile(101)
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
    @Test fun largePagedCatalogRetainsBoundedShelvesAndReportsMeasuredWork():Unit=runBlocking {
        fixture {context,db,settings,custom ->
            val movies=(1..7000).map { MovieEntity(id=it.toLong(),sourceId=101,name="Synthetic film $it",remoteId="movie-$it",year=2025,addedAt=it.toLong(),rating=8.0,streamUrl="https://example.invalid/fixture") }
            val series=(1..1900).map { SeriesEntity(id=it.toLong(),sourceId=101,name="Synthetic series $it",remoteId="series-$it",year=2025,addedAt=it.toLong(),rating=8.0) }
            db.movieDao().upsertAll(movies);db.seriesDao().upsertSeries(series)
            val provider=OfflineProvider();val repo=DiscoveryRepository(db,settings,custom,provider,TrendingScheduleStore(context))
            val start=android.os.SystemClock.elapsedRealtime()
            val movieShelf=repo.shelf(101,listOf(101),MetadataType.MOVIE)
            val seriesShelf=repo.shelf(101,listOf(101),MetadataType.TV)
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            assertFalse(movieShelf.bounded);assertFalse(seriesShelf.bounded)
            assertTrue((movieShelf.rows+seriesShelf.rows).all{it.cards.size<=12})
            assertEquals(0,provider.calls)
            assertEquals(7000L,movieShelf.rows.single{it.kind==DiscoveryKind.RECENT}.cards.first().id)
            assertEquals(1900L,seriesShelf.rows.single{it.kind==DiscoveryKind.RECENT}.cards.first().id)
            val runtime=Runtime.getRuntime()
            println("V05_SYNTHETIC_CATALOG movies=7000 series=1900 queryMs=$elapsed heapBytes=${runtime.totalMemory()-runtime.freeMemory()} heapLimitBytes=${runtime.maxMemory()}")
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
            assertEquals(2L,group.preferred(null,false)!!.id)
            store.save(101,SwedishChoices(preferred=mapOf(group.identity to "101:sd")))
            db.channelDao().upsertAll(listOf(sd.copy(id=900),hd.copy(id=901)))
            val recreated=SwedishChannelStore(context,db,custom)
            val choices=recreated.choices(101)
            assertEquals(900L,recreated.library(101,listOf(101),choices).groups.single().preferred(choices.preferred[group.identity],false)!!.id)
            assertEquals(favorites,db.favoriteDao().getAllOnce());assertNotNull(db.channelDao().getById(3))
            val entity=db.channelDao().getById(900)!!
            assertEquals(sd.httpHeaders,entity.httpHeaders);assertEquals(3,entity.catchupDays);assertEquals(4,entity.number)
            assertTrue(recreated.library(101,listOf(102),choices).groups.isEmpty())
            assertEquals(SwedishChoices(),recreated.choices(102))
            db.profileDao().delete(db.profileDao().getById(101)!!)
            db.profileDao().insert(ProfileEntity(id=101,name="New owner",avatarColor=0,createdAt=999))
            assertEquals(SwedishChoices(),recreated.choices(101))
        }
    }
}
