package tv.own.owntv.home

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** One runner selector; CI also verifies each member's individual executed result. */
@RunWith(Suite::class)
@Suite.SuiteClasses(HomeDpadTraversalTest::class, HomeChannelDefaultsStorageTest::class,
    SportsReaderIntegrationTest::class, SportsProfileStorageTest::class, HomeContentViewportTest::class,
    ChannelPickerNavigationTest::class, SportsLoadingNavigationTest::class, SportsArrivalNavigationTest::class, VodPresentationTest::class,
    HomePresentationTest::class, TwitchOnboardingTest::class, TwitchPlaybackIntentTest::class, SetupOnceHomeTest::class, DiscoveryStorageIntegrationTest::class, DiscoveryPresentationTest::class, TwitchTokenStorageTest::class, HockeyLocalStorageTest::class, V07PresentationTest::class,
    SwedishRowStorageTest::class, SwedishLivePresentationTest::class,
    HomeDashboardStorageTest::class, HomeDashboardPresentationTest::class,
    MinTvIdentityBackupTest::class, MinTvRoutingTest::class,
    tv.own.owntv.core.database.ChannelDaoBoundedWindowTest::class,
    tv.own.owntv.core.i18n.LocalizedContentContextTest::class,
    tv.own.owntv.core.i18n.PluralResourceBoundaryTest::class)
class HomeRegressionSuite
