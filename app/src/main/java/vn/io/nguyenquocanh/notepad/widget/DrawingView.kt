package vn.io.nguyenquocanh.notepad.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import kotlin.math.max

/**
 * Canvas vẽ tay hỗ trợ bút cảm ứng (TouchPen / S Pen / USI):
 * - Lực nhấn điều chỉnh độ dày nét
 * - Đầu tẩy (TOOL_TYPE_ERASER) hoặc giữ nút bút để tẩy
 * - Điểm lịch sử (historical) cho nét mượt, unbuffered dispatch giảm độ trễ
 * - Rê bút (hover) hiển thị con trỏ kích thước nét
 * - Chế độ chỉ nhận bút để chống chạm nhầm lòng bàn tay
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Stroke(val color: Int, val width: Float, val eraser: Boolean) {
        /** Bộ ba x, y, pressure. */
        val points = ArrayList<Float>(256)
        val count: Int get() = points.size / 3
    }

    private val strokes = ArrayList<Stroke>()
    private val redoStack = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var activePointerId = INVALID_POINTER
    private var baseBitmap: Bitmap? = null
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null

    private val density = resources.displayMetrics.density
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.argb(170, 90, 90, 90)
    }
    private var hoverX = Float.NaN
    private var hoverY = Float.NaN
    private var hoverErasing = false

    var penColor: Int = Color.BLACK
    var penWidth: Float = 4f * density
    var eraserMode: Boolean = false
    var stylusOnly: Boolean = false
    var onStateChanged: (() -> Unit)? = null

    var isDirty: Boolean = false
        private set

    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_CROSSHAIR)
    }

    fun hasContent(): Boolean = baseBitmap != null || strokes.isNotEmpty()

    fun setBaseBitmap(bitmap: Bitmap?) {
        baseBitmap = bitmap
        strokes.clear()
        redoStack.clear()
        isDirty = false
        rebuild()
        invalidate()
        onStateChanged?.invoke()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.lastIndex))
        changed()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.lastIndex))
        changed()
    }

    fun clear() {
        baseBitmap = null
        strokes.clear()
        redoStack.clear()
        changed()
    }

    fun markSaved() {
        isDirty = false
    }

    /** Ảnh PNG trong suốt chứa toàn bộ nét vẽ, null nếu trống. */
    fun exportBitmap(): Bitmap? {
        if (!hasContent()) return null
        val w = max(width, baseBitmap?.width ?: 0)
        val h = max(height, baseBitmap?.height ?: 0)
        if (w <= 0 || h <= 0) return baseBitmap
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        render(Canvas(out))
        return out
    }

    private fun changed() {
        isDirty = true
        rebuild()
        invalidate()
        onStateChanged?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        cache = bmp
        cacheCanvas = Canvas(bmp)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (!hoverX.isNaN()) {
            val w = if (hoverErasing) penWidth * ERASER_SCALE else penWidth
            canvas.drawCircle(hoverX, hoverY, max(w / 2f, 2f * density), hoverPaint)
        }
    }

    private fun rebuild() {
        val bmp = cache ?: return
        val c = cacheCanvas ?: return
        bmp.eraseColor(Color.TRANSPARENT)
        render(c)
        current?.let { drawStroke(c, it, 0) }
    }

    private fun render(c: Canvas) {
        baseBitmap?.let { c.drawBitmap(it, 0f, 0f, null) }
        for (s in strokes) drawStroke(c, s, 0)
    }

    private fun drawStroke(c: Canvas, s: Stroke, from: Int) {
        val pts = s.points
        val n = s.count
        if (n == 0) return
        inkPaint.color = s.color
        inkPaint.xfermode = if (s.eraser) clearXfermode else null
        if (from == 0) {
            inkPaint.strokeWidth = widthFor(s, pts[2])
            c.drawPoint(pts[0], pts[1], inkPaint)
        }
        var i = max(1, from)
        while (i < n) {
            val a = (i - 1) * 3
            val b = i * 3
            inkPaint.strokeWidth = widthFor(s, (pts[a + 2] + pts[b + 2]) / 2f)
            c.drawLine(pts[a], pts[a + 1], pts[b], pts[b + 1], inkPaint)
            i++
        }
        inkPaint.xfermode = null
    }

    private fun widthFor(s: Stroke, pressure: Float): Float =
        if (s.eraser) s.width * ERASER_SCALE
        else s.width * (0.35f + 0.9f * pressure.coerceIn(0f, 1f))

    private fun addPoint(s: Stroke, x: Float, y: Float, pressure: Float) {
        s.points.add(x)
        s.points.add(y)
        s.points.add(pressure)
        cacheCanvas?.let { drawStroke(it, s, s.count - 1) }
    }

    private fun isStylus(tool: Int) =
        tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

    private fun isEraserInput(event: MotionEvent, tool: Int): Boolean {
        if (tool == MotionEvent.TOOL_TYPE_ERASER) return true
        val buttons = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        return tool == MotionEvent.TOOL_TYPE_STYLUS && (event.buttonState and buttons) != 0
    }

    private fun pressureOf(tool: Int, raw: Float): Float = if (isStylus(tool)) raw else 0.6f

    private fun accepts(tool: Int): Boolean = !stylusOnly || isStylus(tool)

    private fun startStroke(event: MotionEvent, index: Int) {
        val tool = event.getToolType(index)
        if (current != null || !accepts(tool)) return
        requestUnbufferedDispatch(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        hoverX = Float.NaN
        val s = Stroke(penColor, penWidth, eraserMode || isEraserInput(event, tool))
        current = s
        activePointerId = event.getPointerId(index)
        addPoint(s, event.getX(index), event.getY(index), pressureOf(tool, event.getPressure(index)))
        invalidate()
    }

    private fun finishStroke(cancelled: Boolean) {
        val s = current ?: return
        current = null
        activePointerId = INVALID_POINTER
        if (cancelled) {
            rebuild()
        } else {
            strokes.add(s)
            redoStack.clear()
            isDirty = true
        }
        invalidate()
        onStateChanged?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                startStroke(event, event.actionIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val tool = event.getToolType(index)
                for (h in 0 until event.historySize) {
                    addPoint(
                        s,
                        event.getHistoricalX(index, h),
                        event.getHistoricalY(index, h),
                        pressureOf(tool, event.getHistoricalPressure(index, h))
                    )
                }
                addPoint(s, event.getX(index), event.getY(index), pressureOf(tool, event.getPressure(index)))
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    finishStroke((event.flags and FLAG_CANCELED) != 0)
                }
            }

            MotionEvent.ACTION_UP -> {
                val wasDrawing = current != null
                finishStroke((event.flags and FLAG_CANCELED) != 0)
                if (wasDrawing) performClick()
            }

            MotionEvent.ACTION_CANCEL -> finishStroke(true)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        if (!isStylus(tool)) return super.onHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = event.x
                hoverY = event.y
                hoverErasing = eraserMode || isEraserInput(event, tool)
            }
            else -> hoverX = Float.NaN
        }
        invalidate()
        return true
    }

    companion object {
        private const val INVALID_POINTER = -1
        private const val ERASER_SCALE = 3f
        /** MotionEvent.FLAG_CANCELED (API 33): hệ thống đánh dấu chạm lòng bàn tay. */
        private const val FLAG_CANCELED = 0x20
    }
}
