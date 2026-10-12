package tv.own.owntv.features.home

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

internal enum class TwitchFailureKind { CLIENT, ACCESS, DENIED, EXPIRED, NETWORK, TIMEOUT, RATE_LIMITED, RESPONSE, AUTHENTICATION, STORAGE }
internal enum class TwitchStage { DEVICE, POLL, VALIDATION, STORAGE, REFRESH, STATUS }
/** Only fixed enums/status codes. Never include server messages, codes, identifiers or token values. */
internal class TwitchFailure(val kind: TwitchFailureKind, val stage: TwitchStage? = null, val status: Int? = null) : Exception(kind.name) {
    init { require(status == null || status in 100..599) }
}
internal fun twitchError(response: CompanionResponse): TwitchFailureKind {
    val name = runCatching { val o = JSONObject(response.body); o.optString("error").ifBlank { o.optString("message") } }.getOrDefault("")
    return when {
        response.status == 403 -> TwitchFailureKind.ACCESS
        response.status == 429 -> TwitchFailureKind.RATE_LIMITED
        response.status >= 500 -> TwitchFailureKind.NETWORK
        name in listOf("access_denied", "authorization_declined") -> TwitchFailureKind.DENIED
        name in listOf("expired_token", "expired_device_code", "invalid device code") -> TwitchFailureKind.EXPIRED
        name.contains("client", ignoreCase = true) -> TwitchFailureKind.CLIENT
        response.status == 401 -> TwitchFailureKind.AUTHENTICATION
        else -> TwitchFailureKind.RESPONSE
    }
}

/** Official public-client protocol, injectable transport/monotonic clock for meaningful tests. */
internal class TwitchDeviceGrant(private val post: suspend (String, Map<String, String>) -> CompanionResponse,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    suspend fun login(clientId: String, onCode: suspend (TwitchActivation) -> Unit): JSONObject {
        if (!clientId.matches(Regex("[a-zA-Z0-9]{8,128}"))) throw TwitchFailure(TwitchFailureKind.CLIENT)
        val device = request("device", mapOf("client_id" to clientId, "scopes" to ""))
        if (device.status != 200) throw TwitchFailure(twitchError(device), TwitchStage.DEVICE, device.status)
        val data = parse { JSONObject(device.body) }
        val duration = parse { data.getLong("expires_in").also { require(it in 1..1800) } } * 1000
        var interval = parse { data.getLong("interval").also { require(it in 1..300) } }.coerceAtLeast(5) * 1000
        val deviceCode = parse { data.getString("device_code").also { require(it.length in 1..4096) } }
        val activation = parse { TwitchActivation.parse(data.getString("user_code"), data.getString("verification_uri")) }
        val until = now() + duration
        onCode(activation)
        while (now() < until) {
            delay(minOf(interval, until - now()))
            currentCoroutineContext().ensureActive()
            if (now() >= until) break
            val result = try { request("token", mapOf("client_id" to clientId, "scopes" to "", "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code")) }
            catch (error: TwitchFailure) {
                if (error.kind !in listOf(TwitchFailureKind.NETWORK, TwitchFailureKind.TIMEOUT)) throw error
                interval = maxOf(interval, 10_000) // Bounded by the original expiry, never busy retry.
                continue
            }
            if (result.status == 200) return parse(TwitchStage.POLL) { JSONObject(result.body).also(::validateTokens) }
            val message = runCatching { val o = JSONObject(result.body); o.optString("error").ifBlank { o.optString("message") } }.getOrDefault("")
            when {
                result.status == 400 && message == "authorization_pending" -> Unit
                result.status == 400 && message == "slow_down" -> interval += 5000
                result.status == 429 -> {
                    val retry = result.retryAfter?.let { value -> value.toLongOrNull()?.takeIf { it in 1..604_800 }?.times(1000)
                        ?: runCatching { java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli() - System.currentTimeMillis() }.getOrNull() } ?: 0
                    interval = maxOf(interval, retry, 60_000)
                }
                result.status >= 500 -> interval = maxOf(interval, 10_000)
                else -> throw TwitchFailure(twitchError(result), TwitchStage.POLL, result.status)
            }
        }
        throw TwitchFailure(TwitchFailureKind.EXPIRED, TwitchStage.POLL)
    }
    private suspend fun request(path: String, fields: Map<String, String>): CompanionResponse = try { post(path, fields) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.InterruptedIOException) { currentCoroutineContext().ensureActive(); throw TwitchFailure(TwitchFailureKind.TIMEOUT,
            if (path == "device") TwitchStage.DEVICE else TwitchStage.POLL) }
        catch (_: IOException) { currentCoroutineContext().ensureActive(); throw TwitchFailure(TwitchFailureKind.NETWORK,
            if (path == "device") TwitchStage.DEVICE else TwitchStage.POLL) }
    private fun <T> parse(stage: TwitchStage = TwitchStage.DEVICE, block: () -> T): T = try { block() }
        catch (error: TwitchFailure) { throw error }
        catch (_: Exception) { throw TwitchFailure(TwitchFailureKind.RESPONSE, stage, 200) }
    companion object {
        fun validateTokens(value: JSONObject) {
            require(value.getString("access_token").length in 1..4096 && value.getString("refresh_token").length in 1..4096)
            require(value.getLong("expires_in") in 1..2_678_400 && value.getString("token_type").equals("bearer", true))
        }
        fun validateIdentity(value: JSONObject, clientId: String) {
            require(value.getString("client_id") == clientId && value.getLong("expires_in") in 1..2_678_400)
        }
    }
}
