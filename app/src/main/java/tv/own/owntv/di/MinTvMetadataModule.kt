package tv.own.owntv.di

import org.koin.dsl.module
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.TmdbProvider
import tv.own.owntv.features.metadata.ConservativeMetadataProvider

/** Last module: decorate the existing provider, retaining its config/auth/budget implementation. */
val minTvMetadataModule = module {
    single<MetadataProvider> { ConservativeMetadataProvider(TmdbProvider(get(), get(), get(), get())) }
}
