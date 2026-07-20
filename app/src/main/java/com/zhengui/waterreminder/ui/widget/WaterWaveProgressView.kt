package com.zhengui.waterreminder.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import com.zhengui.waterreminder.R
import kotlin.math.sin

class WaterWaveProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WaterWaveProgress"
    }

    private var waveColor: Int = Color.parseColor("#2196F3")
    private var waveColor2: Int = Color.parseColor("#64B5F6")
    private var bgColor: Int = Color.parseColor("#E3F2FD")
    private var textColor: Int = Color.WHITE
    private var progressValue: Int = 0
    private var maxProgressValue: Int = 100

    // 动画相关
    private var currentProgress: Float = 0f
    private var waveOffset: Float = 0f

    // 画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 圆形区域
    private val rectF = RectF()

    init {
        Log.d(TAG, "init 开始")
        try {
            attrs?.let {
                Log.d(TAG, "开始读取 XML 属性")
                val ta = context.obtainStyledAttributes(it, R.styleable.WaterWaveProgressView)
                Log.d(TAG, "obtainStyledAttributes 成功")
                waveColor = ta.getColor(R.styleable.WaterWaveProgressView_waveColor, waveColor)
                waveColor2 = ta.getColor(R.styleable.WaterWaveProgressView_waveColor2, waveColor2)
                bgColor = ta.getColor(R.styleable.WaterWaveProgressView_bgColor, bgColor)
                textColor = ta.getColor(R.styleable.WaterWaveProgressView_textColor, textColor)
                progressValue = ta.getInt(R.styleable.WaterWaveProgressView_progress, 0)
                maxProgressValue = ta.getInt(R.styleable.WaterWaveProgressView_maxProgress, 100)
                ta.recycle()
                Log.d(TAG, "XML 属性读取完成: waveColor=$waveColor, bgColor=$bgColor, progress=$progressValue")
            }

            bgPaint.color = bgColor
            borderPaint.style = Paint.Style.STROKE
            borderPaint.strokeWidth = 4f
            borderPaint.color = waveColor
            textPaint.color = textColor
            textPaint.textAlign = Paint.Align.CENTER

            currentProgress = progressValue.toFloat()
            Log.d(TAG, "init 完成")
        } catch (e: Exception) {
            Log.e(TAG, "init 失败", e)
            throw e
        }
    }

    fun setProgress(progress: Int, animate: Boolean = true) {
        val target = progress.coerceIn(0, maxProgressValue).toFloat()
        if (animate) {
            val animator = ValueAnimator.ofFloat(currentProgress, target)
            animator.duration = 800
            animator.interpolator = OvershootInterpolator(0.8f)
            animator.addUpdateListener {
                currentProgress = it.animatedValue as Float
                invalidate()
            }
            animator.start()
        } else {
            currentProgress = target
            invalidate()
        }
        progressValue = progress
    }

    fun setMaxProgress(max: Int) {
        maxProgressValue = max
        invalidate()
    }

    fun setWaveColor(color: Int) {
        waveColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = Math.min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - 4f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 绘制背景圆
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 绘制水波纹
        val waterLevel = cy + radius - (2 * radius * currentProgress / maxProgressValue)

        // 设置渐变
        val shader1 = LinearGradient(cx, waterLevel, cx, cy + radius, waveColor, waveColor2, Shader.TileMode.CLAMP)
        wavePaint.shader = shader1

        val shader2 = LinearGradient(cx, waterLevel, cx, cy + radius, waveColor2, waveColor, Shader.TileMode.CLAMP)
        wavePaint2.shader = shader2

        // 波浪路径1
        val wavePath1 = Path()
        val wavePath2 = Path()
        val amplitude = 8f
        val wavelength = size / 2f

        wavePath1.moveTo(cx - radius, cy + radius)
        wavePath2.moveTo(cx - radius, cy + radius)

        var x = cx - radius
        while (x <= cx + radius) {
            val y1 = waterLevel + amplitude * sin((2 * Math.PI * (x - cx) / wavelength) + waveOffset)
            val y2 = waterLevel + amplitude * sin((2 * Math.PI * (x - cx) / wavelength) + waveOffset + Math.PI)
            wavePath1.lineTo(x, y1.toFloat())
            wavePath2.lineTo(x, y2.toFloat())
            x += 2f
        }

        wavePath1.lineTo(cx + radius, cy + radius)
        wavePath1.close()
        wavePath2.lineTo(cx + radius, cy + radius)
        wavePath2.close()

        // 裁剪到圆形区域
        canvas.save()
        val clipPath = Path()
        clipPath.addCircle(cx, cy, radius - 2f, Path.Direction.CW)
        canvas.clipPath(clipPath)

        canvas.drawPath(wavePath1, wavePaint)
        canvas.drawPath(wavePath2, wavePaint2)

        canvas.restore()

        // 绘制边框
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // 绘制百分比文字
        val percent = if (maxProgressValue > 0) (currentProgress * 100 / maxProgressValue).toInt() else 0
        textPaint.textSize = size / 4f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("${percent}%", cx, textY, textPaint)

        // 更新波浪偏移
        waveOffset += 0.08f
        if (waveOffset > 2 * Math.PI) waveOffset = 0f
        invalidate()
    }
}
