package uz.gita.cameraopencv.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defAttrStyle: Int = 0
) : View(context, attrs, defAttrStyle) {

    private val paint = Paint().apply {
        color = Color.RED
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val _rects = ArrayList<RectF>()


    fun setRect(rect: List<RectF>) {
        _rects.clear()
        _rects.addAll(rect)
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        _rects.forEach {
            canvas.drawRect(it, paint)
        }
    }
}