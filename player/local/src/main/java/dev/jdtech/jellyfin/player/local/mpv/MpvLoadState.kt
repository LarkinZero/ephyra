package dev.jdtech.jellyfin.player.local.mpv

/**
 * CE (#446): whether mpv's END_FILE means the current file failed to load.
 *
 * The JNI layer passes no END_FILE reason. A file that ends without reaching FILE_LOADED, and that
 * this player did not replace, remove or stop, failed to load. mpv's error lines are no signal of
 * their own: libmpv hands out a pending event before pending log messages, so the error that
 * explains the failure can arrive after END_FILE. The failure is reported at END_FILE either way,
 * with the error line when one came first; a later one is only logged.
 *
 * Not thread-safe: [MPVPlayer] calls it on the main thread.
 */
internal class MpvLoadState {

    /** Whether mpv got as far as FILE_LOADED for the file it started last. */
    var fileLoaded = false
        private set

    /** This player replaced, removed or stopped the current file, so its END_FILE is no failure. */
    var endFileExpected = false
        private set

    /** The last error mpv logged since it started the current file. */
    private var lastError: String? = null

    /** The failure of the current file was reported. */
    private var failureReported = false

    fun startFile() {
        fileLoaded = false
        endFileExpected = false
        lastError = null
        failureReported = false
    }

    fun fileLoaded() {
        fileLoaded = true
    }

    fun expectEndFile() {
        endFileExpected = true
    }

    /**
     * mpv logged [message] at error level. Returns true when the failure of the current file was
     * reported already, so the line only belongs in the log.
     */
    fun errorLogged(message: String): Boolean {
        if (failureReported) return true
        lastError = message
        return false
    }

    /**
     * mpv ended the current file. Returns the failure to report, or null when this end is not a
     * load failure. [Failure.message] is the error mpv logged before END_FILE, if any.
     */
    fun endFile(): Failure? {
        val error = lastError
        lastError = null
        if (fileLoaded || endFileExpected || failureReported) return null
        failureReported = true
        return Failure(error)
    }

    data class Failure(val message: String?)
}
