package com.nbansal.afterglow.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val tag: String, val notes: String, val apkUrl: String?, val shaUrl: String?)

/** Boring on purpose: GitHub Releases only, no background jobs. All calls block: use Dispatchers.IO. */
class Updater(private val context: Context) {
    private val dir get() = File(context.cacheDir, "updates")

    fun latest(): Release? {
        val json = JSONObject(get("https://api.github.com/repos/aviato28/AfterGlow/releases/latest"))
        val tag = json.optString("tag_name")
        if (Versions.parseRelease(tag) == null) return null
        val version = tag.removePrefix("v")
        var apk: String? = null
        var sha: String? = null
        val assets = json.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            when (a.optString("name")) {
                "afterglow-$version.apk" -> apk = a.optString("browser_download_url")
                "afterglow-$version.apk.sha256" -> sha = a.optString("browser_download_url")
            }
        }
        return Release(tag, json.optString("body"), apk, sha)
    }

    /** Downloads and verifies the APK; deletes it and returns null when the checksum does not match. */
    fun download(release: Release, onProgress: (Float) -> Unit): File? {
        val apkUrl = release.apkUrl ?: return null
        val shaUrl = release.shaUrl ?: return null
        clean()
        dir.mkdirs()
        val file = File(dir, "afterglow-${release.tag.removePrefix("v")}.apk")
        val conn = open(apkUrl)
        try {
            val total = conn.contentLengthLong
            file.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!Versions.checksumMatches(file, get(shaUrl))) {
            clean()
            return null
        }
        return file
    }

    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the verified APK to the system installer; [InstallReceiver] gets the result. */
    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("afterglow.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    fun clean() {
        dir.deleteRecursively()
    }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val host = URL(url).host
        require(host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com")) { "host not allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Afterglow")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    }
}
