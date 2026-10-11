package tv.own.owntv.features.home

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.util.CrashRecorder

/** No provider URLs, fixture/team/profile names, IDs, exception messages or token values. */
internal object SportsDiagnostics {
    enum class Event { HOME, MATCHCENTER, PROFILE_CHANGED, LOADING, DATA, FAILURE, CANCELLED, DETACHED_FOCUS }
    private val events = ArrayDeque<String>()
    @Synchronized fun record(event: Event, stage: HockeyStage? = null) {
        events.addLast("${System.currentTimeMillis()} ${event.name}${stage?.let { " ${it.name}" }.orEmpty()}")
        while (events.size > 48) events.removeFirst()
    }
    @Synchronized fun snapshot(): String = events.joinToString("\n")
    @Synchronized fun failure(error: Exception, stage: HockeyStage) {
        record(Event.FAILURE, stage)
        sanitizedStack(error.stackTraceToString()).lineSequence().take(12).forEach {
            events.addLast("SPORTS_FRAME $it")
        }
        while (events.size > 48) events.removeFirst()
    }

    /** Keep exception types and exact source frames, never arbitrary exception text. Fail closed. */
    fun sanitizedStack(raw: String): String = raw.lineSequence().take(1500).mapNotNull { line ->
        val text = line.trim()
        when {
            text.matches(Regex("at [a-zA-Z0-9_.$<>]+\\([a-zA-Z0-9_.$]+(?::[0-9]+)?\\)")) -> text
            else -> Regex("^(?:Caused by: |Suppressed: )?([a-zA-Z_$][a-zA-Z0-9_.$]*(?:Exception|Error))(?:[: ].*)?$")
                .matchEntire(text)?.let { it.groupValues[1] }
        }
    }.take(300).joinToString("\n")

    /** IO caller; this supplements rather than replaces Core's original crash recorder/export. */
    fun export(context: Context): String? { return try {
        val text = buildString {
            appendLine("Min TV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${BuildConfig.APPLICATION_ID}; Android API ${Build.VERSION.SDK_INT}")
            appendLine("Original crash frames (messages removed):")
            appendLine(CrashRecorder.read(context)?.let(::sanitizedStack).orEmpty())
            if (Build.VERSION.SDK_INT >= 30) {
                val manager = context.getSystemService(ActivityManager::class.java)
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 3).forEach {
                    appendLine("exit: timestamp=${it.timestamp} reason=${it.reason} status=${it.status}")
                    appendLine("memory: pssKb=${it.pss} rssKb=${it.rss}")
                }
            }
            appendLine("Current sports context:")
            appendLine(snapshot())
            // Preserve only our enum-only ring from the previous process' crash diagnostics.
            appendLine("Previous sports context:")
            CrashRecorder.read(context)?.lineSequence()?.filter {
                it.matches(Regex("[0-9]{13} (HOME|MATCHCENTER|PROFILE_CHANGED|LOADING|DATA|FAILURE|CANCELLED|DETACHED_FOCUS)( (SCHEDULE|TABLE))?")) ||
                    (it.startsWith("SPORTS_FRAME ") && sanitizedStack(it.removePrefix("SPORTS_FRAME ")) == it.removePrefix("SPORTS_FRAME "))
            }?.takeLastBounded(48)?.forEach { appendLine(it) }
        }
        if (Build.VERSION.SDK_INT < 29) {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val file = java.io.File(directory, "mintv-sports-crash-report.txt")
            file.writeText(text)
            return file.absolutePath
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "mintv-sports-crash-report.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            checkNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            "Download/mintv-sports-crash-report.txt"
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    } catch (_: Exception) { null } }
}

private fun Sequence<String>.takeLastBounded(count: Int): List<String> {
    val result = ArrayDeque<String>()
    for (line in take(1500)) { result.addLast(line); if (result.size > count) result.removeFirst() }
    return result.toList()
}
