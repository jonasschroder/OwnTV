@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package tv.own.owntv.features.metadata

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.*

class BoundedMetadataTest {
    private abstract class Provider : MetadataProvider {
        override suspend fun searchMovie(title:String,year:Int?,includeAdult:Boolean):List<MetadataSearchResult>?=null
        override suspend fun searchTv(title:String,year:Int?,includeAdult:Boolean):List<MetadataSearchResult>?=null
        override suspend fun movieDetails(tmdbId:Int):MovieDetails?=null
        override suspend fun tvDetails(tmdbId:Int):MovieDetails?=null
        override suspend fun tvSeasonBundle(tvId:Int,seasons:List<Int>):List<SeasonEpisode>?=null
    }
    private fun page(type:MetadataType=MetadataType.MOVIE)=TrendingFeedPage(1,1,listOf(TrendingCandidate(1,type,"Synthetic",null,2025,null,null,null,8.0,10.0,1)))
    @Test fun concurrentCoreAndScreenRequestsShareOneFetchAndCancellationCanRetry()=runTest {
        var calls=0
        val upstream=object:Provider(){override suspend fun trendingPage(type:MetadataType,page:Int):TrendingFeedPage?{calls++;delay(100);return page(type)}}
        val cache=BoundedTrendingProvider(upstream){testScheduler.currentTime}
        val results=coroutineScope{(1..8).map{async{cache.trendingPage(MetadataType.MOVIE,1)}}.awaitAll()}
        assertEquals(1,calls);assertTrue(results.all{it!!.candidates.single().tmdbId==1})
        testScheduler.advanceTimeBy(61000)
        val job=launch{cache.trendingPage(MetadataType.MOVIE,1)}
        testScheduler.runCurrent();job.cancelAndJoin()
        assertNotNull(cache.trendingPage(MetadataType.MOVIE,1));assertEquals(3,calls)
    }
    @Test fun nullAndWrongTypeAreNeverConvertedToConfirmedRankings()=runTest {
        var calls=0
        val upstream=object:Provider(){override suspend fun trendingPage(type:MetadataType,page:Int):TrendingFeedPage?{calls++;return when(calls){1->null;2->page(MetadataType.TV);else->page(type)}}}
        val cache=BoundedTrendingProvider(upstream){testScheduler.currentTime}
        assertNull(cache.trendingPage(MetadataType.MOVIE,1));assertNull(cache.trendingPage(MetadataType.MOVIE,1));assertEquals(1,calls)
        testScheduler.advanceTimeBy(61000);assertNull(cache.trendingPage(MetadataType.MOVIE,1))
        testScheduler.advanceTimeBy(61000);assertNotNull(cache.trendingPage(MetadataType.MOVIE,1))
    }
    @Test fun metadataResponseLimitAcceptsBoundedPayloadAndRejectsOversizeBeforeBuffering() {
        fun client(size:Int)=OkHttpClient.Builder().addInterceptor(MetadataResponseLimit()).addInterceptor {chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("x".repeat(size).toResponseBody()).build()
        }.build()
        val request=Request.Builder().url("https://example.invalid/metadata-fixture").build()
        client(1024).newCall(request).execute().use{assertEquals(1024,it.body.string().length)}
        try {client(1_048_577).newCall(request).execute().use{it.body.string()};fail("Unbounded response accepted")} catch(_:java.io.IOException){}
    }
}
