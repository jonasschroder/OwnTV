@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.home

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TwitchDeviceGrantTest {
    private val client = "publicclient12345"
    private fun device(expiry: Int = 120) = CompanionResponse(200, """{"device_code":"synthetic-device","user_code":"ABCDEFGH","verification_uri":"https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH","expires_in":$expiry,"interval":5}""")
    private fun tokens() = CompanionResponse(200, """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":14400,"token_type":"bearer"}""")
    private fun pending() = CompanionResponse(400, """{"status":400,"message":"authorization_pending"}""")
    private suspend fun failKind(operation: suspend () -> Any?): TwitchFailureKind = try { operation(); error("Expected failure") }
        catch (failure: TwitchFailure) { failure.kind }
    @Test fun deviceCodeIsDisplayedBeforePollingAndFieldsContainNoSecret() = runTest {
        val fields = mutableListOf<Map<String,String>>(); var displayed = false
        val grant = TwitchDeviceGrant({ path, form ->
            fields += form
            if (path == "device") device() else { assertTrue(displayed); tokens() }
        }, { testScheduler.currentTime })
        val result = grant.login(client) { displayed = true; assertEquals("ABCDEFGH",it.code) }
        assertEquals("synthetic-access",result.getString("access_token"))
        assertTrue(fields.all { "client_secret" !in it && it["scopes"] == "" })
        assertEquals(5000,testScheduler.currentTime)
    }
    @Test fun pendingAndSlowDownHonorIncreasingPollingIntervals() = runTest {
        val calls=mutableListOf<Long>()
        val grant=TwitchDeviceGrant({ path, _ -> if(path=="device") device() else {
            calls += testScheduler.currentTime
            when(calls.size) { 1 -> CompanionResponse(400,"""{"error":"slow_down"}""");2 -> pending();else -> tokens() }
        } },{testScheduler.currentTime})
        grant.login(client) {}
        assertEquals(listOf(5000L,15000L,25000L),calls)
    }
    @Test fun denialAndExpiryAreTerminalAndDoNotPersistOrPollAgain() = runTest {
        var calls=0
        val denied=TwitchDeviceGrant({path,_->if(path=="device") device() else { calls++;CompanionResponse(400,"""{"error":"access_denied"}""") }},{testScheduler.currentTime})
        assertEquals(TwitchFailureKind.DENIED,failKind { denied.login(client) {} });assertEquals(1,calls)
        calls=0
        val expired=TwitchDeviceGrant({path,_->if(path=="device") device(11) else {calls++;pending()}},{testScheduler.currentTime})
        assertEquals(TwitchFailureKind.EXPIRED,failKind { expired.login(client) {} });assertEquals(2,calls)
    }
    @Test fun rateLimitAndNetworkInterruptionRemainBoundedByExpiry() = runTest {
        val calls=mutableListOf<Long>()
        val grant=TwitchDeviceGrant({path,_->if(path=="device") device(180) else {
            calls+=testScheduler.currentTime
            when(calls.size) {1 -> throw IOException("synthetic private detail");2 -> CompanionResponse(429,"", "60");else -> tokens()}
        }},{testScheduler.currentTime})
        grant.login(client) {}
        assertEquals(listOf(5000L,15000L,75000L),calls)
    }
    @Test fun backAndBackgroundCancelWithoutAnySubsequentTokenRequest() = runTest {
        var polls=0;val shown=CompletableDeferred<Unit>()
        val grant=TwitchDeviceGrant({path,_->if(path=="device") device() else { polls++;pending()}},{testScheduler.currentTime})
        val work=launch {grant.login(client) {shown.complete(Unit)}}
        shown.await();work.cancelAndJoin();assertEquals(0,polls)
        testScheduler.advanceTimeBy(120000);assertEquals(0,polls)
    }
    @Test fun unexpectedResponsesNeverExposeResponseBodiesInErrors() = runTest {
        for (response in listOf(CompanionResponse(403,"private token"),CompanionResponse(200,"private token"),CompanionResponse(400,"""{"message":"Invalid client_id private token"}"""))) {
            val grant=TwitchDeviceGrant({_,_->response},{testScheduler.currentTime})
            try {grant.login(client) {};fail("Accepted invalid response")} catch (e:TwitchFailure) {
                assertFalse(e.message.orEmpty().contains("private"));assertNull(e.cause)
            }
        }
    }
    @Test fun tokenValidationRejectsWrongClientMissingRefreshAndInvalidExpiry() {
        TwitchDeviceGrant.validateTokens(JSONObject(tokens().body))
        TwitchDeviceGrant.validateIdentity(JSONObject("""{"client_id":"$client","expires_in":14400}"""),client)
        for (value in listOf(JSONObject("""{"client_id":"other-client","expires_in":14400}"""),JSONObject("""{"client_id":"$client","expires_in":0}"""))) {
            try {TwitchDeviceGrant.validateIdentity(value,client);fail("Accepted wrong identity")} catch (_:IllegalArgumentException) {}
        }
        try {TwitchDeviceGrant.validateTokens(JSONObject("""{"access_token":"synthetic","expires_in":100,"token_type":"bearer"}"""));fail("Accepted missing refresh token")} catch (_:Exception) {}
    }
    @Test fun diagnosticsDistinguishDeviceAndTokenFailuresWithoutExposingBodies() = runTest {
        for (stage in listOf(TwitchStage.DEVICE,TwitchStage.POLL)) {
            val grant=TwitchDeviceGrant({path,_->if(path=="device" && stage==TwitchStage.POLL) device()
                else CompanionResponse(403,"synthetic secret must never appear")},{testScheduler.currentTime})
            try {grant.login(client) {};fail("Expected access refusal")} catch(error:TwitchFailure) {
                assertEquals(stage,error.stage);assertEquals(403,error.status)
                assertEquals(TwitchFailureKind.ACCESS,error.kind);assertFalse(error.toString().contains("secret"));assertNull(error.cause)
            }
        }
    }
    @Test fun officialDeviceResponseWithoutPublicQueryFlagContinuesIntoTokenPolling() = runTest {
        var displayed=false;var polls=0
        val response=device().copy(body=device().body.replace("public=true&", ""))
        val grant=TwitchDeviceGrant({path,_->if(path=="device") response else {polls++;tokens()}},{testScheduler.currentTime})
        grant.login(client) { activation -> displayed=true;assertEquals("https://www.twitch.tv/activate?device-code=ABCDEFGH",activation.verificationUri) }
        assertTrue(displayed);assertEquals(1,polls)
    }
}
