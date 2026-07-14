package com.drivingcoach.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.drivingcoach.R
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.db.entity.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareCardGenerator {

    private const val CARD_SIZE = 1080
    private const val PADDING = 80f

    fun generate(
        session: SessionEntity,
        bestLap: LapEntity?,
        consistencyScore: Float,
        context: Context
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_SIZE, CARD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundColor = Color.parseColor("#0D0D0D")
        val brandBlue = ContextCompat.getColor(context, R.color.colorPrimary)
        val white = Color.WHITE
        val grey = Color.parseColor("#808080")

        // Fill background
        canvas.drawColor(backgroundColor)

        // Driving Coach logo text (top-left)
        val logoPaint = Paint().apply {
            color = white
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Driving Coach", PADDING, PADDING + 56f, logoPaint)

        // Large best lap time (centered)
        val lapTimePaint = Paint().apply {
            color = brandBlue
            textSize = 144f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val lapTimeText = bestLap?.let { LapTimeFormatter.formatLapTime(it.durationMs) } ?: "—:—.—"
        canvas.drawText(lapTimeText, CARD_SIZE / 2f, CARD_SIZE / 2f, lapTimePaint)

        // "BEST LAP" label above the time
        val labelPaint = Paint().apply {
            color = grey
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("BEST LAP", CARD_SIZE / 2f, CARD_SIZE / 2f - 100f, labelPaint)

        // Track name below (centered)
        val trackPaint = Paint().apply {
            color = white
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(session.trackName, CARD_SIZE / 2f, CARD_SIZE / 2f + 80f, trackPaint)

        // Date (bottom-left)
        val datePaint = Paint().apply {
            color = grey
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateText = dateFormat.format(Date(session.startedAt))
        canvas.drawText(dateText, PADDING, CARD_SIZE - PADDING, datePaint)

        // Consistency score (bottom-right)
        val consistencyPaint = Paint().apply {
            color = white
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val consistencyText = String.format("CONSISTENCY %.1f%%", consistencyScore)
        canvas.drawText(consistencyText, CARD_SIZE - PADDING, CARD_SIZE - PADDING, consistencyPaint)

        // Brand blue bottom border line (4dp = ~8px at 2x density, let's use 8px)
        val borderPaint = Paint().apply {
            color = brandBlue
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, CARD_SIZE - 4f, CARD_SIZE.toFloat(), CARD_SIZE - 4f, borderPaint)

        return bitmap
    }
}
