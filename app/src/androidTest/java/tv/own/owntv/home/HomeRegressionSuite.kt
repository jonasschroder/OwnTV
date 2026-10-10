package tv.own.owntv.home

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** One runner selector; CI also verifies each member's individual executed result. */
@RunWith(Suite::class)
@Suite.SuiteClasses(HomeDpadTraversalTest::class, HomeChannelDefaultsStorageTest::class)
class HomeRegressionSuite
