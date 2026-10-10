package tv.own.owntv.features.update

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val raw = javaClass.getResourceAsStream("/updates/qa.json")!!.readBytes()
    private val envelope get() = JSONObject(raw.toString(Charsets.UTF_8))
    private val pin get() = UpdatePolicy.sha(Base64.getDecoder().decode(envelope.getString("certificate")))
    private fun verified() = UpdatePolicy.authenticate(raw, "qa", pin, "qa-1000102")
    private fun rejected(block: () -> Unit) { try { block(); fail("Untrusted input accepted") } catch (_: Exception) { } }
    private fun list(vararg entries: JSONObject) = JSONArray(entries.toList()).toString()
    private fun release(tag: String, prerelease: Boolean = true, asset: Boolean = true) = JSONObject()
        .put("tag_name", tag).put("prerelease", prerelease).put("draft", false)
        .put("assets", JSONArray(if (asset) listOf(JSONObject().put("name", "MinTV-qa-update.json")) else emptyList<JSONObject>()))

    @Test fun verifiesRealRsaSignatureAndExactApprovedIdentity() {
        val candidate = verified()
        assertEquals("se.jonasschroder.mintv.qa", candidate.applicationId)
        assertEquals(1000102L, candidate.code)
        assertEquals("https://github.com/jonasschroder/OwnTV/releases/download/qa-1000102/MinTV-Test-v0.2.0-beta.1-1000102-arm.apk", candidate.url)
    }
    @Test fun ownerPinCannotBeReplacedByCertificateInRemoteEnvelope() { rejected { UpdatePolicy.authenticate(raw, "qa", "ab".repeat(32), "qa-1000102") } }
    @Test fun anotherChannelCannotUseValidQaSignature() { rejected { UpdatePolicy.authenticate(raw, "stable", pin, "qa-1000102") } }
    @Test fun tagCannotRedirectSignedFixtureToDifferentRelease() { rejected { UpdatePolicy.authenticate(raw, "qa", pin, "qa-1000103") } }
    @Test fun tamperedPayloadIsRejectedBeforeJsonIsTrusted() {
        val changed = envelope
        changed.put("payload", Base64.getEncoder().encodeToString("{}".toByteArray()))
        rejected { UpdatePolicy.authenticate(changed.toString().toByteArray(), "qa", pin, "qa-1000102") }
    }
    @Test fun invalidSignatureAndAlgorithmAreRejected() {
        for (key in listOf("signature", "algorithm", "certificate")) {
            val changed = envelope.put(key, "invalid")
            rejected { UpdatePolicy.authenticate(changed.toString().toByteArray(), "qa", pin, "qa-1000102") }
        }
    }
    @Test fun oversizedAndMalformedMetadataFailClosed() {
        for (bytes in listOf(ByteArray(40001), "[]".toByteArray(), "{broken".toByteArray())) rejected { UpdatePolicy.authenticate(bytes, "qa", pin, "qa-1000102") }
    }
    @Test fun versionCodeRatherThanVersionNameControlsUpdates() {
        val candidate = verified()
        assertTrue(UpdatePolicy.compatible(candidate, 1000101, 1000102, 34, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.compatible(candidate, 1000102, 0, 34, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.compatible(candidate, 1000103, 0, 34, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.compatible(candidate, 1000101, 1000103, 34, listOf("arm64-v8a")))
    }
    @Test fun unsupportedAbiOrSdkCannotBeInstalled() {
        assertFalse(UpdatePolicy.compatible(verified(), 1, 0, 25, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.compatible(verified(), 1, 0, 34, listOf("x86_64")))
    }
    @Test fun channelsDraftsMissingMetadataAndMalformedTagsAreNotCandidates() {
        assertTrue(UpdatePolicy.releases("[]", "qa").isEmpty())
        assertTrue(UpdatePolicy.releases(list(release("stable-1000102", false), release("qa-1000102").put("draft", true), release("qa-1000102/evil")), "qa").isEmpty())
        rejected { UpdatePolicy.releases(list(release("qa-1000102", asset = false)), "qa") }
    }
    @Test fun highestCodeWinsIndependentlyOfGithubOrdering() {
        assertEquals(listOf(1000103L, 1000102L), UpdatePolicy.releases(list(release("qa-1000102"), release("qa-1000103")), "qa").map { it.code })
    }
    @Test fun malformedOrOversizedReleaseListingIsRejected() {
        rejected { UpdatePolicy.releases("{}", "qa") }
        rejected { UpdatePolicy.releases("x".repeat(1000001), "qa") }
        rejected { UpdatePolicy.releases(JSONArray(List(21) { release("qa-1000102") }).toString(), "qa") }
    }
    @Test fun onlyKnownHttpsAssetRedirectHostsAreAllowed() {
        val initial = verified().url
        assertTrue(UpdatePolicy.allowedUrl(initial, initial))
        assertTrue(UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/a?signature=public-test", initial))
        for (url in listOf("http://release-assets.githubusercontent.com/a", "https://evil.example/a", "https://github.com.evil.example/a", "https://user:pass@release-assets.githubusercontent.com/a", "https://release-assets.githubusercontent.com:444/a", "https://release-assets.githubusercontent.com/a#evil")) assertFalse(url, UpdatePolicy.allowedUrl(url, initial))
        assertFalse(UpdatePolicy.allowedUrl("https://evil.example/a", "https://evil.example/a"))
        assertFalse(UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/a", "https://api.github.com/repos/jonasschroder/OwnTV/releases?per_page=20"))
    }
}
