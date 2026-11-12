package com.nbansal.afterglow.companion

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Favourites
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.home.cleanName
import com.nbansal.afterglow.panel.QuickPanel
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.showcase.Tmdb
import com.nbansal.afterglow.takeover.Adb
import com.nbansal.afterglow.takeover.AdbException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class Reply(val status: Int, val type: String, val body: ByteArray) {
    companion object {
        fun json(status: Int, o: JSONObject) = Reply(status, "application/json; charset=utf-8", o.toString().toByteArray())
        fun error(status: Int, message: String) = json(status, JSONObject().put("error", message))
    }
}

/** Every route of the phone companion. The caller has already checked that the client is on the home network. */
class CompanionApi(
    private val app: Application,
    private val prefs: Prefs,
    private val store: CompanionStore,
    private val pairing: Pairing,
    private val adb: Adb,
    private val apps: () -> List<AppEntry>,
    private val onPaired: () -> Unit,
) {
    private val typing = Mutex()

    suspend fun handle(method: String, path: String, query: Map<String, String>, auth: String?, body: String): Reply {
        if (!path.startsWith("/api/")) {
            if (method != "GET") return Reply.error(405, "GET only")
            return asset(path) ?: Reply.error(404, "Not found")
        }
        if (path == "/api/pair") {
            if (method != "POST") return Reply.error(405, "POST only")
            return try { pair(body) } catch (e: org.json.JSONException) { Reply.error(400, "Bad request") }
        }
        val tokens = store.state.first().tokens
        if (!Pairing.tokenValid(auth, tokens)) return Reply.error(401, "Not paired")
        return try {
            when {
                path == "/api/ping" && method == "GET" -> Reply.json(200, JSONObject().put("name", Build.MODEL ?: "TV"))
                path == "/api/text" && method == "POST" -> text(body)
                path == "/api/key" && method == "POST" -> key(body)
                path == "/api/favourites" && method == "GET" -> favourites()
                path == "/api/favourites" && method == "POST" -> reorder(body)
                path == "/api/tmdbkey" && method == "POST" -> tmdbKey(body)
                path == "/api/backup" && method == "GET" -> backup(query["withKey"] == "1")
                path == "/api/restore" && method == "POST" -> restore(body)
                else -> Reply.error(404, "Not found")
            }
        } catch (e: AdbException) {
            Reply.error(502, e.message ?: "The TV did not accept the command")
        } catch (e: org.json.JSONException) {
            Reply.error(400, "Bad request")
        }
    }

    private fun asset(path: String): Reply? {
        val (file, type) = when (path) {
            "/", "/index.html" -> "index.html" to "text/html; charset=utf-8"
            "/manifest.json" -> "manifest.json" to "application/manifest+json"
            "/icon.svg" -> "icon.svg" to "image/svg+xml"
            else -> return null
        }
        return runCatching { Reply(200, type, app.assets.open("companion/$file").use { it.readBytes() }) }.getOrNull()
    }

    private suspend fun pair(body: String): Reply {
        val pin = JSONObject(body).optString("pin")
        return when (pairing.check(pin)) {
            Pairing.Result.Ok -> {
                val token = pairing.newToken()
                store.setTokens(Pairing.addToken(store.state.first().tokens, token))
                onPaired()
                Reply.json(200, JSONObject().put("token", token).put("name", Build.MODEL ?: "TV"))
            }
            Pairing.Result.BadPin -> Reply.error(403, "Wrong PIN")
            Pairing.Result.Expired -> Reply.error(410, "PIN expired: open Pair a phone on the TV again")
            Pairing.Result.TooMany -> Reply.error(429, "Too many wrong tries: open Pair a phone on the TV again")
        }
    }

    private suspend fun text(body: String): Reply {
        val plan = CompanionText.plan(JSONObject(body).optString("text"))
        typing.withLock {
            plan.commands.forEachIndexed { i, c ->
                if (i > 0) delay(CompanionText.ChunkDelayMs)
                adb.run(c)
            }
        }
        return Reply.json(200, JSONObject().put("sent", plan.commands.size).put("skipped", plan.skipped))
    }

    private suspend fun key(body: String): Reply {
        val k = JSONObject(body).optString("key")
        if (!CompanionKeys.known(k)) return Reply.error(400, "Unknown key")
        if (k == CompanionKeys.Panel) {
            if (!Settings.canDrawOverlays(app)) return Reply.error(409, "Quick panel is off on the TV")
            Handler(Looper.getMainLooper()).post { runCatching { QuickPanel.toggle(app) } }
        } else {
            adb.run(CompanionKeys.command(k)!!)
        }
        return Reply.json(200, JSONObject().put("ok", true))
    }

    private suspend fun favourites(): Reply {
        val s = prefs.state.first()
        val list = Favourites.resolve(s.favourites.orEmpty(), apps(), s.hidden, s.names)
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
        return Reply.json(200, JSONObject().put("favourites", a).put("tmdb", ShowcaseLogic.maskKey(s.tmdbKey)))
    }

    private suspend fun reorder(body: String): Reply {
        val arr = JSONObject(body).optJSONArray("order") ?: return Reply.error(400, "No order")
        val requested = (0 until arr.length()).map { arr.optString(it) }
        val s = prefs.state.first()
        val all = s.favourites.orEmpty()
        val shown = Favourites.resolve(all, apps(), s.hidden, s.names).map { it.pkg }
        val next = CompanionFavourites.reorder(all, shown, requested) ?: return Reply.error(400, "The list changed on the TV: reload")
        prefs.setFavourites(next)
        return Reply.json(200, JSONObject().put("ok", true))
    }

    private suspend fun tmdbKey(body: String): Reply {
        val k = JSONObject(body).optString("key").trim()
        if (!Tmdb.isValidKey(k)) return Reply.error(400, ShowcaseLogic.badKeyMessage(k))
        prefs.setTmdbKey(k)
        return Reply.json(200, JSONObject().put("tmdb", ShowcaseLogic.maskKey(k)))
    }

    private suspend fun backup(withKey: Boolean): Reply =
        Reply(200, "application/json; charset=utf-8", BackupCodec.encode(BackupCodec.of(prefs.state.first(), withKey)).toByteArray())

    private suspend fun restore(body: String): Reply {
        val b = BackupCodec.decode(body) ?: return Reply.error(400, "That is not a valid Afterglow backup")
        b.favourites?.let { prefs.setFavourites(it) }
        prefs.setHidden(b.hidden)
        prefs.setNames(b.names)
        prefs.setButtons(b.buttons)
        prefs.setUserName(cleanName(b.userName))
        prefs.setShowcase(b.showcase)
        prefs.setOpenWith(b.openWith)
        b.tmdbKey?.let { prefs.setTmdbKey(it) }
        return Reply.json(200, JSONObject().put("ok", true))
    }
}
