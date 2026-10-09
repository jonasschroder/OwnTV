package tv.own.owntv.home

/** Android delivers onNewIntent before onResume; decide restoration only after that delivery. */
class MinTvForegroundPolicy(
    private val discardRestore: () -> Unit,
    private val restore: () -> Unit,
) {
    private var normalEntry = true // a newly created Activity must not autoplay an old session

    fun onNormalAppEntry() { normalEntry = true }

    fun onResume(allowFullscreenResume: Boolean) {
        if (normalEntry || !allowFullscreenResume) {
            normalEntry = false
            discardRestore()
        } else {
            restore() // returning from another app/screensaver retains the existing fullscreen policy
        }
    }
}
