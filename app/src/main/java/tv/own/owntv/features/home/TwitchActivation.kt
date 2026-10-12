package tv.own.owntv.features.home

import java.net.URI
import java.net.URLDecoder

/** Only Twitch's documented public-client activation page may be displayed. No redirects. */
internal data class TwitchActivation(val code: String, val verificationUri: String) {
    companion object {
        fun parse(code: String, value: String): TwitchActivation {
            require(code.matches(Regex("[A-Z0-9]{4,16}")) && value.length <= 512)
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host == "www.twitch.tv" && uri.path == "/activate" &&
                uri.port == -1 && uri.userInfo == null && uri.fragment == null)
            val fields = uri.rawQuery.orEmpty().split('&').map {
                val pair = it.split('=', limit = 2)
                require(pair.size == 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
            // The official endpoint also returns only device-code (verified live 2026-10-12).
            // Preserve that exact address. Optional public must still be true; never synthesize it.
            val keys = fields.map { it.first }
            val values = fields.toMap()
            require(fields.size in 1..2 && keys.distinct().size == keys.size &&
                keys.all { it in setOf("public", "device-code") } && values["device-code"] == code &&
                ("public" !in values || values["public"] == "true"))
            return TwitchActivation(code, uri.toASCIIString())
        }
    }
}
