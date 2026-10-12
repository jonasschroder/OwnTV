package tv.own.owntv.di

import org.koin.dsl.module
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.TmdbProvider
import tv.own.owntv.features.metadata.ConservativeMetadataProvider
import tv.own.owntv.features.metadata.BoundedTrendingProvider
import tv.own.owntv.features.metadata.MetadataResponseLimit
import tv.own.owntv.core.network.HttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Last module: decorate the existing provider, retaining its config/auth/budget implementation. */
val minTvMetadataModule = module {
    single<MetadataProvider> { BoundedTrendingProvider(ConservativeMetadataProvider(TmdbProvider(
        HttpClient(get<OkHttpClient>().newBuilder().callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(MetadataResponseLimit()).build()), get(), get(), get()))) }
}
