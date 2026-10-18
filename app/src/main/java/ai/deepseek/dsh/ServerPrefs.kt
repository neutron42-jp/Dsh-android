package ai.deepseek.dsh

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Single server profile + pending share text. Encrypted store comes later. */
class ServerPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dsh", Context.MODE_PRIVATE)

    /** Web UI root, for example `http://192.168.1.10:3080`. */
    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit { putString(KEY_URL, value) }

    /**
     * One-shot login URL carrying the launch token printed by `dsh web`
     * (`http://host:port/?token=...`). The server refuses every request until a
     * browser has exchanged that token for a cookie, so the app loads this once
     * and then forgets it.
     */
    var loginUrl: String?
        get() = prefs.getString(KEY_LOGIN, null)
        set(value) = prefs.edit { putString(KEY_LOGIN, value) }

    var pendingShare: String?
        get() = prefs.getString(KEY_SHARE, null)
        set(value) = prefs.edit { putString(KEY_SHARE, value) }

    var userScriptEnabled: Boolean
        get() = prefs.getBoolean(KEY_US_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_US_ENABLED, value) }

    var userCss: String?
        get() = prefs.getString(KEY_US_CSS, null)
        set(value) = prefs.edit { putString(KEY_US_CSS, value) }

    var userJs: String?
        get() = prefs.getString(KEY_US_JS, null)
        set(value) = prefs.edit { putString(KEY_US_JS, value) }

    var refreshRequested: Boolean
        get() = prefs.getBoolean(KEY_REFRESH, false)
        set(value) = prefs.edit { putBoolean(KEY_REFRESH, value) }

    var appMenuEnabled: Boolean
        get() = prefs.getBoolean(KEY_APPMENU, true)
        set(value) = prefs.edit { putBoolean(KEY_APPMENU, value) }

    fun clearSession() {
        prefs.edit {
            remove(KEY_SHARE)
            remove(KEY_LOGIN)
        }
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_LOGIN = "login_url"
        private const val KEY_SHARE = "pending_share"
        private const val KEY_US_ENABLED = "userscript_enabled"
        private const val KEY_US_CSS = "userscript_css"
        private const val KEY_US_JS = "userscript_js"
        private const val KEY_REFRESH = "refresh_requested"
        private const val KEY_APPMENU = "appmenu_enabled"

        /** Normalize user input: trim, add http:// when missing, drop trailing /. */
        fun normalize(raw: String): String {
            var v = raw.trim()
            if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
            return v.trimEnd('/')
        }

        /**
         * A `dsh web` login URL looks like `http://host:port/?token=<launch token>`.
         * Returns (serverUrl, fullLoginUrl) or null when no token is present.
         */
        fun splitLoginUrl(raw: String): Pair<String, String>? {
            val v = raw.trim()
            if (!v.contains("token=")) return null
            val cut = v.indexOf('?')
            if (cut <= 0) return null
            val base = v.substring(0, cut).trimEnd('/')
            if (base.isEmpty()) return null
            return base to v
        }
    }
}
