package com.leaguedesk.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.leaguedesk.app.data.Credentials
import com.leaguedesk.app.ui.AppBackground
import com.leaguedesk.app.ui.IconAction
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.theme.LD
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.URLDecoder

private const val LOGIN_URL = "https://www.espn.com/login?redirect=https%3A%2F%2Ffantasy.espn.com%2F"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EspnLoginScreen(
    onCookies: (Credentials) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var pageTitle by remember { mutableStateOf("ESPN") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val target = url ?: return false
                    return !(target.startsWith("http://") || target.startsWith("https://"))
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        runCatching {
                            pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "ESPN"
                        }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
                }
            }
        }
    }

    fun cleanup() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearHistory()
            webView.clearCache(true)
        }
    }

    fun importSession() {
        if (importing) return
        val credentials = readEspnCookies()
        if (credentials == null) {
            errorText = "No ESPN session found yet. Finish signing in, then tap Import session again."
            return
        }
        importing = true
        errorText = null
        cleanup()
        onCookies(credentials)
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    LaunchedEffect(webView) {
        cleanup()
        webView.loadUrl(LOGIN_URL)
    }

    LaunchedEffect(webView) {
        while (isActive) {
            delay(1200)
            val url = webView.url.orEmpty()
            // ESPN sets anonymous cookies on the login page. Only import once the
            // redirect has actually landed on Fantasy.
            if (!url.startsWith("https://fantasy.espn.com/")) continue
            val credentials = readEspnCookies()
            if (credentials != null) {
                importing = true
                errorText = null
                cleanup()
                onCookies(credentials)
                break
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onCancel()
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ESPN SIGN-IN",
                        color = LD.Accent2,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        pageTitle,
                        color = LD.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconAction(
                    Icons.Rounded.Refresh,
                    { webView.reload() },
                    "Reload login",
                    size = 34.dp,
                )
                IconAction(
                    Icons.Rounded.Close,
                    {
                        cleanup()
                        onCancel()
                    },
                    "Cancel login",
                    size = 34.dp,
                )
            }

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    color = LD.Accent2,
                    trackColor = LD.Glass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(99.dp)),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LD.Panel)
                    .border(1.dp, LD.Line, RoundedCornerShape(20.dp)),
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )

                if (importing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LD.Bg.copy(alpha = 0.92f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = LD.Accent2)
                        Text(
                            "ESPN session found",
                            color = LD.Text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            "Importing cookies and loading your league…",
                            color = LD.Dim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            errorText?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(LD.Bad.copy(alpha = 0.07f))
                        .border(1.dp, LD.Bad.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = LD.Bad, modifier = Modifier.size(15.dp))
                    Text(error, color = LD.Bad, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Lock, null, tint = LD.Good, modifier = Modifier.size(14.dp))
                Text(
                    "Sign in on ESPN's page. The app imports the session, then clears the WebView cookies.",
                    color = LD.Dim,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                LDButton(
                    "Import session",
                    ::importSession,
                    variant = com.leaguedesk.app.ui.ButtonVariant.SOFT,
                    compact = true,
                    enabled = !importing,
                )
            }
        }
    }
}

private fun readEspnCookies(): Credentials? {
    val manager = CookieManager.getInstance()
    val header = listOf(
        "https://fantasy.espn.com",
        "https://www.espn.com",
        "https://espn.com",
    ).mapNotNull { manager.getCookie(it) }
        .joinToString("; ")

    if (header.isBlank()) return null
    val values = header.split(";")
        .map { it.trim() }
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
        }
        .toMap()

    val swidRaw = values["SWID"] ?: values["swid"] ?: return null
    val s2 = values["espn_s2"] ?: return null
    if (swidRaw.isBlank() || s2.isBlank()) return null

    val swid = runCatching { URLDecoder.decode(swidRaw, "UTF-8") }.getOrDefault(swidRaw)
    val normalized = if (swid.startsWith("{")) swid else "{$swid}"
    return Credentials(label = "ESPN", swid = normalized, s2 = s2)
}

