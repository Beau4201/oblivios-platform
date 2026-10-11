// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.io.IOException
import org.sovereignos.airpods.R
import com.android.settingslib.widget.theme.R as ThemeR

/**
 * One battery ring of the header, drawn like the approved design (`ring()` in the mock-up) and like
 * Settings' own `BluetoothBatteryIndicator`: a 300 degree arc with the gap at the top, the level
 * colour, a round badge in the gap and the flat artwork in the middle. An unknown level draws the
 * track only, without a badge (design 4.5).
 */
class BatteryRingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Kind(val asset: String) {
        LEFT("icons/left_v1.png"),
        RIGHT("icons/right_v1.png"),
        CASE("icons/case_v1.png"),
    }

    private val d = resources.displayMetrics.density
    private var kind = Kind.LEFT
    private var percent = -1
    private var charging = false
    private var artwork: Bitmap? = null
    private var gapColor = context.getColor(ThemeR.color.settingslib_materialColorSurfaceContainer)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_DP * d
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()
    private val src = Rect()
    private val dst = RectF()
    private val bolt = Path()

    fun set(kind: Kind, percent: Int, charging: Boolean) {
        if (this.kind != kind || artwork == null) {
            this.kind = kind
            artwork = load(kind)
        }
        this.percent = percent
        this.charging = charging
        contentDescription = description()
        invalidate()
    }

    /** The colour of the page behind the ring (the sheet uses a higher surface than the page). */
    fun setGapColor(color: Int) {
        gapColor = color
        invalidate()
    }

    private fun description(): String {
        val name = context.getString(
            when (kind) {
                Kind.LEFT -> R.string.airpods_left_earbud
                Kind.RIGHT -> R.string.airpods_right_earbud
                Kind.CASE -> R.string.airpods_case_name
            }
        )
        return when {
            percent < 0 -> context.getString(R.string.airpods_ring_unknown, name)
            charging -> context.getString(R.string.airpods_ring_charging, name, percent)
            else -> context.getString(R.string.airpods_ring_discharging, name, percent)
        }
    }

    private fun load(kind: Kind): Bitmap? =
        try {
            context.assets.open(kind.asset).use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            null
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((WIDTH_DP * d).toInt(), (HEIGHT_DP * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cx = WIDTH_DP / 2f * d
        val r = RADIUS_DP * d
        val cy = CENTER_Y_DP * d
        val by = cy - r
        val surface = context.getColor(ThemeR.color.settingslib_materialColorSurfaceBright)
        oval.set(cx - r, cy - r, cx + r, cy + r)

        arcPaint.color = surface
        canvas.drawArc(oval, START_ANGLE, SWEEP, false, arcPaint)
        if (percent > 0) {
            arcPaint.color = levelBackground()
            canvas.drawArc(oval, START_ANGLE, SWEEP * percent / 100f, false, arcPaint)
        }
        fillPaint.color = gapColor
        canvas.drawCircle(cx, by, (BADGE_DP / 2f + 1f) * d, fillPaint)
        fillPaint.color = surface
        canvas.drawCircle(cx, cy, r - STROKE_DP / 2f * d - d, fillPaint)

        artwork?.let {
            src.set(0, 0, it.width, it.height)
            dst.set(cx - ART_DP / 2f * d, cy - ART_DP / 2f * d, cx + ART_DP / 2f * d, cy + ART_DP / 2f * d)
            canvas.drawBitmap(it, src, dst, bitmapPaint)
        }

        if (percent >= 0) {
            fillPaint.color = levelBackground()
            canvas.drawCircle(cx, by, BADGE_DP / 2f * d, fillPaint)
            glyph(canvas, cx, by)
        }
    }

    private fun glyph(canvas: Canvas, cx: Float, by: Float) {
        val on = levelContent()
        if (charging) {
            fillPaint.color = on
            bolt.reset()
            bolt.moveTo(cx + 1.6f * d, by - 6.8f * d)
            bolt.lineTo(cx - 3.6f * d, by + 0.8f * d)
            bolt.lineTo(cx - 0.4f * d, by + 0.8f * d)
            bolt.lineTo(cx - 1.6f * d, by + 6.8f * d)
            bolt.lineTo(cx + 3.6f * d, by - 0.8f * d)
            bolt.lineTo(cx + 0.4f * d, by - 0.8f * d)
            bolt.close()
            canvas.drawPath(bolt, fillPaint)
            return
        }
        glyphStroke.color = on
        oval.set(cx - 3.6f * d, by - 6.4f * d, cx + 3.6f * d, by + 7.2f * d)
        canvas.drawRoundRect(oval, 1.8f * d, 1.8f * d, glyphStroke)
        fillPaint.color = on
        oval.set(cx - 1.6f * d, by - 8.2f * d, cx + 1.6f * d, by - 6.4f * d)
        canvas.drawRoundRect(oval, 0.9f * d, 0.9f * d, fillPaint)
        val fill = maxOf(2f, 13f * percent / 100f)
        oval.set(cx - 2.2f * d, by + (5.6f - fill) * d, cx + 2.2f * d, by + 5.6f * d)
        canvas.drawRoundRect(oval, d, d, fillPaint)
    }

    /** As Settings' indicator: charging and 51 to 100 are `low`, 20 to 50 `medium`, 1 to 19 `high`. */
    private fun levelBackground(): Int = context.getColor(
        when {
            charging || percent > 50 -> ThemeR.color.settingslib_colorBackgroundLevel_low
            percent >= 20 -> ThemeR.color.settingslib_colorBackgroundLevel_medium
            percent >= 1 -> ThemeR.color.settingslib_colorBackgroundLevel_high
            else -> ThemeR.color.settingslib_materialColorOnSurface
        }
    )

    private fun levelContent(): Int = context.getColor(
        when {
            charging || percent > 50 -> ThemeR.color.settingslib_colorContentLevel_low
            percent >= 20 -> ThemeR.color.settingslib_colorContentLevel_medium
            percent >= 1 -> ThemeR.color.settingslib_colorContentLevel_high
            else -> ThemeR.color.settingslib_materialColorSurfaceBright
        }
    )

    private companion object {
        const val WIDTH_DP = 80f
        const val HEIGHT_DP = 90f
        const val STROKE_DP = 6f
        const val BADGE_DP = 24f
        const val ART_DP = 48f

        // off = (24 - 6) / 2 = 9; radius = min(80, 90 - 9) / 2 - 3 = 37; centre y = (90 + 9) / 2 = 49.5
        const val RADIUS_DP = 37f
        const val CENTER_Y_DP = 49.5f
        const val START_ANGLE = 300f
        const val SWEEP = 300f
    }
}
