package ru.stresh.day24watchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.WatchFaceLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.Duration
import java.time.ZonedDateTime

class AnalogWatchCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<AnalogWatchCanvasRenderer.AnalogSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class AnalogSharedAssets : SharedAssets {
        override fun onDestroy() {
        }
    }

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val clockHandPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2.dp
        pathEffect = CornerPathEffect(10f)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 14.sp
        typeface = ResourcesCompat.getFont(context, R.font.barlow_light)
        textAlign = Paint.Align.CENTER
    }
    private val hourMarksPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = HOUR_MARKS_WIDTH
    }
    private val minuteMarksPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = MINUTE_MARKS_WIDTH
    }

    private val centerCirclePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private lateinit var hourHandFill: Path
    private lateinit var hourHandBorder: Path
    private lateinit var minuteHandFill: Path
    private lateinit var minuteHandBorder: Path
    private lateinit var secondHand: Path

    private var currentWatchFaceSize = Rect(0, 0, 0, 0)


    override suspend fun createSharedAssets(): AnalogSharedAssets {
        return AnalogSharedAssets()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: AnalogSharedAssets
    ) {
        val backgroundColor = Color.BLACK

        canvas.drawColor(backgroundColor)

        drawComplications(canvas, zonedDateTime)

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS_OVERLAY)) {
            drawClockHands(canvas, bounds, zonedDateTime)
        }

        if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
            renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)
        ) {
            drawHourNumbers(
                canvas,
                bounds,
                Color.WHITE,
            )
//            drawHourMarks(canvas, bounds, Color.WHITE)
            drawMinuteMarks(canvas, bounds, Color.GRAY)
        }
    }

    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun drawClockHands(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime
    ) {
        if (currentWatchFaceSize != bounds) {
            currentWatchFaceSize = bounds
            recalculateClockHands(bounds)
        }

        val secondOfDay = zonedDateTime.toLocalTime().toSecondOfDay()
        val secondsPerHourHandRotation = Duration.ofHours(24).seconds
        val secondsPerMinuteHandRotation = Duration.ofHours(1).seconds

        val hourRotation =
            secondOfDay.rem(secondsPerHourHandRotation) * 360f / secondsPerHourHandRotation
        val minuteRotation =
            secondOfDay.rem(secondsPerMinuteHandRotation) * 360f / secondsPerMinuteHandRotation

        canvas.withScale(
            x = WATCH_HAND_SCALE,
            y = WATCH_HAND_SCALE,
            pivotX = bounds.exactCenterX(),
            pivotY = bounds.exactCenterY()
        ) {
            val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT

            clockHandPaint.style = if (drawAmbient) Paint.Style.STROKE else Paint.Style.FILL
            clockHandPaint.color = Color.WHITE

            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(hourHandBorder, clockHandPaint)
            }

            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                drawPath(minuteHandBorder, clockHandPaint)
            }

            drawHourAndMinuteCenterCircle(canvas, bounds)

            if (!drawAmbient) {
                drawSecondsCenterCircle(canvas, bounds)
                clockHandPaint.color = Color.RED

                val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
                val secondsRotation = secondOfDay.rem(secondsPerSecondHandRotation) * 360f /
                        secondsPerSecondHandRotation

                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
                    drawPath(secondHand, clockHandPaint)
                }
            }
            drawMainCenterCircle(canvas, bounds)
        }
    }

    private fun recalculateClockHands(bounds: Rect) {
        hourHandBorder = createClockHand(
            bounds,
            HOUR_HAND_LENGTH_FRACTION,
            HOUR_HAND_WIDTH_FRACTION,
            GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION,
            30f,
            30f
        )
        hourHandFill = hourHandBorder

        minuteHandBorder = createClockHand(
            bounds,
            MINUTE_HAND_LENGTH_FRACTION_DEFAULT,
            MINUTE_HAND_WIDTH_FRACTION,
            GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION,
            30f,
            30f
        )
        minuteHandFill = minuteHandBorder

        secondHand = createClockHand(
            bounds,
            SECOND_HAND_LENGTH_FRACTION,
            SECOND_HAND_WIDTH_FRACTION,
            GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION,
            30f,
            30f
        )
    }

    private fun createClockHand(
        bounds: Rect,
        length: Float,
        thickness: Float,
        gapBetweenHandAndCenter: Float,
        roundedCornerXRadius: Float,
        roundedCornerYRadius: Float
    ): Path {
        val width = bounds.width()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val left = centerX - thickness / 2 * width
        val top = centerY - (gapBetweenHandAndCenter + length) * width
        val right = centerX + thickness / 2 * width
        val bottom = centerY - gapBetweenHandAndCenter * width
        val path = Path()

        val a = PointF(left + (thickness / 2 * width) + 2, top)
        val b = PointF(left + (thickness / 2 * width) + 4, top)
        val c = PointF(right, bottom)
        val d = PointF(left, bottom)

        path.moveTo(a.x, a.y)
        path.lineTo(a.x, a.y)
        path.lineTo(b.x, b.y)
        path.lineTo(c.x, c.y)
        path.lineTo(d.x, d.y)
        path.close()

//        if (roundedCornerXRadius != 0.0f || roundedCornerYRadius != 0.0f) {
//            path.addRoundRect(
//                left,
//                top,
//                right,
//                bottom,
//                roundedCornerXRadius,
//                roundedCornerYRadius,
//                Path.Direction.CW
//            )
//        } else {
//            path.addRect(
//                left,
//                top,
//                right,
//                bottom,
//                Path.Direction.CW
//            )
//        }
        return path
    }

    private fun drawHourNumbers(
        canvas: Canvas,
        bounds: Rect,
        outerElementColor: Int
    ) {
        val textBounds = Rect()
        textPaint.color = outerElementColor
        var angle = 0f
        for (i in HOUR_MARKS.indices) {
            textPaint.getTextBounds(HOUR_MARKS[i], 0, HOUR_MARKS[i].length, textBounds)
            val y = HOUR_NUMBERS_MARGIN / 2 - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.withRotation(angle, bounds.exactCenterX(), bounds.exactCenterY()) {
                if (i in 4..8) {
                    val rotationY = y + (textPaint.descent() + textPaint.ascent()) / 2
                    canvas.withRotation(180f, bounds.exactCenterX(), rotationY) {
                        canvas.drawText(
                            HOUR_MARKS[i],
                            bounds.exactCenterX(),
                            y,
                            textPaint
                        )
                    }
                } else {
                    canvas.drawText(
                        HOUR_MARKS[i],
                        bounds.exactCenterX(),
                        y,
                        textPaint
                    )
                }
            }
            angle += 30
        }
    }

    private fun drawHourMarks(
        canvas: Canvas,
        bounds: Rect,
        outerElementColor: Int
    ) {
        hourMarksPaint.color = outerElementColor
        var angle = 15f
        for (i in 1..HOUR_MARKS_COUNT) {
            canvas.withRotation(angle, bounds.exactCenterX(), bounds.exactCenterY()) {
                canvas.drawLine(
                    bounds.exactCenterX(),
                    HOUR_MARKS_START,
                    bounds.exactCenterX(),
                    HOUR_MARKS_SIZE,
                    hourMarksPaint
                )
            }
            angle += 30
        }
    }

    private fun drawMinuteMarks(
        canvas: Canvas,
        bounds: Rect,
        outerElementColor: Int
    ) {
        minuteMarksPaint.color = outerElementColor
        var mainAngle = 0f
        for (h in HOUR_MARKS.indices) {
            canvas.withRotation(mainAngle, bounds.exactCenterX(), bounds.exactCenterY()) {
                var inHourAngle = 6f
                for (i in 0..3) {
                    canvas.withRotation(inHourAngle, bounds.exactCenterX(), bounds.exactCenterY()) {
                        canvas.drawLine(
                            bounds.exactCenterX(),
                            MINUTE_MARKS_MARGIN,
                            bounds.exactCenterX(),
                            MINUTE_MARKS_SIZE + MINUTE_MARKS_MARGIN,
                            minuteMarksPaint
                        )
                    }
                    inHourAngle += 6
                }
            }
            mainAngle += 30
        }
    }

    private fun drawHourAndMinuteCenterCircle(
        canvas: Canvas,
        bounds: Rect,
    ) {
        centerCirclePaint.color = Color.WHITE
        canvas.drawCircle(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            CENTER_HOUR_AND_MINUTE_CIRCLE_RADIUS,
            centerCirclePaint
        )
    }

    private fun drawSecondsCenterCircle(
        canvas: Canvas,
        bounds: Rect,
    ) {
        centerCirclePaint.color = Color.RED
        canvas.drawCircle(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            CENTER_SECONDS_CIRCLE_RADIUS,
            centerCirclePaint
        )
    }

    private fun drawMainCenterCircle(
        canvas: Canvas,
        bounds: Rect,
    ) {
        centerCirclePaint.color = Color.BLACK
        canvas.drawCircle(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            CENTER_MAIN_CIRCLE_RADIUS,
            centerCirclePaint
        )
    }

    companion object {
        private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

        private val HOUR_MARKS = arrayOf("0", "2", "4", "6", "8", "10", "12", "14", "16", "18", "20", "22")
        private const val HOUR_MARKS_COUNT = 12
        private const val HOUR_MARKS_WIDTH = 4f
        private const val HOUR_MARKS_SIZE = 35f
        private const val HOUR_MARKS_START = 10f
        private const val HOUR_NUMBERS_MARGIN = 45f

        private const val MINUTE_MARKS_SIZE = 8f
        private const val MINUTE_MARKS_MARGIN = 18f
        private const val MINUTE_MARKS_WIDTH = 2f

        private const val CENTER_HOUR_AND_MINUTE_CIRCLE_RADIUS = 15f
        private const val CENTER_SECONDS_CIRCLE_RADIUS = 10f
        private const val CENTER_MAIN_CIRCLE_RADIUS = 5f

        private const val WATCH_HAND_SCALE = 1.0f

        private const val HOUR_HAND_LENGTH_FRACTION = 0.21028f
        private const val HOUR_HAND_WIDTH_FRACTION = 0.02336f

        const val MINUTE_HAND_LENGTH_FRACTION_DEFAULT = 0.3783f
        const val MINUTE_HAND_LENGTH_FRACTION_MINIMUM = 0.10000f
        const val MINUTE_HAND_LENGTH_FRACTION_MAXIMUM = 0.40000f
        private const val MINUTE_HAND_WIDTH_FRACTION = 0.0163f

        private const val SECOND_HAND_LENGTH_FRACTION = 0.37383f
        private const val SECOND_HAND_WIDTH_FRACTION = 0.00734f

        private const val GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION = 0f
    }
}
