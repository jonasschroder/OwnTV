package tv.own.owntv

// One launcher activity per icon colour, so each can carry its own icon, banner and launch-screen theme
// (see the manifest and core's AppIconSwitcher). No code of their own: MainActivity is the app.
class MainActivitySunflower : MainActivity()
class MainActivityCobalt : MainActivity()
class MainActivityTomato : MainActivity()
class MainActivityBoard : MainActivity()
class MainActivityPetrol : MainActivity()
class MainActivityOlive : MainActivity()
class MainActivityOliveCream : MainActivity()
class MainActivityPixel : MainActivity()

/** The temporary Min TV mark is also used when animations are off. */
@Suppress("UNUSED_PARAMETER")
internal fun stillLaunchTheme(icon: tv.own.owntv.core.brand.AppIcon): Int = R.style.Theme_MinTV_Starting
