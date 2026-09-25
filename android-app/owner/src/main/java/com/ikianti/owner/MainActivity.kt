package com.ikianti.owner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private val origin = Uri.parse(BuildConfig.DASHBOARD_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        status = TextView(this).apply { setPadding(24, 12, 24, 12); text = "Iki Besitzer" }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Dashboard neu laden"
            setOnClickListener { web.loadUrl(dashboardUrl(intent)) }
        })
        web = WebView(this)
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        // Ein gemeinsamer, lebenszyklusgebundener Rückweg für Tasten und Gesten ab API 26.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        web.settings.apply {
            // Für das Dashboard nötig. Navigation und native Nachrichten werden unten
            // auf die feste HTTPS-Origin begrenzt; Datei- und Klartextzugriffe bleiben gesperrt.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // Auch fremde POST-Hauptseiten blockieren, die shouldOverrideUrlLoading nicht erfasst.
                // Dashboard-Ressourcen (z. B. Karten und Skripte) dürfen weiter geladen werden.
                if (request.isForMainFrame && !trusted(request.url)) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
                        ByteArrayInputStream("Navigation außerhalb des Dashboards gesperrt.".toByteArray(Charsets.UTF_8))
                    )
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (trusted(request.url)) return false
                if (request.isForMainFrame && request.hasGesture() && request.url.scheme == "https") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                }
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status.text = "Dashboard nicht erreichbar. Verbindung prüfen und neu laden."
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "IkiOwner", setOf(origin.toString().trimEnd('/'))) {
                    _, message, source, mainFrame, reply ->
                if (mainFrame && trusted(source) && message.data == "getToken") {
                    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                        reply.postMessage("{\"error\":\"Bitte Benachrichtigungen für Iki Besitzer in Android erlauben.\"}")
                    } else if (FirebaseApp.getApps(this).isEmpty()) {
                        reply.postMessage("{\"error\":\"Firebase-Konfiguration der Besitzer-App fehlt.\"}")
                    } else {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (!isDestroyed && trusted(Uri.parse(web.url ?: ""))) {
                                reply.postMessage(if (task.isSuccessful) {
                                    JSONObject().put("token", task.result).toString()
                                } else "{\"error\":\"Push-Registrierung fehlgeschlagen. Bitte erneut versuchen.\"}")
                            }
                        }
                    }
                }
            }
        } else {
            status.text = "Bitte Android System WebView aktualisieren, um Push zu aktivieren."
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        web.loadUrl(dashboardUrl(intent))
    }

    private fun trusted(uri: Uri): Boolean = uri.scheme == "https" &&
        uri.host == origin.host && uri.port == origin.port && uri.userInfo == null

    private fun dashboardUrl(source: Intent): String = origin.buildUpon().apply {
        appendQueryParameter("owner_app", "1")
        val device = source.getStringExtra("device_id")
        val date = source.getStringExtra("date")
        if (device != null && date != null) {
            appendQueryParameter("device_id", device)
            appendQueryParameter("report_date", date)
        }
    }.build().toString()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        web.loadUrl(dashboardUrl(intent))
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized && trusted(Uri.parse(web.url ?: ""))) {
            web.evaluateJavascript("window.dispatchEvent(new Event('iki-owner-resume'))", null)
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
