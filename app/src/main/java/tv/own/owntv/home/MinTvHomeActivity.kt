package tv.own.owntv.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import tv.own.owntv.R

/** Stable HOME component, independent of the IPTV icon activities and their task. */
class MinTvHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var selected by rememberSaveable { mutableIntStateOf(0) }
                val focus = remember { listOf(FocusRequester(), FocusRequester()) }
                LaunchedEffect(Unit) { focus[selected].requestFocus() }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { focus[selected].requestFocus() }
                // A HOME root stays home on Back; IPTV and Settings keep their own Back behavior.
                BackHandler { }
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF101A24)).padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Button(
                            onClick = { open(MinTvIntents.liveTv(this@MinTvHomeActivity)) },
                            modifier = Modifier.width(240.dp).focusRequester(focus[0])
                                .onFocusChanged { if (it.isFocused) selected = 0 },
                        ) {
                            Text(stringResource(R.string.mintv_home_live), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { open(MinTvIntents.settings()) },
                            modifier = Modifier.width(240.dp).focusRequester(focus[1])
                                .onFocusChanged { if (it.isFocused) selected = 1 },
                        ) {
                            Text(stringResource(R.string.mintv_home_settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.mintv_home_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.mintv_home_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
