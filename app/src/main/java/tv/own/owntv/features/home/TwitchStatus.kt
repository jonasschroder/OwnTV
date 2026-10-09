package tv.own.owntv.features.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal sealed interface TwitchState {
    data object Unavailable : TwitchState
    data object Offline : TwitchState
    data class Live(val title: String, val viewers: Int) : TwitchState
}

/** No thumbnails, player, scopes, embedded secret or server. Optional public-client device login. */
internal class TwitchStatus(context: Context, client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val authMutex = Mutex()
    private val file = AtomicFile(File(context.noBackupFilesDir, "mintv-twitch.enc"))
    private var validatedAt = 0L

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "mintv-twitch"
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun read(): JSONObject? = runCatching {
        require(file.baseFile.length() in 29..16_384)
        val bytes = file.openRead().use { it.readBytes() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
    }.getOrNull()

    private fun save(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toString().toByteArray())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    suspend fun disconnect() = authMutex.withLock { withContext(Dispatchers.IO) { file.delete(); validatedAt = 0 } }

    suspend fun login(clientId: String, onCode: (String) -> Unit) = authMutex.withLock { withContext(Dispatchers.IO) {
        require(clientId.matches(Regex("[a-zA-Z0-9]{8,128}")))
        val device = post("device", mapOf("client_id" to clientId, "scopes" to ""))
        require(device.first == 200)
        val data = JSONObject(device.second)
        val code = data.getString("user_code")
        require(code.matches(Regex("[A-Z0-9]{4,16}")))
        onCode(code)
        val until = System.currentTimeMillis() + data.getLong("expires_in").coerceIn(1, 1800) * 1000
        val interval = data.getLong("interval").coerceIn(5, 60) * 1000
        while (System.currentTimeMillis() < until) {
            delay(interval)
            val result = post("token", mapOf("client_id" to clientId, "scopes" to "", "device_code" to data.getString("device_code"),
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
            if (result.first == 200) {
                val tokens = JSONObject(result.second)
                require(tokens.getString("access_token").length in 1..4096)
                save(tokens.put("client_id", clientId).put("expires_at", System.currentTimeMillis() + tokens.getLong("expires_in") * 1000))
                validatedAt = 0
                return@withContext
            }
            require(result.first == 400 && JSONObject(result.second).optString("message") == "authorization_pending")
        }
        error("expired")
    } }

    suspend fun status(now: Long): TwitchState = authMutex.withLock { withContext(Dispatchers.IO) {
        var token = read() ?: return@withContext TwitchState.Unavailable
        val clientId = token.getString("client_id")
        if (token.getLong("expires_at") <= now + 60_000) {
            // Public-client refresh rotates single-use refresh tokens; never retry transparently.
            val refreshed = post("token", mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to token.getString("refresh_token")))
            if (refreshed.first != 200) { file.delete(); return@withContext TwitchState.Unavailable }
            token = JSONObject(refreshed.second).put("client_id", clientId)
            token.put("expires_at", now + token.getLong("expires_in") * 1000)
            save(token)
            validatedAt = 0
        }
        val access = token.getString("access_token")
        if (validatedAt == 0L || now - validatedAt >= 60 * 60_000L) {
            val validation = client.companionRequest(Request.Builder().url("https://id.twitch.tv/oauth2/validate")
                .header("Authorization", "OAuth $access").build(), 16_384)
            if (validation.first == 401) file.delete()
            require(validation.first == 200 && JSONObject(validation.second).getString("client_id") == clientId)
            validatedAt = now
        }
        val response = client.companionRequest(Request.Builder().url("https://api.twitch.tv/helix/streams?user_login=ohnepixel")
            .header("Client-ID", clientId).header("Authorization", "Bearer $access").build(), 32_768)
        if (response.first == 401) { file.delete(); validatedAt = 0 }
        require(response.first == 200)
        parse(response.second)
    } }

    private suspend fun post(path: String, fields: Map<String, String>): Pair<Int, String> =
        client.companionRequest(Request.Builder().url("https://id.twitch.tv/oauth2/$path").post(FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()).build(), 16_384)

    companion object {
        fun parse(body: String): TwitchState {
            val rows = JSONObject(body).getJSONArray("data")
            if (rows.length() == 0) return TwitchState.Offline
            require(rows.length() == 1)
            val stream = rows.getJSONObject(0)
            require(stream.getString("user_login").equals("ohnepixel", ignoreCase = true) && stream.getString("type") == "live")
            val count = stream.getInt("viewer_count")
            require(count >= 0)
            return TwitchState.Live(stream.getString("title").take(240), count)
        }
    }
}
