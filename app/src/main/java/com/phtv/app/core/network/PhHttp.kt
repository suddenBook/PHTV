package com.phtv.app.core.network

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Networking for PornHub. No public API exists, so we fetch HTML/JS like a browser:
 * realistic UA + Referer + consent cookies. The same client is reused for image loading (Coil)
 * so thumbnails on hotlink-protected CDNs also carry a Referer.
 */
object PhHttp {
    const val BASE = "https://www.pornhub.com"
    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PhCookieJar())
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(LogInterceptor())
        .build()

    fun getText(url: String, referer: String = "$BASE/"): String {
        val request = Request.Builder().url(url).header("Referer", referer).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body.string()
        }
    }
}

/** Ensures every request (including Coil image loads) carries browser-like headers. */
private class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val b = original.newBuilder()
        if (original.header("User-Agent") == null) b.header("User-Agent", PhHttp.USER_AGENT)
        if (original.header("Referer") == null) b.header("Referer", "${PhHttp.BASE}/")
        if (original.header("Accept-Language") == null) b.header("Accept-Language", "en-US,en;q=0.9")
        return chain.proceed(b.build())
    }
}

/** Logs every HTTP call (status, method, host/path, duration) under tag PHTV-NET. */
private class LogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val start = System.nanoTime()
        return try {
            val resp = chain.proceed(req)
            val ms = (System.nanoTime() - start) / 1_000_000
            Log.d("PHTV-NET", "${resp.code} ${req.method} ${req.url.host}${req.url.encodedPath} (${ms}ms)")
            resp
        } catch (t: Throwable) {
            Log.e("PHTV-NET", "FAIL ${req.method} ${req.url}: ${t.message}")
            throw t
        }
    }
}

/** Persists server cookies and always presents the consent/age cookies so content isn't gated. */
private class PhCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            cookies.forEach { c ->
                list.removeAll { it.name == c.name }
                list.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val saved = store[url.host]?.let { synchronized(it) { it.toList() } } ?: emptyList()
        val present = saved.mapTo(HashSet()) { it.name }
        return saved + consentCookies(url.host).filter { it.name !in present }
    }

    private fun consentCookies(host: String): List<Cookie> = listOf(
        seed(host, "cookieConsent", "1"),
        seed(host, "age_verified", "1"),
        seed(host, "accessAgeDisclaimerPH", "1"),
        seed(host, "platform", "pc"),
    )

    private fun seed(host: String, name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain(host).path("/").build()
}
