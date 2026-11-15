package dev.jdtech.jellyfin.player.local.domain

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.pgs.PgsParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import timber.log.Timber

/** The image subtitle formats: pictures, not text (PGS, VobSub, DVB). */
internal val IMAGE_SUBTITLE_TYPES =
    setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)

/**
 * CE (#613): media3's subtitle parsers, plus the image subtitle files the server delivers.
 *
 * media3 parses an image subtitle one sample at a time, as a container (Matroska, a transport
 * stream) carries it. Loaded as a file of its own (during a transcode, or a downloaded sidecar),
 * the whole file reaches the parser as one sample, and nothing is drawn. The server delivers such a
 * track as a whole file: PGS as a `.sup` file, VobSub and DVB as a Matroska file with only that
 * track. [ImageSubtitleFileParser] splits both into their pictures, each with its own time.
 */
@OptIn(UnstableApi::class)
internal class ImageSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory()
) : SubtitleParser.Factory {
    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        if (format.sampleMimeType in IMAGE_SUBTITLE_TYPES) {
            ImageSubtitleFileParser(format, delegate)
        } else {
            delegate.create(format)
        }
}

/**
 * Parses an image subtitle that is either a whole file ([parseSupFile], [parseMatroskaFile]) or a
 * sample of a container, which goes to media3's own parser.
 *
 * The parser for a sample is made when the first one arrives: VobSub and DVB read their setup from
 * [format]'s initialization data, which a file loaded on its own does not have, so media3's parser
 * would fail on a format that only names the file.
 */
@OptIn(UnstableApi::class)
internal class ImageSubtitleFileParser(
    private val format: Format,
    private val factory: SubtitleParser.Factory,
) : SubtitleParser {
    private var sampleParser: SubtitleParser? = null

    override fun getCueReplacementBehavior(): Int = factory.getCueReplacementBehavior(format)

    override fun reset() {
        sampleParser?.reset()
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val ordered = OrderedOutput(outputOptions, output::accept)
        when {
            // A PGS sample starts with a segment type, never with "PG".
            data.startsWith(offset, length, SUP_MAGIC) ->
                parseSupFile(data, offset, length, ordered::accept)
            // A VobSub or DVB track in a container always comes with its setup.
            format.initializationData.isEmpty() && data.startsWith(offset, length, EBML_MAGIC) ->
                parseMatroskaFile(data, offset, length, factory, ordered::accept)
            else -> {
                val parser = sampleParser ?: factory.create(format).also { sampleParser = it }
                parser.parse(data, offset, length, outputOptions, output)
                return
            }
        }
        ordered.finish()
    }
}

/**
 * How long the last picture of a file shows when nothing clears it: to the end of any film. A
 * picture without a duration is not shown from a file loaded on its own (see [LastingUntilNext]).
 */
private const val LAST_PICTURE_DURATION_US = 24L * 60 * 60 * 1_000_000

/**
 * Passes on pictures, given in the order of their times, each without a duration of its own lasting
 * until the next one starts. Only one picture is held back, so a long film's decoded pictures are
 * not all in memory at once.
 *
 * A picture lasts until the next one replaces it, so the parsers give it no duration. In a
 * container that works, but from a file loaded on its own ExoPlayer showed none of them (#613,
 * emulator, media3 1.11.1).
 */
@OptIn(UnstableApi::class)
internal class LastingUntilNext(private val output: (CuesWithTiming) -> Unit) {
    private var pending: CuesWithTiming? = null

    fun add(cues: CuesWithTiming) {
        pending?.let { previous ->
            output(
                if (
                    previous.durationUs != C.TIME_UNSET || cues.startTimeUs < previous.startTimeUs
                ) {
                    previous
                } else {
                    previous.lasting(cues.startTimeUs - previous.startTimeUs)
                }
            )
        }
        pending = cues
    }

    fun finish() {
        pending?.let { last ->
            output(
                if (last.durationUs != C.TIME_UNSET || last.cues.isEmpty()) last
                else last.lasting(LAST_PICTURE_DURATION_US)
            )
        }
        pending = null
    }

    private fun CuesWithTiming.lasting(durationUs: Long) =
        CuesWithTiming(cues, startTimeUs, durationUs)
}

/**
 * Passes cues on in the order [options] asks for, given in the order of their times: all of them
 * without a start time. With one, those still showing at it or later, and then (when it asks for
 * all of them) the ones before, which are held until [finish]. ExoPlayer asks a file loaded on its
 * own for all of them, so nothing is held then.
 */
@OptIn(UnstableApi::class)
internal class OrderedOutput(
    private val options: SubtitleParser.OutputOptions,
    private val output: (CuesWithTiming) -> Unit,
) {
    private val before = mutableListOf<CuesWithTiming>()

    fun accept(cues: CuesWithTiming) {
        val startTimeUs = options.startTimeUs
        when {
            startTimeUs == C.TIME_UNSET ||
                cues.endTimeUs == C.TIME_UNSET ||
                cues.endTimeUs > startTimeUs -> output(cues)
            options.outputAllCues -> before += cues
        }
    }

    fun finish() {
        before.forEach(output)
        before.clear()
    }
}

/** "PG": the start of every segment of a `.sup` file. */
private val SUP_MAGIC = byteArrayOf(0x50, 0x47)

/** The EBML header ID that starts a Matroska file. */
private val EBML_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

/** "PG", presentation and decoding time (90 kHz), segment type, segment size. */
private const val SUP_HEADER_SIZE = 13

/** The segment that closes a display set: one picture, or the clearing of one. */
private const val PGS_END_OF_DISPLAY_SET = 0x80

private fun ByteArray.startsWith(offset: Int, length: Int, magic: ByteArray): Boolean =
    length >= magic.size && magic.indices.all { this[offset + it] == magic[it] }

/**
 * The display sets of a `.sup` file, each at the time of its first segment. Each is handed to
 * media3's [PgsParser] the way Matroska carries one: the segments without their "PG" headers. A
 * display set lasts until the next one, which replaces it (an empty one clears the picture), so
 * none has a duration of its own: [LastingUntilNext] gives them one. They go to [output] one at a
 * time, in the order of the file. A damaged file gives the display sets before the damage.
 */
@OptIn(UnstableApi::class)
internal fun parseSupFile(
    data: ByteArray,
    offset: Int,
    length: Int,
    output: (CuesWithTiming) -> Unit,
) {
    val parser = PgsParser()
    val timing = LastingUntilNext(output)
    val displaySet = ByteArrayOutputStream()
    var startTimeUs = C.TIME_UNSET
    val end = offset + length
    var position = offset
    while (
        position + SUP_HEADER_SIZE <= end && data.startsWith(position, end - position, SUP_MAGIC)
    ) {
        val segmentType = data[position + 10].toInt() and 0xFF
        val segmentEnd = position + SUP_HEADER_SIZE + data.readUInt16(position + 11)
        if (segmentEnd > end) break
        if (startTimeUs == C.TIME_UNSET) startTimeUs = data.readUInt32(position + 2) * 100 / 9
        // The segment as Matroska stores it: type, size, payload.
        displaySet.write(data, position + 10, segmentEnd - position - 10)
        position = segmentEnd
        if (segmentType == PGS_END_OF_DISPLAY_SET) {
            val timeUs = startTimeUs
            try {
                parser.parse(displaySet.toByteArray(), SubtitleParser.OutputOptions.allCues()) {
                    timing.add(CuesWithTiming(it.cues, timeUs, C.TIME_UNSET))
                }
            } catch (e: RuntimeException) {
                Timber.w(e, "Skipped a PGS display set at $timeUs us")
            }
            displaySet.reset()
            startTimeUs = C.TIME_UNSET
        }
    }
    if (position < end) Timber.w("PGS file damaged at byte ${position - offset} of $length")
    timing.finish()
}

private fun ByteArray.readUInt16(position: Int): Int =
    ((this[position].toInt() and 0xFF) shl 8) or (this[position + 1].toInt() and 0xFF)

private fun ByteArray.readUInt32(position: Int): Long =
    (readUInt16(position).toLong() shl 16) or readUInt16(position + 2).toLong()

/**
 * The subtitles of a Matroska file, read by media3's [MatroskaExtractor], which hands each sample
 * of a subtitle track to [factory]'s parser with the track's setup and its time. The parsed samples
 * are kept encoded (a picture compressed), and decoded one at a time for [output], in the order of
 * their times. A damaged file gives the subtitles before the damage.
 */
@OptIn(UnstableApi::class)
internal fun parseMatroskaFile(
    data: ByteArray,
    offset: Int,
    length: Int,
    factory: SubtitleParser.Factory,
    output: (CuesWithTiming) -> Unit,
) {
    val tracks = SubtitleTracksOutput()
    // The whole file is at hand: no need to jump to the index at its end.
    val extractor = MatroskaExtractor(factory, MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
    extractor.init(tracks)
    val positionHolder = PositionHolder()
    var input = byteInput(data, offset, length, 0)
    try {
        while (true) {
            when (extractor.read(input, positionHolder)) {
                Extractor.RESULT_CONTINUE -> Unit
                Extractor.RESULT_SEEK ->
                    input = byteInput(data, offset, length, positionHolder.position)
                else -> break
            }
        }
    } catch (e: IOException) {
        Timber.w(e, "Subtitle file damaged")
    } finally {
        extractor.release()
    }
    val decoder = CueDecoder()
    val timing = LastingUntilNext(output)
    for (sample in tracks.samples().sortedBy { it.timeUs }) {
        timing.add(decoder.decode(sample.timeUs, sample.data, 0, sample.data.size))
    }
    timing.finish()
}

private class EncodedCues(val timeUs: Long, val data: ByteArray)

@OptIn(UnstableApi::class)
private fun byteInput(data: ByteArray, offset: Int, length: Int, position: Long) =
    DefaultExtractorInput(
        object : DataReader {
            private var next = offset + position.toInt()

            override fun read(buffer: ByteArray, bufferOffset: Int, readLength: Int): Int {
                if (readLength == 0) return 0
                val left = offset + length - next
                if (left <= 0) return C.RESULT_END_OF_INPUT
                val count = minOf(readLength, left)
                System.arraycopy(data, next, buffer, bufferOffset, count)
                next += count
                return count
            }
        },
        position,
        length.toLong(),
    )

/** Collects the parsed samples of every subtitle track of an extractor, and drops the rest. */
@OptIn(UnstableApi::class)
private class SubtitleTracksOutput : ExtractorOutput {
    private val tracks = mutableListOf<CueTrackOutput>()

    fun samples(): List<EncodedCues> = tracks.flatMap { it.samples }

    override fun track(id: Int, type: Int): TrackOutput =
        if (type == C.TRACK_TYPE_TEXT) CueTrackOutput().also { tracks += it }
        else DiscardingTrackOutput()

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) = Unit
}

/**
 * The samples of one subtitle track. media3 parses them before they arrive here, as long as its
 * parser knows the format: they are then encoded cues ([MimeTypes.APPLICATION_MEDIA3_CUES]).
 */
@OptIn(UnstableApi::class)
private class CueTrackOutput : TrackOutput {
    val samples = mutableListOf<EncodedCues>()
    private var parsed = false
    private var buffer = ByteArray(4096)
    private var size = 0

    override fun format(format: Format) {
        parsed = format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
    }

    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int,
    ): Int {
        ensureCapacity(size + length)
        val read = input.read(buffer, size, length)
        if (read == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw EOFException()
        }
        size += read
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        ensureCapacity(size + length)
        data.readBytes(buffer, size, length)
        size += length
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        val start = this.size - offset - size
        if (parsed) samples += EncodedCues(timeUs, buffer.copyOfRange(start, start + size))
        // Keep only what follows the sample: the start of the next one.
        System.arraycopy(buffer, start + size, buffer, 0, offset)
        this.size = offset
    }

    private fun ensureCapacity(capacity: Int) {
        if (capacity > buffer.size) buffer = buffer.copyOf(maxOf(capacity, buffer.size * 2))
    }
}
