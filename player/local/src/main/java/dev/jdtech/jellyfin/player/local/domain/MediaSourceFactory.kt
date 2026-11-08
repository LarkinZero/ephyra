package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

// Media3 retries on top of these, so a dead server stays on the spinner for several times as long.
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000L
private const val HTTP_READ_TIMEOUT_MS = 60_000L

/**
 * Gives a transcode longer than Media3's default 8 s to return its first bytes.
 *
 * CE (#296): media goes over [apiClient] (the Jellyfin API's OkHttp client, see
 * `JellyfinApi.httpClient`) with its own timeouts. `newBuilder()` keeps the connection pool, so the
 * first media request reuses the connection the PlaybackInfo call just used instead of opening one.
 */
internal fun buildMediaSourceFactory(
    context: Context,
    apiClient: OkHttpClient,
): DefaultMediaSourceFactory {
    val client =
        apiClient
            .newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    return DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            // The User-Agent DefaultHttpDataSource sent (the system's), so the server's logs and
            // any proxy rules see the same client as before.
            OkHttpDataSource.Factory(client).setUserAgent(System.getProperty("http.agent")),
        )
    )
}
