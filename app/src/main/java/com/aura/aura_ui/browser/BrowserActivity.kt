package com.aura.aura_ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.aura.aura_ui.ui.theme.MonoArgb
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.resume

/**
 * Dedicated AURA browser window. WebView-backed, single tab per window, with JS handles for the
 * browser_* tools and a visible handoff button the user can always reach.
 */
@SuppressLint("SetJavaScriptEnabled")
class BrowserActivity : Activity() {

    lateinit var webView: WebView
        private set
    private lateinit var urlLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        urlLabel = TextView(this).apply {
            text = "AURA browser"
            setTextColor(MonoArgb.textOnInk)
            setBackgroundColor(MonoArgb.ink)
            setPadding(40, 28, 40, 28)
            textSize = 13f
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(MonoArgb.canvas)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        BrowserController.onPageFinished(url, view?.title.orEmpty(), view)
                        urlLabel.text = url
                    }
                }
            }
        }
        root.addView(
            urlLabel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = 56 * resources.displayMetrics.density.toInt()
            },
        )
        setContentView(root)
        BrowserController.attach(this, webView)
        intent?.getStringExtra(EXTRA_URL)?.let { webView.loadUrl(it) }
    }

    override fun onDestroy() {
        BrowserController.detach(this)
        runCatching { webView.destroy() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

object BrowserController {
    private val main = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<BrowserActivity>? = null
    private var webViewRef: WeakReference<WebView>? = null
    private var pageWaiter: ((String) -> Unit)? = null
    @Volatile private var currentUrl: String = ""
    @Volatile private var generation: Long = 0
    @Volatile private var handoffActive: Boolean = false
    private val elementHandles = linkedMapOf<String, String>() // el_id -> selector-ish hint

    fun generation(): Long = generation
    fun isHandoffActive(): Boolean = handoffActive
    fun clearHandoff() { handoffActive = false }

    fun attach(activity: BrowserActivity, webView: WebView) {
        activityRef = WeakReference(activity)
        webViewRef = WeakReference(webView)
    }

    fun detach(activity: BrowserActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            webViewRef = null
        }
    }

    fun onPageFinished(url: String, title: String, webView: WebView?) {
        currentUrl = url
        generation += 1
        elementHandles.clear()
        if (webView != null) webViewRef = WeakReference(webView)
        pageWaiter?.invoke(title)
        pageWaiter = null
    }

    suspend fun open(context: android.content.Context, url: String): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            val activity = activityRef?.get()
            if (activity != null && !activity.isFinishing) {
                val loaded = suspendCancellableCoroutine<String?> { cont ->
                    pageWaiter = { title -> if (cont.isActive) cont.resume(title) }
                    activity.webView.loadUrl(url)
                    cont.invokeOnCancellation { pageWaiter = null }
                }
                return@runCatching loaded ?: url
            }
            context.startActivity(
                Intent(context, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            val loaded = suspendCancellableCoroutine<String?> { cont ->
                pageWaiter = { title -> if (cont.isActive) cont.resume(title) }
                cont.invokeOnCancellation { pageWaiter = null }
            }
            loaded ?: url
        }
    }

    suspend fun read(selector: String?, maxChars: Int): Result<String> = evaluate(
        """
        (function(){
          var el = ${if (selector == null) "document.body" else "document.querySelector(${quote(selector)})"};
          if (!el) return JSON.stringify({error:"not_found", generation:${generation}});
          var text = (el.innerText || el.textContent || "").slice(0, $maxChars);
          var nodes = Array.from(document.querySelectorAll('a,button,input,textarea,select,[role="button"],[onclick]')).slice(0,80).map(function(e,i){
            var r=e.getBoundingClientRect();
            var id='el_'+${generation}+'_'+i;
            e.setAttribute('data-aura-el', id);
            return {el_id:id, tag:e.tagName.toLowerCase(), text:(e.innerText||e.value||e.getAttribute('aria-label')||'').trim().slice(0,120), href:e.href||null, x:Math.round(r.x), y:Math.round(r.y), w:Math.round(r.width), h:Math.round(r.height)};
          });
          return JSON.stringify({url:location.href,title:document.title,generation:${generation},text:text,elements:nodes});
        })()
        """.trimIndent(),
    )

    suspend fun find(query: String): Result<List<JsonObject>> = evaluate(
        """
        (function(){
          var q = ${quote(query)}.toLowerCase();
          var body = (document.body.innerText || "");
          var out = [];
          var i = body.toLowerCase().indexOf(q);
          while (i >= 0 && out.length < 10) {
            out.push({ context: body.slice(Math.max(0, i - 60), i + q.length + 60) });
            i = body.toLowerCase().indexOf(q, i + q.length);
          }
          return JSON.stringify(out);
        })()
        """.trimIndent(),
    ).map { body -> Json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonArray }?.mapNotNull { element ->
        element as? JsonObject
    }.orEmpty() }

    suspend fun act(action: String, selector: String?, text: String?, direction: String?): Result<String> {
        if (handoffActive) return Result.failure(IllegalStateException("Browser handoff is active; user is in control"))
        val sel = selector?.let { s ->
            // allow el_id handles produced by browser_read
            if (s.startsWith("el_")) "[data-aura-el=\"${s}\"]" else s
        }
        return evaluate(
            when (action) {
                "click" -> """
                    (function(){var el=document.querySelector(${quote(sel ?: "body")});if(!el)return "not_found";el.click();return JSON.stringify({result:"clicked",generation:${generation},url:location.href});})()
                """.trimIndent()
                "type" -> """
                    (function(){var el=document.querySelector(${quote(sel ?: "input,textarea")});if(!el)return "not_found";
                    el.focus();el.value=${quote(text.orEmpty())};el.dispatchEvent(new Event('input',{bubbles:true}));
                    el.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({result:"typed",generation:${generation}});})()
                """.trimIndent()
                "scroll" -> """
                    (function(){var d=${quote(direction ?: "down")};
                    if(d==='up')window.scrollBy(0,-window.innerHeight*0.8);
                    else if(d==='left')window.scrollBy(-window.innerWidth*0.8,0);
                    else if(d==='right')window.scrollBy(window.innerWidth*0.8,0);
                    else window.scrollBy(0,window.innerHeight*0.8);return JSON.stringify({result:"scrolled",generation:${generation}});})()
                """.trimIndent()
                "press" -> """
                    (function(){var el=document.activeElement||document.body;
                    el.dispatchEvent(new KeyboardEvent('keydown',{key:${quote(text ?: "Enter")},bubbles:true}));
                    el.dispatchEvent(new KeyboardEvent('keyup',{key:${quote(text ?: "Enter")},bubbles:true}));return JSON.stringify({result:"pressed",generation:${generation}});})()
                """.trimIndent()
                "select" -> """
                    (function(){var el=document.querySelector(${quote(sel ?: "select")});if(!el)return "not_found";
                    el.value=${quote(text.orEmpty())};el.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({result:"selected",generation:${generation}});})()
                """.trimIndent()
                else -> "return JSON.stringify({error:'unsupported',generation:${generation}});"
            },
        )
    }

    suspend fun extract(selector: String?): Result<JsonObject> = evaluate(
        """
        (function(){
          var root = ${if (selector == null) "document.body" else "document.querySelector(${quote(selector)})"};
          if (!root) return JSON.stringify({error:"not_found"});
          var links = Array.from(root.querySelectorAll('a')).slice(0,60).map(function(a){return {text:(a.innerText||'').trim(),href:a.href};});
          return JSON.stringify({title:document.title,url:location.href,text:(root.innerText||'').slice(0,12000),links:links});
        })()
        """.trimIndent(),
    ).map { body ->
        runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    suspend fun waitFor(condition: String, timeoutMs: Long): Result<Boolean> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val script = if (condition == "idle") {
                "document.readyState === 'complete'"
            } else {
                "(document.body.innerText || '').toLowerCase().includes(${quote(condition.lowercase())})"
            }
            val hit = evaluate(script).getOrNull()?.toBooleanStrictOrNull() ?: false
            if (hit) return Result.success(true)
            delay(250)
        }
        return Result.success(false)
    }

    suspend fun screenshot(context: android.content.Context): Result<File> = withContext(Dispatchers.Main) {
        runCatching {
            val webView = webViewRef?.get() ?: error("Browser is not open")
            val bitmap = Bitmap.createBitmap(webView.width.coerceAtLeast(1), webView.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            webView.draw(android.graphics.Canvas(bitmap))
            val dir = File(context.filesDir, "browser").also { it.mkdirs() }
            val file = File(dir, "browser_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bitmap.recycle()
            file
        }
    }

    fun tabs(): List<String> = listOf(currentUrl)

    suspend fun handoff(context: android.content.Context, reason: String?): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            handoffActive = true
            val url = currentUrl
            if (url.isNotBlank()) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            activityRef?.get()?.finish()
            Unit
        }
    }

    fun close() {
        main.post { activityRef?.get()?.finish() }
        activityRef = null
        webViewRef = null
        currentUrl = ""
    }

    fun currentUrl(): String = currentUrl

    private suspend fun evaluate(script: String): Result<String> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val webView = webViewRef?.get()
            if (webView == null) {
                if (cont.isActive) cont.resume(Result.failure(IllegalStateException("Browser is not open")))
                return@suspendCancellableCoroutine
            }
            webView.evaluateJavascript(script) { raw ->
                if (cont.isActive) {
                    val decoded = runCatching {
                        Json.parseToJsonElement(raw ?: "").jsonPrimitive.content
                    }.getOrDefault(raw ?: "")
                    cont.resume(Result.success(decoded))
                }
            }
        }
    }

    private fun quote(value: String): String = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(value))
}
