package tv.own.owntv.features.update

/** Phase A safety gate. Core's ABI-only release selection cannot separate Min TV Test and Stable.
 * Replace the host adapter in Phase C before enabling any public update channel; never change Core's pin.
 */
internal object MinTvUpdateGate {
    const val legacyUpdaterAllowed = false
}
