package com.phtv.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.phtv.app.core.network.PhHttp

/**
 * Application entry point. Configures Coil's singleton image loader to fetch through our OkHttp
 * client so thumbnails carry the Referer/User-Agent that hotlink-protected PornHub CDNs require.
 */
class PhtvApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { PhHttp.client })) }
            .logger(DebugLogger())
            .build()
}
