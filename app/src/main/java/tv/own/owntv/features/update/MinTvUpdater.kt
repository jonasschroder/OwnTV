package tv.own.owntv.features.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Response
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.settings.SettingsRepository

/** Foreground-only host adapter; pinned Core and IPTV stores are untouched. */
class MinTvUpdater(private val context: Context, private val settings: SettingsRepository) {
    internal sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val candidate: UpdatePolicy.Candidate) : State
        data class Downloading(val percent: Int) : State
        data object Permission : State
        data object Installing : State
        data object Confirmation : State
        data object Cancelled : State
        data class Failed(val reason: Problem) : State
    }
    internal enum class Problem { SETUP, NETWORK, RATE_LIMIT, INVALID, STORAGE, INSTALL, UNSUPPORTED }
    private class UpdateError(val problem: Problem) : IOException()
    private val mutableState = MutableStateFlow<State>(State.Idle)
    internal val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val receiverChanges = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "confirmation" && prefs.contains("confirmation")) mutableState.value = State.Confirmation
        if (key == "result") {
            val result = prefs.getInt("result", Int.MIN_VALUE)
            if (result == PackageInstaller.STATUS_FAILURE_ABORTED) mutableState.value = State.Cancelled
            else if (result != Int.MIN_VALUE && result != PackageInstaller.STATUS_SUCCESS) mutableState.value = State.Failed(installProblem(result))
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(receiverChanges) }
    private val directory = File(context.cacheDir, "verified-update")
    private val apk get() = File(directory, "candidate.apk")
    private val manifest get() = File(directory, "manifest.json")
    private val transport = UpdateTransport()
    @Volatile private var foreground = false
    private var candidate: UpdatePolicy.Candidate? = null
    val channel get() = if (context.packageName.endsWith(".qa")) "qa" else "stable"
    val channelLabel get() = context.getString(if (channel == "qa") tv.own.owntv.R.string.mintv_update_channel_test else tv.own.owntv.R.string.mintv_update_channel_stable)
    private val pin get() = BuildConfig.UPDATE_CERTIFICATE_SHA256
    @Suppress("DEPRECATION")
    private val installedCode get() = context.packageManager.getPackageInfo(context.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    }

    fun setForeground(value: Boolean) { foreground = value; transport.setForeground(value) }
    private fun signer(path: String? = null): String {
        if (Build.VERSION.SDK_INT < 28) throw UpdateError(Problem.UNSUPPORTED)
        val info = if (path == null) context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            else context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("Unreadable APK")
        val signers = info.signingInfo?.apkContentsSigners ?: error("No signer")
        require(signers.size == 1)
        return UpdatePolicy.sha(signers[0].toByteArray())
    }
    private fun ready() = !BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 28 && context.packageName == UpdatePolicy.packageFor(channel) && pin.matches(Regex("[0-9a-f]{64}")) && runCatching { signer() == pin }.getOrDefault(false)
    private fun installProblem(status: Int) = if (status == PackageInstaller.STATUS_FAILURE_STORAGE) Problem.STORAGE else Problem.INSTALL

    internal suspend fun check(force: Boolean = false) = lock.withLock {
        if (!foreground || mutableState.value is State.Downloading || mutableState.value is State.Installing || mutableState.value is State.Confirmation) return@withLock
        if (!ready()) { if (force) mutableState.value = State.Failed(Problem.SETUP); return@withLock }
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("blocked_until", 0)) { if (force) mutableState.value = State.Failed(Problem.RATE_LIMIT); return@withLock }
        if (now - prefs.getLong("attempt", 0) < if (force) 60_000 else UpdatePolicy.CHECK_INTERVAL) return@withLock
        prefs.edit().putLong("attempt", now).apply()
        mutableState.value = State.Checking
        try {
            withContext(Dispatchers.IO) {
                var release: UpdatePolicy.Release? = null
                for (page in 1..3) {
                    val raw = fetchBytes("https://api.github.com/repos/${UpdatePolicy.REPO}/releases?per_page=20&page=$page", 1_000_000).toString(Charsets.UTF_8)
                    val found = UpdatePolicy.releases(raw, channel)
                    if (found.isNotEmpty()) { release = found.first().takeIf { it.code > installedCode }; break }
                    if (org.json.JSONArray(raw).length() < 20) break
                    if (page == 3) throw UpdateError(Problem.INVALID)
                }
                if (release == null) { candidate = null; mutableState.value = State.UpToDate }
                else {
                    val selected = release
                    val bytes = fetchBytes("https://github.com/${UpdatePolicy.REPO}/releases/download/${selected.tag}/MinTV-$channel-update.json", UpdatePolicy.MAX_MANIFEST)
                    val verified = UpdatePolicy.authenticate(bytes, channel, pin, selected.tag)
                    if (verified.apk !in selected.assets) throw UpdateError(Problem.UNSUPPORTED)
                    if (!UpdatePolicy.compatible(verified, installedCode, prefs.getLong("highest", 0), Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())) throw UpdateError(Problem.UNSUPPORTED)
                    directory.mkdirs(); manifest.writeBytes(bytes)
                    if (prefs.getLong("download_code", 0) != verified.code) apk.delete()
                    prefs.edit().putString("tag", selected.tag).putLong("highest", verified.code).apply()
                    candidate = verified; mutableState.value = State.Available(verified)
                }
                settings.recordUpdateCheck(now)
            }
        } catch (cancel: CancellationException) { mutableState.value = State.Idle; throw cancel }
        catch (error: Exception) { mutableState.value = if (!foreground) State.Idle else State.Failed(problem(error)) }
    }
    private fun problem(error: Exception) = (error as? UpdateError)?.problem ?: if (error is IOException) Problem.NETWORK else Problem.INVALID
    private fun response(initial: String): Response = try { transport.response(initial) }
        catch (error: UpdateTransport.HttpFailure) {
            if (error.blockedUntil > 0) prefs.edit().putLong("blocked_until", error.blockedUntil).apply()
            throw UpdateError(if (error.status == 403 || error.status == 429) Problem.RATE_LIMIT else if (error.status == 404) Problem.INVALID else Problem.NETWORK)
        }
    private suspend fun fetchBytes(url: String, limit: Int): ByteArray = response(url).use { response ->
        require(response.body.contentLength() <= limit)
        response.body.byteStream().use { input ->
            val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { coroutineContext.ensureActive(); val size = input.read(buffer); if (size < 0) break; require(result.size() + size <= limit); result.write(buffer, 0, size) }
            result.toByteArray()
        }
    }
    internal suspend fun download() = lock.withLock {
        val info = candidate ?: return@withLock
        if (!ready() || !foreground) return@withLock
        mutableState.value = State.Downloading(0)
        val partial = File(directory, "candidate.part")
        try {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                if (StatFs(directory.path).availableBytes < info.bytes * 2 + 32 * 1024 * 1024) throw UpdateError(Problem.STORAGE)
                response(info.url).use { response ->
                    val length = response.body.contentLength(); require(length == -1L || length == info.bytes)
                    val digest = MessageDigest.getInstance("SHA-256"); var received = 0L
                    response.body.byteStream().use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive(); if (!foreground) throw CancellationException()
                            val size = input.read(buffer); if (size < 0) break
                            received += size; require(received <= info.bytes)
                            digest.update(buffer, 0, size); output.write(buffer, 0, size)
                            mutableState.value = State.Downloading((received * 100 / info.bytes).toInt())
                        }
                    } }
                    require(received == info.bytes && UpdatePolicy.hex(digest.digest()) == info.hash)
                }
                verifyApk(partial, info)
                apk.delete(); require(partial.renameTo(apk))
                prefs.edit().putLong("download_code", info.code).apply()
                if (context.packageManager.canRequestPackageInstalls()) install(info) else mutableState.value = State.Permission
            }
        } catch (cancel: CancellationException) { mutableState.value = State.Available(info); throw cancel }
        catch (error: Exception) { mutableState.value = if (!foreground) State.Available(info) else State.Failed(problem(error)) }
        finally { partial.delete() }
    }
    private fun verifyApk(file: File, info: UpdatePolicy.Candidate) {
        require(ready() && file.length() == info.bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) } }
        require(UpdatePolicy.hex(digest.digest()) == info.hash)
        val archive = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("Unreadable APK")
        require(Build.VERSION.SDK_INT >= 28 && archive.packageName == context.packageName && archive.longVersionCode == info.code && archive.versionName == info.version)
        require(archive.applicationInfo?.minSdkVersion == info.minSdk && signer(file.path) == pin)
        require((archive.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0)
        require(UpdatePolicy.compatible(info, installedCode, prefs.getLong("highest", 0), Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList()))
        java.util.zip.ZipFile(file).use { zip ->
            val abis = zip.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }.map { it.split('/')[1] }.toSet()
            require(abis == info.abis)
        }
    }
    private suspend fun install(info: UpdatePolicy.Candidate) {
        if (!foreground) throw CancellationException()
        verifyApk(apk, info)
        if (!context.packageManager.canRequestPackageInstalls()) { mutableState.value = State.Permission; return }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("MinTV-update.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { coroutineContext.ensureActive(); if (!foreground) throw CancellationException(); val size = input.read(buffer); if (size < 0) break; output.write(buffer, 0, size) }
                    }; session.fsync(output)
                }
                require(prefs.edit().putInt("session", id).putLong("target", info.code).remove("confirmation").remove("result").commit())
                val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(context.packageName + ".UPDATE_STATUS")
                val pending = PendingIntent.getBroadcast(context, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                mutableState.value = State.Installing; session.commit(pending.intentSender)
            }; apk.delete()
        } catch (error: Exception) { installer.abandonSession(id); prefs.edit().remove("session").remove("confirmation").apply(); throw error }
    }
    internal suspend fun resume() = lock.withLock { withContext(Dispatchers.IO) {
        if (!ready()) return@withContext
        val target = prefs.getLong("target", 0)
        if (target > 0 && installedCode >= target) {
            prefs.edit().remove("session").remove("target").remove("confirmation").remove("result").apply()
            directory.deleteRecursively(); mutableState.value = State.UpToDate; return@withContext
        }
        candidate = runCatching { UpdatePolicy.authenticate(manifest.readBytes(), channel, pin, prefs.getString("tag", "")!!) }.getOrNull()
        val result = prefs.getInt("result", Int.MIN_VALUE)
        mutableState.value = when {
            prefs.contains("confirmation") -> State.Confirmation
            result == PackageInstaller.STATUS_FAILURE_ABORTED -> State.Cancelled
            result != Int.MIN_VALUE && result != PackageInstaller.STATUS_SUCCESS -> State.Failed(installProblem(result))
            prefs.contains("session") && context.packageManager.packageInstaller.getSessionInfo(prefs.getInt("session", -1)) != null -> State.Installing
            apk.exists() && candidate != null -> if (context.packageManager.canRequestPackageInstalls()) State.Available(candidate!!) else State.Permission
            candidate != null && candidate!!.code > installedCode -> State.Available(candidate!!)
            else -> State.Idle
        }
    }
    }
    internal fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    internal fun confirmationIntent(): Intent? = runCatching { Intent.parseUri(prefs.getString("confirmation", null), Intent.URI_INTENT_SCHEME) }.getOrNull()
    internal fun openInstallerScreen(intent: Intent?) {
        if (intent == null || runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure)
            mutableState.value = State.Failed(Problem.INSTALL)
    }
    internal suspend fun continueInstall() = lock.withLock {
        val info = candidate ?: return@withLock
        try { withContext(Dispatchers.IO) { install(info) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutableState.value = State.Failed(Problem.INSTALL) }
    }
    internal fun hasDownload() = apk.exists() && prefs.getLong("download_code", 0) == candidate?.code
    internal fun dismiss() { transport.cancel(); mutableState.value = State.Idle; prefs.edit().remove("result").apply() }
    companion object { internal const val STORE = "mintv-verified-updates" }
}
