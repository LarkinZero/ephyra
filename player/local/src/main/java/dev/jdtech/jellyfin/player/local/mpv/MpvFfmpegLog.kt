package dev.jdtech.jellyfin.player.local.mpv

import android.content.Context
import dev.jdtech.mpv.MPVLib
import java.util.concurrent.CopyOnWriteArrayList
import timber.log.Timber

/**
 * CE (#707): hands FFmpeg's log lines to every open player.
 *
 * FFmpeg has one log callback for the whole process. mpv points it at the first instance that
 * initializes and leaves it there until that instance is destroyed; after that the lines go to
 * stderr, which nobody reads, until the next instance initializes. An instance that initialized
 * while another one held the log never gets `ffmpeg` lines of its own. Those lines (tcp, http:
 * "Stream ends prematurely") are how [MpvStreamBreak] tells a lost connection from the end of the
 * file, and how [MpvLoadState] names a failed load.
 *
 * Since #460 a player's mpv is destroyed in the background, so the next player can start before the
 * last one's mpv is gone, or while a stuck one never goes. That player then took every lost
 * connection for the end of the item: it closed and was reported as played.
 *
 * So an mpv instance with nothing to play is created before any player's and kept for the life of
 * the process. It holds FFmpeg's log, and [dispatch] passes each `ffmpeg` line to every player that
 * is open. Which file a line is about is not known; [MpvStreamBreak] only uses one together with a
 * buffer that ended short of the file's end.
 */
internal class MpvFfmpegLog {

    fun interface Sink {
        fun onFfmpegLog(prefix: String, level: Int, text: String)
    }

    private val sinks = CopyOnWriteArrayList<Sink>()

    fun add(sink: Sink) {
        sinks.addIfAbsent(sink)
    }

    fun remove(sink: Sink) {
        sinks.remove(sink)
    }

    /** A line from mpv's log of the instance that holds FFmpeg's log. */
    fun dispatch(prefix: String, level: Int, text: String) {
        if (!isFfmpeg(prefix)) return
        for (sink in sinks) sink.onFfmpegLog(prefix, level, text)
    }

    companion object {
        /** FFmpeg's own lines: `ffmpeg`, and `ffmpeg/video`, `ffmpeg/demuxer` and the like. */
        fun isFfmpeg(prefix: String): Boolean = prefix == "ffmpeg" || prefix.startsWith("ffmpeg/")

        private var shared: MpvFfmpegLog? = null
        private var tried = false

        /** Kept so it is never collected: its native instance holds FFmpeg's log. */
        @Suppress("unused") private var holder: MPVLib? = null

        /**
         * The process' instance, created on the first call, before the first player creates its
         * mpv. Null when mpv could not be started for it: each player then gets the lines only when
         * its own mpv holds FFmpeg's log, as before. Not tried again after that, because an
         * instance created after a player's would not hold the log.
         */
        @Synchronized
        fun shared(context: Context): MpvFfmpegLog? {
            if (tried) return shared
            tried = true
            val log = MpvFfmpegLog()
            try {
                val mpv = MPVLib.create(context) ?: return null
                mpv.setOptionString("config", "no")
                mpv.setOptionString("vo", "null")
                mpv.setOptionString("ao", "null")
                mpv.setOptionString("idle", "yes")
                // As in MPVPlayer: verbose lines carry the URLs with the Jellyfin token.
                mpv.requestLogMessages("warn")
                mpv.init()
                mpv.addLogObserver(
                    object : MPVLib.LogObserver {
                        override fun logMessage(prefix: String, level: Int, text: String) {
                            log.dispatch(prefix, level, text)
                        }
                    }
                )
                holder = mpv
                shared = log
            } catch (e: Exception) {
                Timber.w(e, "Could not start mpv for FFmpeg's log")
            }
            return shared
        }
    }
}
