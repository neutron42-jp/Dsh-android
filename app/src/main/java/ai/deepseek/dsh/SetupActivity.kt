package ai.deepseek.dsh

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLHandshakeException

/**
 * First-run / re-login / URL-edit screen. No browser UI here on purpose.
 *
 * `dsh web` refuses every request until a browser has exchanged the launch
 * token printed at startup for a cookie, so this screen takes both the plain
 * Web UI URL and the tokenized login URL.
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        /** When set, always show the form (used to fix a broken URL). */
        const val EXTRA_EDIT_URL = "edit_url"
    }

    private lateinit var prefs: ServerPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPrefs(this)

        val editUrl = intent.getStringExtra(EXTRA_EDIT_URL)

        // Share intent: a `dsh web` login URL points the app at a server,
        // other text is chat input.
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val sharedLogin = shared?.let { ServerPrefs.splitLoginUrl(it) }
        if (sharedLogin != null) {
            prefs.serverUrl = ServerPrefs.normalize(sharedLogin.first)
            prefs.loginUrl = sharedLogin.second
            openMain(sharedLogin.second)
            return
        }
        if (shared != null && shared.trim().startsWith("http")) {
            prefs.serverUrl = ServerPrefs.normalize(shared)
            openMain()
            return
        }
        shared?.let { prefs.pendingShare = it }

        // Already configured -> straight into the app, no browser detour.
        // Edit mode and fresh shares always show the form.
        if (editUrl == null) {
            prefs.serverUrl?.let { openMain(); return }
        }

        setContentView(R.layout.activity_setup)

        val urlInput = findViewById<TextInputEditText>(R.id.input_server_url)
        val loginInput = findViewById<TextInputEditText>(R.id.input_login_url)
        val connect = findViewById<MaterialButton>(R.id.btn_connect)
        val error = findViewById<TextView>(R.id.text_setup_error)
        urlInput.setText(editUrl ?: prefs.serverUrl.orEmpty())

        // Pre-fill when the user shares a login URL to the app.
        if (intent?.action == Intent.ACTION_SEND) {
            val s = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (s.contains("token=")) loginInput.setText(s.trim())
        }

        connect.setOnClickListener {
            error.visibility = View.GONE
            // A tokenized URL wins wherever it was pasted: it carries both the
            // server address and the one-time login.
            val login = ServerPrefs.splitLoginUrl(loginInput.text.toString())
                ?: ServerPrefs.splitLoginUrl(urlInput.text.toString())
            if (login != null) {
                prefs.serverUrl = ServerPrefs.normalize(login.first)
                prefs.loginUrl = login.second
                openMain(login.second)
                return@setOnClickListener
            }
            val urlRaw = loginInput.text.toString().ifBlank { urlInput.text.toString() }
            if (urlRaw.isBlank()) {
                Toast.makeText(this, getString(R.string.setup_need_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = ServerPrefs.normalize(urlRaw)
            connect.isEnabled = false
            connect.text = getString(R.string.checking)
            Thread {
                val result = checkServer(url)
                runOnUiThread {
                    connect.isEnabled = true
                    connect.text = getString(R.string.setup_connect)
                    when (result) {
                        CheckResult.OK, CheckResult.SSL_ISSUE -> {
                            prefs.serverUrl = url
                            prefs.loginUrl = null
                            openMain()
                        }
                        CheckResult.NEEDS_LOGIN -> {
                            error.text = getString(R.string.err_needs_login)
                            error.visibility = View.VISIBLE
                        }
                        CheckResult.UNREACHABLE -> {
                            error.text = getString(R.string.err_unreachable)
                            error.visibility = View.VISIBLE
                        }
                        CheckResult.NOT_DSH -> {
                            error.text = getString(R.string.err_not_dsh)
                            error.visibility = View.VISIBLE
                        }
                    }
                }
            }.start()
        }
    }

    private fun openMain(firstUrl: String? = null) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FIRST_URL, firstUrl ?: prefs.serverUrl)
            }
        )
        finish()
    }

    private enum class CheckResult { OK, SSL_ISSUE, NEEDS_LOGIN, UNREACHABLE, NOT_DSH }

    /**
     * Fingerprint a `dsh web` server: an authenticated browser gets the SPA
     * shell (200), a fresh one gets `401 dsh web authentication required`.
     * Anything else on the root path is not this product.
     */
    private fun checkServer(base: String): CheckResult {
        return try {
            val conn = URL("$base/").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "text/html")
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                when {
                    code == 200 && (body.contains("DeepSeek Harness") || body.contains("__DSH_BOOT__")) ->
                        CheckResult.OK
                    code == 401 && body.contains("dsh web authentication required") ->
                        CheckResult.NEEDS_LOGIN
                    code in 300..399 && body.isEmpty() -> CheckResult.NEEDS_LOGIN
                    else -> CheckResult.NOT_DSH
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: SSLHandshakeException) {
            // Self-hosted certs: the WebView lets the user through anyway.
            CheckResult.SSL_ISSUE
        } catch (e: Exception) {
            CheckResult.UNREACHABLE
        }
    }
}
