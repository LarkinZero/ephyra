package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.text.SubtitleParser
import dev.jdtech.jellyfin.player.local.presentation.SubtitleFileMediaSource
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
 *
 * CE (#613): an image subtitle loaded as a file of its own is read by [ImageSubtitleParserFactory].
 *
 * CE (#711): a subtitle loaded as a file of its own does not hold up the video, see
 * [SubtitleFilesMediaSourceFactory].
 */
@OptIn(UnstableApi::class)
internal fun buildMediaSourceFactory(
    context: Context,
    apiClient: OkHttpClient,
): MediaSource.Factory {
    val client =
        apiClient
            .newBuilder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    val dataSourceFactory =
        DefaultDataSource.Factory(
            context,
            // The User-Agent DefaultHttpDataSource sent (the system's), so the server's logs and
            // any proxy rules see the same client as before.
            OkHttpDataSource.Factory(client).setUserAgent(System.getProperty("http.agent")),
        )
    val subtitleParserFactory = ImageSubtitleParserFactory()
    return SubtitleFilesMediaSourceFactory(
        DefaultMediaSourceFactory(dataSourceFactory)
            .setSubtitleParserFactory(subtitleParserFactory),
        dataSourceFactory,
        subtitleParserFactory,
    )
}

/**
 * CE (#711): makes the source of an item with [MediaItem.SubtitleConfiguration]s itself: [factory]
 * makes the stream's, and each subtitle file gets a [SubtitleFileMediaSource], merged in the order
 * media3 merges them. media3's own subtitle sources hold the video back while a file loads, see
 * [SubtitleFileMediaSource]. An item without subtitle files is [factory]'s alone.
 *
 * The stream's source gets the item without its subtitle files, or [factory] would add its own;
 * where the source can take it, the full item is put back, so the player reports the item it was
 * given. Clipping and ads, which DefaultMediaSourceFactory also applies to the merged source, apply
 * to the stream only; no item of the app has them.
 */
@OptIn(UnstableApi::class)
internal class SubtitleFilesMediaSourceFactory(
    private val factory: MediaSource.Factory,
    private val subtitleDataSourceFactory: DataSource.Factory,
    private var subtitleParserFactory: SubtitleParser.Factory,
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory = apply {
        factory.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): MediaSource.Factory = apply { factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }

    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory
    ): MediaSource.Factory = apply {
        this.subtitleParserFactory = subtitleParserFactory
        factory.setSubtitleParserFactory(subtitleParserFactory)
    }

    @Deprecated("Deprecated in Media3")
    override fun experimentalParseSubtitlesDuringExtraction(
        parseSubtitlesDuringExtraction: Boolean
    ): MediaSource.Factory = apply {
        @Suppress("DEPRECATION")
        factory.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
    }

    override fun getSupportedTypes(): IntArray = factory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val subtitles = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
        if (subtitles.isEmpty()) return factory.createMediaSource(mediaItem)
        val stream =
            factory.createMediaSource(
                mediaItem.buildUpon().setSubtitleConfigurations(emptyList()).build()
            )
        if (stream.canUpdateMediaItem(mediaItem)) stream.updateMediaItem(mediaItem)
        val files = subtitles.map {
            SubtitleFileMediaSource(it, subtitleDataSourceFactory, subtitleParserFactory)
        }
        return MergingMediaSource(stream, *files.toTypedArray())
    }
}
