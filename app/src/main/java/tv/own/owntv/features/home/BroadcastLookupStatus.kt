package tv.own.owntv.features.home

/** Deliberately excludes fixture names, provider URLs, response bodies and exception messages. */
internal enum class BroadcastLookupOutcome {
    NOT_REQUESTED, DISABLED, ACCESS_BLOCKED, OUTSIDE_WINDOW, INVALID_FIXTURE, BACKOFF,
    RATE_LIMITED, CACHE_HIT, NO_LINEAR_CHANNEL, NOT_FOUND, AMBIGUOUS, FIELDS_REJECTED, VERIFIED,
    HTTP_ERROR, NETWORK_ERROR, FORMAT_ERROR, CACHE_ERROR, CANCELLED,
}
internal enum class BroadcastLookupStage { ELIGIBILITY, CACHE, ROBOTS, TERMS, LISTING, FIXTURE }
internal data class BroadcastLookupStatus(val outcome: BroadcastLookupOutcome = BroadcastLookupOutcome.NOT_REQUESTED,
    val at: Long? = null, val stage: BroadcastLookupStage = BroadcastLookupStage.ELIGIBILITY,
    val retryAt: Long? = null, val http: Int? = null)
internal class BroadcastLookupFailure(val outcome: BroadcastLookupOutcome, val http: Int? = null) : Exception()
