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

    suspend fun connected(): Boolean = authMutex.withLock { withContext(Dispatchers.IO) { read() != null } }

    suspend fun disconnect() = authMutex.withLock { withContext(Dispatchers.IO) { file.delete(); validatedAt = 0 } }

    suspend fun login(clientId: String, onCode: (TwitchActivation) -> Unit) = authMutex.withLock { withContext(Dispatchers.IO) {
        val tokens = TwitchDeviceGrant(post = { path, fields -> postResponse(path, fields) }).login(clientId) { activation ->
            withContext(Dispatchers.Main.immediate) { onCode(activation) }
        }
        val validation = try { client.companionResponse(Request.Builder().url("https://id.twitch.tv/oauth2/validate")
            .header("Authorization", "OAuth ${tokens.getString("access_token")}").build(), 16_384) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: CompanionResponseTooLarge) { throw TwitchFailure(TwitchFailureKind.RESPONSE, TwitchStage.VALIDATION) }
        catch (_: java.io.InterruptedIOException) { throw TwitchFailure(TwitchFailureKind.TIMEOUT, TwitchStage.VALIDATION) }
        catch (_: java.io.IOException) { throw TwitchFailure(TwitchFailureKind.NETWORK, TwitchStage.VALIDATION) }
        if (validation.status != 200) throw TwitchFailure(twitchError(validation), TwitchStage.VALIDATION, validation.status)
        try { TwitchDeviceGrant.validateIdentity(JSONObject(validation.body), clientId) }
        catch (_: Exception) { throw TwitchFailure(TwitchFailureKind.AUTHENTICATION, TwitchStage.VALIDATION, validation.status) }
        try { save(tokens.put("client_id", clientId).put("expires_at", System.currentTimeMillis() + tokens.getLong("expires_in") * 1000)) }
        catch (_: Exception) { throw TwitchFailure(TwitchFailureKind.STORAGE, TwitchStage.STORAGE) }
        validatedAt = System.currentTimeMillis()
    } }

    suspend fun status(now: Long): TwitchState = authMutex.withLock { withContext(Dispatchers.IO) {
        var token = read() ?: return@withContext TwitchState.Unavailable
        val clientId = token.getString("client_id")
        if (token.getLong("expires_at") <= now + 60_000) {
            // Public-client refresh rotates single-use refresh tokens; never retry transparently.
            val refreshed = post("token", mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to token.getString("refresh_token")))
            if (refreshed.first in listOf(400, 401)) { file.delete(); return@withContext TwitchState.Unavailable }
            if (refreshed.first != 200) throw TwitchFailure(twitchError(CompanionResponse(refreshed.first, "")), TwitchStage.REFRESH, refreshed.first)
            token = JSONObject(refreshed.second).also { TwitchDeviceGrant.validateTokens(it) }.put("client_id", clientId)
            token.put("expires_at", now + token.getLong("expires_in") * 1000)
            save(token)
            validatedAt = 0
        }
        val access = token.getString("access_token")
        if (validatedAt == 0L || now - validatedAt >= 60 * 60_000L) {
            val validation = client.companionRequest(Request.Builder().url("https://id.twitch.tv/oauth2/validate")
                .header("Authorization", "OAuth $access").build(), 16_384)
            if (validation.first == 401) file.delete()
            if (validation.first != 200) throw TwitchFailure(twitchError(CompanionResponse(validation.first, "")), TwitchStage.VALIDATION, validation.first)
            try { TwitchDeviceGrant.validateIdentity(JSONObject(validation.second), clientId) }
            catch (_: Exception) { file.delete(); validatedAt = 0; throw TwitchFailure(TwitchFailureKind.AUTHENTICATION, TwitchStage.VALIDATION, validation.first) }
            validatedAt = now
        }
        val response = client.companionRequest(Request.Builder().url("https://api.twitch.tv/helix/streams?user_login=ohnepixel")
            .header("Client-ID", clientId).header("Authorization", "Bearer $access").build(), 32_768)
        if (response.first == 401) { file.delete(); validatedAt = 0 }
        if (response.first != 200) throw TwitchFailure(twitchError(CompanionResponse(response.first, "")), TwitchStage.STATUS, response.first)
        parse(response.second)
    } }

    private suspend fun post(path: String, fields: Map<String, String>): Pair<Int, String> =
        postResponse(path, fields).let { it.status to it.body }
    private suspend fun postResponse(path: String, fields: Map<String, String>): CompanionResponse =
        client.companionResponse(Request.Builder().url("https://id.twitch.tv/oauth2/$path").post(FormBody.Builder().apply {
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
