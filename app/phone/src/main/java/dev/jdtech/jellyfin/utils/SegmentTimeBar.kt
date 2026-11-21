package dev.jdtech.jellyfin.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R as Media3R
import dev.jdtech.jellyfin.core.presentation.ephyra.SeekBarSegment
import dev.jdtech.jellyfin.core.presentation.ephyra.seekBarSegmentColor
import dev.jdtech.jellyfin.core.presentation.ephyra.seekBarSegments
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.presentation.theme.darkScheme
import kotlin.math.max

/**
 * CE (#681): media3's time bar with the item's media segments drawn as ranges of the track.
 *
 * The ranges go under media3's own drawing: its unplayed and buffered colours are translucent, so
 * the segment colour shows through them, and the played part (opaque `primary`) covers it. Progress
 * stays cyan and the segments show what is still ahead (VISION §3.4).
 *
 * media3 keeps the bar's bounds to itself, so they are worked out here the same way, from the same
 * attributes: centred bar of `bar_height`, inset by half the scrubber on each side. The player
 * layout uses the centred gravity only.
 */
class SegmentTimeBar
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    DefaultTimeBar(context, attrs, defStyleAttr) {
    private val barHeight: Int
    private val scrubberPadding: Int
    private val paint = Paint()
    private var durationMs = 0L
    private var segments: List<FindroidSegment> = emptyList()
    private var ranges: List<SeekBarSegment> = emptyList()

    init {
        fun dp(value: Float) =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    context.resources.displayMetrics,
                )
                .toInt()
        val a = context.obtainStyledAttributes(attrs, Media3R.styleable.DefaultTimeBar)
        try {
            barHeight =
                a.getDimensionPixelSize(
                    Media3R.styleable.DefaultTimeBar_bar_height,
                    dp(DEFAULT_BAR_HEIGHT_DP),
                )
            val scrubber = a.getDrawable(Media3R.styleable.DefaultTimeBar_scrubber_drawable)
            scrubberPadding =
                if (scrubber != null) {
                    (scrubber.minimumWidth + 1) / 2
                } else {
                    val enabled =
                        a.getDimensionPixelSize(
                            Media3R.styleable.DefaultTimeBar_scrubber_enabled_size,
                            dp(DEFAULT_SCRUBBER_ENABLED_SIZE_DP),
                        )
                    val disabled =
                        a.getDimensionPixelSize(
                            Media3R.styleable.DefaultTimeBar_scrubber_disabled_size,
                            dp(DEFAULT_SCRUBBER_DISABLED_SIZE_DP),
                        )
                    val dragged =
                        a.getDimensionPixelSize(
                            Media3R.styleable.DefaultTimeBar_scrubber_dragged_size,
                            dp(DEFAULT_SCRUBBER_DRAGGED_SIZE_DP),
                        )
                    (max(disabled, max(enabled, dragged)) + 1) / 2
                }
        } finally {
            a.recycle()
        }
    }

    /** The current item's segments; an empty list clears them. */
    fun setSegments(segments: List<FindroidSegment>) {
        if (segments == this.segments) return
        this.segments = segments
        updateRanges()
    }

    override fun setDuration(duration: Long) {
        super.setDuration(duration)
        if (duration == durationMs) return
        durationMs = duration
        updateRanges()
    }

    private fun updateRanges() {
        ranges = seekBarSegments(segments, durationMs)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (ranges.isNotEmpty()) {
            val left = paddingLeft + scrubberPadding
            val right = width - paddingRight - scrubberPadding
            val top = (height - barHeight) / 2
            val barWidth = (right - left).toFloat()
            if (barWidth > 0f) {
                ranges.forEach { range ->
                    paint.color = darkScheme.seekBarSegmentColor(range.type).toArgb()
                    canvas.drawRect(
                        left + barWidth * range.start,
                        top.toFloat(),
                        left + barWidth * range.end,
                        (top + barHeight).toFloat(),
                        paint,
                    )
                }
            }
        }
        super.onDraw(canvas)
    }

    private companion object {
        // media3's defaults, for a layout that doesn't set the attribute.
        const val DEFAULT_BAR_HEIGHT_DP = 4f
        const val DEFAULT_SCRUBBER_ENABLED_SIZE_DP = 12f
        const val DEFAULT_SCRUBBER_DISABLED_SIZE_DP = 0f
        const val DEFAULT_SCRUBBER_DRAGGED_SIZE_DP = 16f
    }
}
