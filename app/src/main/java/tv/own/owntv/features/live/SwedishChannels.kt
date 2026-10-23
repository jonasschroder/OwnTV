package tv.own.owntv.features.live

import java.text.Normalizer
import java.util.Locale

internal enum class ChannelQuality { FHD, HD, SD, UNKNOWN, UHD;
    // Keep declaration order stable; advertised quality is not measured stream quality.
    val automaticRank: Int get() = when (this) {
        HD -> 0
        SD -> 1
        FHD -> 2
        UNKNOWN -> 3
        UHD -> 4
    }
}
internal enum class SwedishCollection { GENERAL, SPORT, NEWS, CHILDREN, OTHER }
internal data class SwedishName(val identity: String, val label: String, val quality: ChannelQuality,
    val collection: SwedishCollection, val order: Int)
internal data class SwedishVariant(val id: Long, val sourceId: Long, val stableKey: String, val rawName: String,
    val name: SwedishName, val number: Int?)
internal data class SwedishGroup(val identity: String, val label: String, val variants: List<SwedishVariant>) {
    fun preferred(key: String?, showUhd: Boolean): SwedishVariant? = variants.firstOrNull { it.stableKey == key }
        ?: variants.filter { showUhd || it.name.quality != ChannelQuality.UHD }
            .minWithOrNull(compareBy<SwedishVariant> { it.name.quality.automaticRank }.thenBy { it.sourceId }.thenBy { it.id })
}

/** Names are evidence of identity/advertised quality, never proof of stream reliability. */
internal object SwedishChannels {
    private val quality = Regex("(?i)(?<![a-z0-9])(?:ultra[ -]?hd|3840[x×]2160|2160p|1080p|720p|576p|480p|4k|uhd|fhd|hd|sd)(?![a-z0-9])")
    private val country = Regex("^(SE|SWE|SVERIGE|SWEDEN|[A-Z]{2})\\s*[:|]\\s*", RegexOption.IGNORE_CASE)
    private val suffix = Regex("\\s+(SE|SWE|SVERIGE|SWEDEN)$", RegexOption.IGNORE_CASE)
    private val general = listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6", "Sjuan", "TV8", "Kanal 9", "TV10", "Kanal 11", "TV12", "SVT24", "SVT Barn", "Kunskapskanalen")
    private val known = general + listOf("TV4 Hockey", "TV4 Sport Live 1", "TV4 Sport Live 2", "TV4 Sport Live 3", "TV4 Sport Live 4", "TV4 Sportkanalen", "TV4 Fotboll", "V Sport Premium", "V Sport Hockey", "V Sport Motor", "V Sport Vinter", "V Sport Extra", "V Sport 1", "V Sport 2", "V Sport 3", "Eurosport 1", "Eurosport 2", "BBC News", "CNN", "Al Jazeera", "Nickelodeon", "Nick Jr", "Disney Channel", "Cartoon Network")
    fun recognize(raw: String): SwedishName? {
        if (raw.length !in 1..512) return null
        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        val prefix = country.find(value)
        if (prefix != null && prefix.groupValues[1].uppercase(Locale.ROOT) !in setOf("SE", "SWE", "SVERIGE", "SWEDEN")) return null
        val tagged = prefix != null || suffix.containsMatchIn(value)
        if (prefix != null) value = value.substring(prefix.range.last + 1)
        value = suffix.replace(value, "")
        val labels = quality.findAll(value).map { it.value.lowercase(Locale.ROOT).replace(" ", "").replace("-", "") }.toSet()
        val q = when {
            labels.any { it in setOf("4k", "uhd", "ultrahd", "2160p", "3840x2160", "3840×2160") } -> ChannelQuality.UHD
            labels.any { it in setOf("fhd", "1080p") } -> ChannelQuality.FHD
            labels.any { it in setOf("hd", "720p") } -> ChannelQuality.HD
            labels.any { it in setOf("sd", "576p", "480p") } -> ChannelQuality.SD
            else -> ChannelQuality.UNKNOWN
        }
        value = quality.replace(value, " ").replace(Regex("\\s+"), " ").trim().trim('|', ':', '-', ' ')
        // Remove a Swedish suffix that was separated by a quality label (TV4 HD SE).
        value = suffix.replace(value, "").trim()
        val compact = value.lowercase(Locale.ROOT).replace(" ", "")
        val exact = known.firstOrNull { it.lowercase(Locale.ROOT).replace(" ", "") == compact }
            ?: if (compact == "tv7") "Sjuan" else null
        // Unknown suffixes (regions, +1, audio, alternate broadcasts) stay in a distinct identity.
        // Only country-tagged unknown names enter Other; untagged uncertain names stay in All.
        if (exact == null && !tagged) return null
        if (compact.isEmpty() || compact == "tv4playhockey") return null // An OTT service is not a linear channel.
        val label = exact ?: value
        val collection = when {
            label in general -> SwedishCollection.GENERAL
            exact?.contains("Sport", true) == true || exact?.contains("Hockey", true) == true || exact?.startsWith("Eurosport") == true || exact == "TV4 Fotboll" -> SwedishCollection.SPORT
            exact in listOf("BBC News", "CNN", "Al Jazeera") -> SwedishCollection.NEWS
            exact in listOf("Nickelodeon", "Nick Jr", "Disney Channel", "Cartoon Network") -> SwedishCollection.CHILDREN
            else -> SwedishCollection.OTHER
        }
        return SwedishName(label.lowercase(Locale.ROOT).replace(" ", ""), label, q, collection,
            general.indexOf(label).takeIf { it >= 0 } ?: 100)
    }
    fun group(variants: List<SwedishVariant>, order: List<String> = emptyList(), hidden: Set<String> = emptySet()): List<SwedishGroup> =
        variants.filter { it.name.identity !in hidden }.groupBy { it.name.identity }.mapNotNull { (id, rows) ->
            // A name-only key occurring on different provider rows cannot identify a saved source.
            val unambiguous = rows.groupBy { it.stableKey }.values.mapNotNull { it.distinctBy { row -> row.id }.singleOrNull() }
            if (unambiguous.isEmpty()) null else SwedishGroup(id, unambiguous.first().name.label, unambiguous)
        }.sortedWith(compareBy<SwedishGroup> { order.indexOf(it.identity).takeIf { n -> n >= 0 } ?: (order.size + it.variants.first().name.order) }.thenBy { it.label })
}
