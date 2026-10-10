package tv.own.owntv.features.update

import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Pure, bounded trust policy. Only authenticated bytes determine APK identity/location. */
internal object UpdatePolicy {
    const val REPO = "jonasschroder/OwnTV"
    const val MAX_MANIFEST = 40_000
    const val MAX_APK = 200_000_000L
    const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
    data class Release(val code: Long, val tag: String, val assets: Set<String>)
    data class Candidate(val channel: String, val applicationId: String, val code: Long, val version: String,
        val apk: String, val hash: String, val bytes: Long, val minSdk: Int, val abis: Set<String>, val notes: String,
        val tag: String) {
        val url: String get() = "https://github.com/$REPO/releases/download/$tag/$apk"
    }
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun sha(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    fun packageFor(channel: String): String = when (channel) {
        "qa" -> "se.jonasschroder.mintv.qa"
        "stable" -> "se.jonasschroder.mintv"
        else -> error("Unknown channel")
    }
    fun releases(raw: String, channel: String): List<Release> {
        require(raw.toByteArray().size <= 1_000_000)
        packageFor(channel)
        val list = JSONArray(raw); require(list.length() <= 20)
        val found = mutableListOf<Release>()
        for (i in 0 until list.length()) {
            val entry = list.getJSONObject(i)
            if (entry.optBoolean("draft", true) || entry.optBoolean("prerelease", false) != (channel == "qa")) continue
            val tag = entry.optString("tag_name")
            if (!tag.matches(Regex("$channel-[1-9][0-9]{6,9}"))) continue
            val code = tag.substringAfter('-').toLong()
            require(code in 1_000_001..2_100_000_000)
            val assets = entry.getJSONArray("assets")
            require(assets.length() <= 20)
            val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
            require(names.count { it == "MinTV-$channel-update.json" } == 1 && names.distinct().size == names.size)
            found += Release(code, tag, names.toSet())
        }
        return found.sortedByDescending { it.code }
    }
    fun authenticate(raw: ByteArray, channel: String, pin: String, tag: String): Candidate {
        require(raw.size <= MAX_MANIFEST && pin.matches(Regex("[0-9a-f]{64}")))
        val envelope = JSONObject(raw.toString(Charsets.UTF_8))
        require(envelope.getString("algorithm") == "SHA256withRSA")
        val cert = Base64.getDecoder().decode(envelope.getString("certificate"))
        val body = Base64.getDecoder().decode(envelope.getString("payload"))
        val signature = Base64.getDecoder().decode(envelope.getString("signature"))
        require(cert.size in 256..8192 && body.size <= 24_000 && signature.size in 256..1024)
        require(sha(cert) == pin)
        val publicKey = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert)).publicKey
        require(publicKey.algorithm == "RSA")
        require(Signature.getInstance("SHA256withRSA").run { initVerify(publicKey); update(body); verify(signature) })
        val json = JSONObject(body.toString(Charsets.UTF_8))
        require(json.getInt("schema") == 1 && json.getString("channel") == channel)
        require(json.getString("application_id") == packageFor(channel))
        val code = json.getLong("version_code"); require(code in 1_000_001..2_100_000_000)
        require(tag == "$channel-$code" && json.getString("tag") == tag)
        require(json.getString("source_commit").matches(Regex("[0-9a-f]{40}")))
        val version = json.getString("version_name")
        val number = "(0|[1-9][0-9]*)"
        require(version.length <= 80 && version.matches(Regex("$number\\.$number\\.$number" + if (channel == "qa") "-beta\\.[1-9][0-9]*" else "")))
        val prefix = if (channel == "qa") "MinTV-Test" else "MinTV"
        val apk = json.getString("apk"); require(apk == "$prefix-v$version-$code-arm.apk")
        val hash = json.getString("apk_sha256"); require(hash.matches(Regex("[0-9a-f]{64}")))
        val bytes = json.getLong("apk_bytes"); require(bytes in 1..MAX_APK)
        val sdk = json.getInt("min_sdk"); require(sdk in 26..34)
        val array = json.getJSONArray("abis"); require(array.length() == 2)
        val abis = (0 until array.length()).map { array.getString(it) }.toSet()
        require(abis == setOf("arm64-v8a", "armeabi-v7a"))
        val notes = json.getString("notes"); require(notes.toByteArray().size <= 16_000)
        return Candidate(channel, packageFor(channel), code, version, apk, hash, bytes, sdk, abis, notes, tag)
    }
    fun compatible(candidate: Candidate, installedCode: Long, highestSeen: Long, sdk: Int, abis: List<String>): Boolean =
        candidate.code > installedCode && candidate.code >= highestSeen && candidate.minSdk <= sdk && abis.any { it in candidate.abis }

    /** Redirects are manual, HTTPS-only, bounded and host-specific. No token or user URL involved. */
    fun allowedUrl(value: String, initial: String): Boolean = runCatching {
        val uri = URI(value); val origin = URI(initial)
        val trustedOrigin = origin.scheme == "https" && origin.userInfo == null && origin.port == -1 && origin.fragment == null && (
            (origin.host == "api.github.com" && origin.path == "/repos/$REPO/releases" && origin.query?.matches(Regex("per_page=20(&page=[1-3])?")) == true) ||
            (origin.host == "github.com" && origin.query == null && origin.path.startsWith("/$REPO/releases/download/") && !origin.path.contains("..")))
        if (!trustedOrigin) return false
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.fragment != null) return false
        if (value == initial) return true
        origin.host == "github.com" && (uri.host == "release-assets.githubusercontent.com" || uri.host == "objects.githubusercontent.com") && uri.path.startsWith("/")
    }.getOrDefault(false)
}
