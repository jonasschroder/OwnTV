package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.features.home.*

/** Fresh CI app's Android Keystore only; isolated encrypted file and entirely synthetic transport. */
@RunWith(AndroidJUnit4::class)
class TwitchTokenStorageTest {
    @Test fun validatedTokensStayEncryptedAndTransientRefreshDoesNotDeleteSignIn():Unit=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(app.cacheDir,"twitch-v05-fixture-"+java.util.UUID.randomUUID()).apply{mkdirs()}
        val context=object:ContextWrapper(app){override fun getNoBackupFilesDir()=directory}
        val clientId="syntheticpublicclient"
        var polls=0;var refreshStatus=500
        val client=OkHttpClient.Builder().addInterceptor {chain ->
            val request=chain.request();assertTrue(request.url.host in setOf("id.twitch.tv","api.twitch.tv"))
            val (code,body)=when(request.url.encodedPath){
                "/oauth2/device" -> 200 to """{"device_code":"synthetic-device","user_code":"ABCDEFGH","verification_uri":"https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH","expires_in":30,"interval":5}"""
                "/oauth2/token" -> {polls++;if(polls==1)200 to """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":1,"token_type":"bearer"}""" else refreshStatus to """{"message":"Synthetic refresh response"}"""}
                "/oauth2/validate" -> 200 to """{"client_id":"$clientId","expires_in":1}"""
                else -> error("Unexpected Twitch fixture endpoint")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            val status=TwitchStatus(context,client)
            status.login(clientId){assertEquals("ABCDEFGH",it.code)}
            assertTrue(status.connected())
            val file=File(directory,"mintv-twitch.enc")
            assertTrue(file.exists());assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("synthetic-access"))
            try{status.status(System.currentTimeMillis());fail("Transient failure did not surface")}catch(e:TwitchFailure){assertEquals(TwitchFailureKind.NETWORK,e.kind)}
            assertTrue(status.connected());assertTrue(file.exists());assertEquals(2,polls)
            refreshStatus=400
            assertEquals(TwitchState.Unavailable,status.status(System.currentTimeMillis()))
            assertFalse(status.connected());assertEquals(3,polls)
        }finally{directory.deleteRecursively();client.connectionPool.evictAll()}
    }
}
