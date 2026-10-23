package tv.own.owntv.features.live.source

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogWindowProvider
import tv.own.owntv.ui.components.OwnTVPopup
import androidx.tv.material3.*
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.*
import tv.own.owntv.ui.theme.mpx

internal enum class SourcePickerError { UNAVAILABLE, SAVE_FAILED }
internal data class SourcePickerData(val group: SwedishGroup, val choices: SwedishChoices, val bounded: Boolean = false)

/** The dialog owns focus/selection only. Persistence and playback have one guarded caller. */
@Composable
internal fun ChannelSourcePicker(data: SourcePickerData?, openingDownTime: Long?, busy: Boolean,
    error: SourcePickerError?, onChoose: (SwedishVariant, Boolean) -> Unit, onDismiss: () -> Unit) {
    val result = data?.let { ChannelSourceResolver.resolve(it.group, it.choices) }
    val rows = result?.alternatives.orEmpty()
    var selected by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val targets = remember { mutableMapOf<String, FocusRequester>() }
    val secondary = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val initial = rows.firstOrNull { it.stableKey == data?.choices?.preferred?.get(data.group.identity) }
        ?: result?.selected ?: rows.firstOrNull()
    val selectedRow = rows.firstOrNull { it.stableKey == selected }
    OwnTVPopup(onDismissRequest = { if (!busy) onDismiss() }, dismissOnBackPress = !busy,
        dismissOnClickOutside = !busy, stageLayout = true) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused, rows.map { it.stableKey }, error, data != null) {
            if (!windowFocused) return@LaunchedEffect
            if (!entered) {
                selected = initial?.stableKey
                if (initial != null) {
                    list.scrollToItem(rows.indexOf(initial))
                    repeat(2) { withFrameNanos { } }
                    entered = targets[initial.stableKey]?.requestSafely() == true
                } else if (data != null || error != null) { repeat(2) { withFrameNanos { } }; entered = close.requestSafely() }
            } else if (selectedRow == null) { withFrameNanos { }; close.requestSafely() }
        }
        val view = LocalView.current
        DisposableEffect(view) {
            val hideBars = Runnable {
                var parent = view.parent
                while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
                (parent as? DialogWindowProvider)?.window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            view.post(hideBars)
            onDispose { view.removeCallbacks(hideBars) }
        }
        Box(Modifier.fillMaxSize().padding(24.mpx), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 1050.mpx).fillMaxWidth().heightIn(max = 900.mpx)
                .sourcePickerOpeningGuard(openingDownTime)
                .background(Color(0xFF192E38), RoundedCornerShape(26.mpx)).padding(38.mpx)
                .testTag("source-picker"), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                TvText(stringResource(R.string.mintv_source_title), size = 22, color = MinTvMuted, bold = true, lines = 1)
                if (data != null || error == null) TvText(data?.group?.label ?: stringResource(R.string.mintv_source_loading), size = 38, bold = true, lines = 2)
                TvText(stringResource(R.string.mintv_source_hint), size = 21, color = MinTvMuted, lines = 2)
                if (result?.status == ChannelSourceStatus.SAVED_MISSING)
                    TvText(stringResource(R.string.mintv_source_saved_missing), size = 19, color = MinTvTeal, lines = 3)
                if (data?.bounded == true) TvText(stringResource(R.string.mintv_source_partial), size = 18, color = MinTvMuted, lines = 2)
                error?.let { TvText(stringResource(if (it == SourcePickerError.SAVE_FAILED) R.string.mintv_source_save_failed
                    else R.string.mintv_source_unavailable), size = 21, color = Color(0xFFFFC5A3), lines = 3) }
                if (data != null && rows.isEmpty()) TvText(stringResource(R.string.mintv_source_ambiguous), size = 21, color = MinTvMuted, lines = 3)
                val sources = rows.map { it.sourceId }.distinct().sorted()
                LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false).heightIn(max = 380.mpx),
                    verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    items(rows, key = { it.stableKey }) { variant ->
                        val focus = remember(variant.stableKey) { targets.getOrPut(variant.stableKey) { FocusRequester() } }
                        val saved = data?.choices?.preferred?.get(data.group.identity) == variant.stableKey
                        val quality = if (variant.name.quality == ChannelQuality.UNKNOWN) stringResource(R.string.mintv_source_unknown_quality)
                            else variant.name.quality.name
                        // Ordinal source labels disclose no account names, URLs, usernames or tokens.
                        val label = stringResource(R.string.mintv_source_version, data!!.group.label, quality,
                            stringResource(R.string.mintv_source_provider, sources.indexOf(variant.sourceId) + 1))
                        Button(onClick = { if (!busy) onChoose(variant, true) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 76.mpx).focusRequester(focus)
                                .testTag("source-variant-${variant.id}").onFocusChanged { if (it.isFocused) selected = variant.stableKey }
                                .onPreviewKeyEvent { e ->
                                    if (e.key == Key.DirectionRight) {
                                        if (e.type == KeyEventType.KeyDown) secondary.requestSafely()
                                        true
                                    } else false
                                },
                            contentPadding = PaddingValues(horizontal = 22.mpx, vertical = 12.mpx),
                            shape = ButtonDefaults.shape(RoundedCornerShape(16.mpx)),
                            colors = ButtonDefaults.colors(containerColor = Color(0xFF203944), focusedContainerColor = Color(0xFF29454D)),
                            border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
                            scale = ButtonDefaults.scale(focusedScale = 1f)) {
                            Column(Modifier.weight(1f)) {
                                TvText(label, size = 23, bold = true, lines = 2)
                                if (saved) TvText(stringResource(R.string.mintv_source_saved), size = 18, color = MinTvTeal, lines = 1)
                            }
                            TvText(stringResource(R.string.mintv_source_play), size = 18, color = MinTvMuted, lines = 1)
                        }
                    }
                }
                Button(onClick = { selectedRow?.let { if (!busy) onChoose(it, false) } }, enabled = !busy && selectedRow != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.mpx).focusRequester(secondary).testTag("source-save-only")
                        .onPreviewKeyEvent { e ->
                            if (e.key == Key.DirectionLeft) {
                                if (e.type == KeyEventType.KeyDown) selected?.let { targets[it]?.requestSafely() }
                                true
                            } else false
                        }, shape = ButtonDefaults.shape(RoundedCornerShape(14.mpx)),
                    colors = ButtonDefaults.colors(containerColor = Color(0xFF29404A), focusedContainerColor = Color(0xFF365661)),
                    border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
                    scale = ButtonDefaults.scale(focusedScale = 1f)) {
                    TvText(stringResource(R.string.mintv_source_save_only), size = 21, lines = 2)
                }
                SmartAction(stringResource(R.string.mintv_close), { if (!busy) onDismiss() },
                    Modifier.focusRequester(close).testTag("source-close"), compact = true)
                TvText(stringResource(R.string.mintv_source_back_hint), size = 18, color = MinTvMuted, lines = 2)
            }
        }
    }
}
