package com.drivingcoach.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.drivingcoach.R
import com.drivingcoach.data.track.CoachMap
import kotlin.math.min

/**
 * Draws the circuit with its three sectors in three colours, so that a coaching
 * sentence about sector 2 points at a visible piece of road.
 *
 * ### Why this is not [com.drivingcoach.ui.session.tabs.analysis.TrackMapView]
 *
 * That view answers a different question. It colours the trace by speed, overlays
 * braking zones and numbers the corners - everything wanted when studying one lap.
 * Here the trace must be read as *three regions*, and a speed gradient running
 * through them would fight the sector colours for the same pixels.
 *
 * It is also drawn for a different reason. The Analysis map shows a lap that was
 * driven. This one shows *where the sectors are*, and the times beside it may come
 * from several different laps - so there is deliberately no racing line, no speed and
 * no suggestion that this is a path anyone took.
 *
 * ### What is deliberately not drawn
 *
 * The Dream Lap stitches the best sector 1 from one lap, sector 2 from another and
 * sector 3 from a third. Drawing those three fragments as a continuous line would
 * show a path that was never driven, and a driver would reasonably read it as advice
 * about where to point the car. Sectors are therefore shaded as regions of track and
 * never joined into a line that claims to be a lap.
 *
 * All geometry arrives pre-projected and normalised from [CoachMap], and every Paint
 * is allocated once, so [onDraw] neither allocates nor recomputes.
 */
class SectorMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val PADDING_DP = 18f
        const val TRACK_STROKE_DP = 5f
        const val START_FINISH_RADIUS_DP = 6f
        const val LABEL_SIZE_DP = 12f
        const val LABEL_LIFT_DP = 10f
    }

    private val density = resources.displayMetrics.density

    private fun strokePaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TRACK_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, colorRes)
    }

    private val sectorPaints = listOf(
        strokePaint(R.color.sectorOne),
        strokePaint(R.color.sectorTwo),
        strokePaint(R.color.sectorThree)
    )

    private val startFinishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.colorGold)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LABEL_SIZE_DP * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorOnSurfaceVariant)
        textSize = LABEL_SIZE_DP * density * 1.1f
        textAlign = Paint.Align.CENTER
    }

    private val segmentPath = Path()

    private var drawing: CoachMap.Drawing? = null

    /** Message shown in place of the circuit when there is nothing to draw. */
    var emptyText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    fun setDrawing(value: CoachMap.Drawing?) {
        drawing = value?.takeIf { it.points.size >= 2 }
        contentDescription = describe()
        invalidate()
    }

    /**
     * The map carries real information, so it needs a real description rather than the
     * decorative label a picture would get. It names the provenance, because "the
     * surveyed circuit" and "the shape of your own laps" are different claims.
     */
    private fun describe(): CharSequence {
        val current = drawing ?: return emptyText
        val provenance = when (current.provenance) {
            CoachMap.Provenance.SURVEYED_CENTRELINE -> R.string.coach_map_desc_surveyed
            CoachMap.Provenance.DERIVED_FROM_LAPS -> R.string.coach_map_desc_derived
            CoachMap.Provenance.SINGLE_LAP -> R.string.coach_map_desc_single_lap
        }
        return resources.getString(
            R.string.coach_map_content_description,
            resources.getString(provenance)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val current = drawing
        if (current == null) {
            if (emptyText.isNotEmpty()) {
                canvas.drawText(emptyText, width / 2f, height / 2f, emptyPaint)
            }
            return
        }

        val padding = PADDING_DP * density
        val scale = min(width - 2 * padding, height - 2 * padding)
        if (scale <= 0f) return

        val offsetX = (width - scale) / 2f
        val offsetY = (height - scale) / 2f

        fun px(x: Float) = offsetX + x * scale
        fun py(y: Float) = offsetY + y * scale

        val points = current.points
        val bounds = listOf(
            0 to current.firstBoundary,
            current.firstBoundary to current.secondBoundary,
            current.secondBoundary to points.size
        )

        bounds.forEachIndexed { sector, (from, to) ->
            segmentPath.reset()
            segmentPath.moveTo(px(points[from].x), py(points[from].y))
            for (i in from + 1 until to) {
                segmentPath.lineTo(px(points[i].x), py(points[i].y))
            }
            // Each sector is carried one point into the next so the three strokes meet
            // instead of leaving two gaps in the circuit at the boundaries. The last
            // sector wraps to point 0, which closes the loop at the start/finish.
            val joinAt = if (to < points.size) to else 0
            segmentPath.lineTo(px(points[joinAt].x), py(points[joinAt].y))
            canvas.drawPath(segmentPath, sectorPaints[sector])
        }

        bounds.forEachIndexed { sector, (from, to) ->
            val mid = points[(from + to) / 2]
            labelPaint.color = sectorPaints[sector].color
            canvas.drawText(
                resources.getString(R.string.coach_map_sector_label, sector + 1),
                px(mid.x),
                py(mid.y) - LABEL_LIFT_DP * density,
                labelPaint
            )
        }

        // Drawn last so it is never hidden under a sector stroke. Index 0 is the
        // start/finish on both a surveyed and a derived shape - see CoachMap.
        canvas.drawCircle(
            px(points[0].x), py(points[0].y),
            START_FINISH_RADIUS_DP * density,
            startFinishPaint
        )
    }
}
