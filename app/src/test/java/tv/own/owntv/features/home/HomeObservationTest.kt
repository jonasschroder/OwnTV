package tv.own.owntv.features.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeObservationTest {
    @Test fun noUnusedFeedAndExactlyOneProducerUntilLastConsumerLeaves() = runTest {
        val count = MutableStateFlow(0)
        var starts = 0
        var cancelled = 0
        backgroundScope.launch {
            whileHomeObserved(count) {
                starts++
                try { awaitCancellation() } finally { cancelled++ }
            }
        }
        runCurrent(); assertEquals(0, starts)
        count.value = 1; runCurrent(); assertEquals(1, starts)
        count.value = 2; runCurrent(); assertEquals(1, starts)
        count.value = 1; runCurrent(); assertEquals(0, cancelled)
        count.value = 0; runCurrent(); assertEquals(1, cancelled)
        count.value = 1; runCurrent(); assertEquals(2, starts)
    }
}
