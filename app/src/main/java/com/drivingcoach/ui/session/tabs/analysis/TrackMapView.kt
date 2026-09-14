package com.drivingcoach.ui.session.tabs.analysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.drivingcoach.R
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the recorded track outline from GPS alone - no map tiles, no network,
 * no map SDK.
 *
 * Delivery 1 of this app is expected to work with the device in flight mode
 * (SRS section 8), so a tile-backed map would leave the most informative screen
 * in the app blank exactly when it is being used. The GPS trace already contains
 * the track's shape; everything here is drawn from that.
 *
 * The outline is coloured by speed (slow to fast), braking zones are overdrawn
 * in red, corners are labelled in order of passing and the start/finish is
 * marked.
 *
 * All geometry arrives pre-projected and pre-decimated from
 * [SessionAnalysisProcessor.buildTrackPath], and every Paint is allocated once,
 * so [onDraw] neither allocates nor recomputes.
 */
class TrackMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val PADDING_DP = 20f
        const val TRACK_STROKE_DP = 5f
        const val BRAKING_STROKE_DP = 5f
        const val MARKER_RADIUS_DP = 4f
        const val LABEL_SIZE_DP = 11f
        const val LABEL_OFFSET_DP = 12f
        const val START_FINISH_RADIUS_DP = 6f
    }

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TRACK_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val brakingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BRAKING_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.colorError)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.colorOnSurface)
    }

    private val startFinishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.colorGold)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorOnSurface)
        textSize = LABEL_SIZE_DP * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorOnSurfaceVariant)
        textSize = LABEL_SIZE_DP * density * 1.1f
        textAlign = Paint.Align.CENTER
    }

    private val slowColor = ContextCompat.getColor(context, R.color.speedGradientSlow)
    private val midColor = ContextCompat.getColor(context, R.color.speedGradientMid)
    private val fastColor = ContextCompat.getColor(context, R.color.speedGradientFast)

    private val segmentPath = Path()

    private var path: TrackPath? = null

    /** Message shown in place of the track when there is nothing to draw. */
    var emptyText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    fun setTrackPath(trackPath: TrackPath?) {
        path = trackPath?.takeUnless { it.isEmpty }
        contentDescription = if (path == null) {
            emptyText
        } else {
            resources.getString(
                R.string.analysis_map_content_description,
                path?.cornerMarkers?.size ?: 0
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val track = path
        if (track == null) {
            canvas.drawText(
                emptyText,
                width / 2f,
                height / 2f + emptyPaint.textSize / 3f,
                emptyPaint
            )
            return
        }

        val padding = PADDING_DP * density
        val scale = min(width - 2 * padding, height - 2 * padding)
        if (scale <= 0f) return

        val offsetX = (width - scale) / 2f
        val offsetY = (height - scale) / 2f

        fun px(x: Float) = offsetX + x * scale
        fun py(y: Float) = offsetY + y * scale

        val points = track.points
        val speedRange = max(0.1f, track.maxSpeedKmh - track.minSpeedKmh)

        // Drawn segment by segment because the colour changes continuously along
        // the lap; a single Path could only carry one colour.
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            segmentPath.rewind()
            segmentPath.moveTo(px(a.x), py(a.y))
            segmentPath.lineTo(px(b.x), py(b.y))

            if (a.isBraking && b.isBraking) {
                canvas.drawPath(segmentPath, brakingPaint)
            } else {
                val ratio = ((a.speedKmh + b.speedKmh) / 2f - track.minSpeedKmh) / speedRange
                trackPaint.color = speedColor(ratio.coerceIn(0f, 1f))
                canvas.drawPath(segmentPath, trackPaint)
            }
        }

        val markerRadius = MARKER_RADIUS_DP * density
        val labelOffset = LABEL_OFFSET_DP * density
        for (marker in track.cornerMarkers) {
            val cx = px(marker.x)
            val cy = py(marker.y)
            canvas.drawCircle(cx, cy, markerRadius, cornerPaint)
            canvas.drawText(marker.label, cx, cy - labelOffset, labelPaint)
        }

        track.startFinish?.let { marker ->
            val cx = px(marker.x)
            val cy = py(marker.y)
            canvas.drawCircle(cx, cy, START_FINISH_RADIUS_DP * density, startFinishPaint)
            canvas.drawText(marker.label, cx, cy + labelOffset + labelPaint.textSize / 2f, labelPaint)
        }
    }

    /** Blends the slow -> mid -> fast gradient at [ratio] in 0..1. */
    private fun speedColor(ratio: Float): Int = if (ratio < 0.5f) {
        blend(slowColor, midColor, ratio * 2f)
    } else {
        blend(midColor, fastColor, (ratio - 0.5f) * 2f)
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
    )
}
