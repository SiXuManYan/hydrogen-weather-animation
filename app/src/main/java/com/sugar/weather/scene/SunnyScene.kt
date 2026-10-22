package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 晴天场景。
 *
 * 白天：8 层绕中心自转的 12/10 边形光晕（用「穿过圆心的粗线」拼出带凹角的色带）+ 4 个六边形云。
 * 夜间：切换为 23 条大圆弧组成的流星效果，背景由 [SceneType] 切成深蓝。
 * 太阳中心始终位于屏幕上方，初始时最外圈光晕的下沿落在屏幕约 1/3 处。
 */
class SunnyScene : WeatherScene {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcRect = RectF()
    private val cloudPath = Path()

    // 每层预计算的粗线端点（单位圆坐标）。绘制时用 canvas.scale(radius) 放大，
    // 避免每帧重新计算三角函数和顶点。
    private val haloLines = arrayOfNulls<FloatArray>(SunnySceneConfig.HALO_RADIUS_DP.size)
    // 线宽取单位圆上相邻顶点的距离，缩放后恰好等于该层的实际边长。
    private val haloUnitStroke = FloatArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloRadiusPx = FloatArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloDepthPx = FloatArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloAngles = FloatArray(SunnySceneConfig.HALO_RADIUS_DP.size) {
        SunnySceneConfig.HALO_PHASE_DEG[it]
    }
    private val haloFromR = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloFromG = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloFromB = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloToR = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloToG = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)
    private val haloToB = IntArray(SunnySceneConfig.HALO_RADIUS_DP.size)

    private val cloudRadiusPx = FloatArray(SunnySceneConfig.CLOUD_RADIUS_DP.size)
    private val cloudDepthPx = FloatArray(SunnySceneConfig.CLOUD_RADIUS_DP.size)
    private val cloudAngles = FloatArray(SunnySceneConfig.CLOUD_RADIUS_DP.size) {
        SunnySceneConfig.CLOUD_PHASE_DEG[it]
    }

    private val arcAngles = FloatArray(SunnySceneConfig.ARC_RADIUS_DP.size)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var initialVertical = 0f
    private var smoothedH = 0f
    private var smoothedV = 0f
    private var scale = 1f
    private var positionInitialized = false
    private var colorT = SunnySceneConfig.COLOR_WAVE_START
    private var colorDirection = 1f
    private var pitchAbs = SunnySceneConfig.PITCH_NEUTRAL_DEG
    private var rollAbs = 0f

    init {
        for (i in 0 until haloLines.size) {
            haloLines[i] = buildHaloLines(SunnySceneConfig.HALO_SIDES[i])
            haloUnitStroke[i] = 2f * sin(PI / SunnySceneConfig.HALO_SIDES[i]).toFloat()
            haloFromR[i] = (SunnySceneConfig.HALO_COLOR_FROM[i] shr 16) and 0xFF
            haloFromG[i] = (SunnySceneConfig.HALO_COLOR_FROM[i] shr 8) and 0xFF
            haloFromB[i] = SunnySceneConfig.HALO_COLOR_FROM[i] and 0xFF
            haloToR[i] = (SunnySceneConfig.HALO_COLOR_TO[i] shr 16) and 0xFF
            haloToG[i] = (SunnySceneConfig.HALO_COLOR_TO[i] shr 8) and 0xFF
            haloToB[i] = SunnySceneConfig.HALO_COLOR_TO[i] and 0xFF
        }
        buildCloudPath()
    }

    /**
     * 生成一层光晕的 6 条（10 边形为 5 条）粗线端点，坐标位于单位圆内。
     *
     * 原实现的画法：12 个顶点均匀分布，取相邻两点中点，再与其对边的中点连线，
     * 得到 6 条穿过圆心的线；由于线宽等于相邻顶点距离，叠加后就是目标色带，
     * 而不是普通的正多边形填充。
     */
    private fun buildHaloLines(sides: Int): FloatArray {
        val half = sides / 2
        val result = FloatArray(half * 4)
        val vx = FloatArray(sides)
        val vy = FloatArray(sides)
        for (i in 0 until sides) {
            val angle = Math.toRadians(i * 360.0 / sides)
            vx[i] = cos(angle).toFloat()
            vy[i] = sin(angle).toFloat()
        }
        var out = 0
        for (i in 0 until half) {
            val next = (i + 1) % sides
            val opposite = i + half
            val oppositeNext = (opposite + 1) % sides
            result[out++] = (vx[i] + vx[next]) * 0.5f
            result[out++] = (vy[i] + vy[next]) * 0.5f
            result[out++] = (vx[opposite] + vx[oppositeNext]) * 0.5f
            result[out++] = (vy[opposite] + vy[oppositeNext]) * 0.5f
        }
        return result
    }

    private fun buildCloudPath() {
        cloudPath.reset()
        cloudPath.moveTo(1f, 0f)
        for (i in 1 until 6) {
            val angle = i * PI / 3.0
            cloudPath.lineTo(cos(angle).toFloat(), sin(angle).toFloat())
        }
        cloudPath.close()
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        val radiusFactor = (density * 5f / 3f).toInt()
        // 与原 App 一致的密度补偿公式：radiusPx = radiusDp * density * floor(density * 5/3)。
        for (i in haloRadiusPx.indices) {
            haloRadiusPx[i] = SunnySceneConfig.HALO_RADIUS_DP[i] * density * radiusFactor
            haloDepthPx[i] = SunnySceneConfig.HALO_DEPTH_DP[i] * density
        }
        for (i in cloudRadiusPx.indices) {
            cloudRadiusPx[i] = SunnySceneConfig.CLOUD_RADIUS_DP[i] * density
            cloudDepthPx[i] = SunnySceneConfig.CLOUD_DEPTH_DP[i] * density
        }
        // 不同机型的屏幕比例不同，这里让最外圈光晕下沿保持在屏幕约 1/3 处。
        initialVertical = -height / 6f - haloRadiusPx[0]
        if (!positionInitialized) {
            smoothedV = initialVertical
            positionInitialized = true
        }
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        if (width == 0 || height == 0) return
        val dt = dtMs / 1000f
        if (dt <= 0f) return

        pitchAbs = abs(tilt.pitchDeg)
        rollAbs = abs(tilt.rollDeg)
        // 以竖直握持（约 90°）为俯仰中性点：手机向前/向后放倒时太阳整体上移/下移。
        val pitchOffset = (pitchAbs - SunnySceneConfig.PITCH_NEUTRAL_DEG).coerceIn(-90f, 90f)
        val roll = tilt.rollDeg.coerceIn(-90f, 90f)

        // 竖直握持时俯仰偏移为 0；向前/向后放倒手机时太阳相应上移/下移。
        val targetH = SunnySceneConfig.HORIZONTAL_RANGE_DP * density * roll / 90f
        val targetV = initialVertical +
            SunnySceneConfig.VERTICAL_RANGE_DP * density * pitchOffset / 180f
        // 速度跟随 + 接近目标时按距离减速，避免传感器抖动造成画面抖动。
        smoothedH = follow(smoothedH, targetH, dt)
        smoothedV = follow(smoothedV, targetV, dt)

        // 俯仰越大，光晕整体略微缩小，增强“推拉感”。
        val targetScale = 1f - 0.05f * (abs(pitchOffset) / 90f)
        scale += (targetScale - scale) * min(1f, dt * 6f)

        for (i in haloAngles.indices) {
            haloAngles[i] = (haloAngles[i] + SunnySceneConfig.HALO_SPIN_DEG_PER_SEC[i] * dt) % 720f
        }
        for (i in cloudAngles.indices) {
            cloudAngles[i] = (cloudAngles[i] + SunnySceneConfig.CLOUD_SPIN_DEG_PER_SEC[i] * dt) % 720f
        }
        for (i in arcAngles.indices) {
            val spin = SunnySceneConfig.ARC_SPIN_DEG_PER_SEC[i]
            if (spin != 0f) {
                arcAngles[i] = (arcAngles[i] + spin * dt) % 360f
            }
        }

        // 颜色在起止色之间做三角波往返；8 层共用同一相位，和原实现一样同步呼吸。
        colorT += colorDirection * dt / SunnySceneConfig.COLOR_WAVE_HALF_PERIOD_SEC
        if (colorT >= 1f) {
            colorT = 1f
            colorDirection = -1f
        } else if (colorT <= 0f) {
            colorT = 0f
            colorDirection = 1f
        }
    }

    private fun follow(current: Float, target: Float, dt: Float): Float {
        val distance = target - current
        val absDistance = abs(distance)
        if (absDistance < 0.01f) return target
        val maxSpeed = SunnySceneConfig.FOLLOW_MAX_SPEED_DP * density
        val slowZone = SunnySceneConfig.FOLLOW_SLOW_ZONE_DP * density
        // 距离目标较远时全速追踪；进入慢速区后速度与剩余距离成正比，平滑吸附。
        val speed = if (absDistance < slowZone) maxSpeed * absDistance / slowZone else maxSpeed
        val step = min(absDistance, speed * dt)
        return if (distance > 0f) current + step else current - step
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        if (night) {
            drawNight(canvas)
        } else {
            drawDay(canvas)
        }
    }

    private fun drawDay(canvas: Canvas) {
        val centerX = width * 0.5f
        val centerY = height * 0.5f + smoothedV
        // 先画外层大半径，再画内层小半径；每层通过 canvas 变换完成平移、旋转和缩放。
        // strokeWidth 使用单位值，会随 canvas.scale 自动放大到实际线宽。
        for (i in haloAngles.indices) {
            haloPaint.color = haloColor(i)
            haloPaint.strokeWidth = haloUnitStroke[i]
            canvas.save()
            canvas.translate(
                centerX + haloDepthPx[i] * smoothedH / SunnySceneConfig.DEPTH_DIVISOR,
                centerY
            )
            canvas.rotate(haloAngles[i])
            val radius = haloRadiusPx[i] * scale
            canvas.scale(radius, radius)
            val lines = haloLines[i]!!
            var index = 0
            while (index < lines.size) {
                canvas.drawLine(lines[index], lines[index + 1], lines[index + 2], lines[index + 3], haloPaint)
                index += 4
            }
            canvas.restore()
        }

        // 云的透明度随俯仰变化：竖直握持时最明显，手机放平后逐渐淡出。
        val alphaFactor = ((pitchAbs - SunnySceneConfig.CLOUD_ALPHA_START_DEG) /
            (SunnySceneConfig.CLOUD_ALPHA_FULL_DEG - SunnySceneConfig.CLOUD_ALPHA_START_DEG))
            .coerceIn(0f, 1f)
        if (alphaFactor <= 0f) return
        val baseAlpha = (SunnySceneConfig.CLOUD_MAX_ALPHA * alphaFactor).toInt()
        // 最小的 10dp 六边形只在明显左右倾斜时淡入，默认构图保持 3 个可见六边形。
        val tinyFactor = ((rollAbs - 8f) / 30f).coerceIn(0f, 1f)
        val verticalParallax = smoothedV - initialVertical
        // 反向遍历：先画远处的小云，再画近处的大云，保证遮挡关系正确。
        for (i in cloudAngles.indices.reversed()) {
            var alpha = baseAlpha
            if (i == cloudAngles.size - 1) {
                alpha = (alpha * tinyFactor).toInt()
            }
            if (alpha <= 0) continue
            cloudPaint.alpha = alpha
            val radius = cloudRadiusPx[i] * scale
            val x = width * 0.5f + smoothedH +
                cloudDepthPx[i] * smoothedH / SunnySceneConfig.DEPTH_DIVISOR
            val y = height * SunnySceneConfig.CLOUD_BASE_Y_FRACTION[i] + verticalParallax
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(cloudAngles[i])
            canvas.scale(radius, radius)
            canvas.drawPath(cloudPath, cloudPaint)
            canvas.restore()
        }
    }

    private fun drawNight(canvas: Canvas) {
        // 夜间弧线的圆心固定在屏幕左上方远处，再叠加 +1250dp 的纵向下移，
        // 让半径几百 dp 的大圆只在屏幕上露出局部，形成流星/光带一样的斜线。
        val centerX = SunnySceneConfig.ARC_OFFSET_X_DP * density + smoothedH
        val centerY = SunnySceneConfig.ARC_OFFSET_Y_DP * density + smoothedV +
            SunnySceneConfig.ARC_EXTRA_Y_DP * density
        for (i in arcAngles.indices) {
            val radius = SunnySceneConfig.ARC_RADIUS_DP[i] * density
            arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            arcPaint.strokeWidth = SunnySceneConfig.ARC_WIDTH_DP[i] * density
            arcPaint.color = SunnySceneConfig.ARC_COLOR[i]
            val count = SunnySceneConfig.ARC_COUNT[i]
            val start = SunnySceneConfig.ARC_START_DEG[i]
            val sweep = SunnySceneConfig.ARC_SWEEP_DEG[i]
            for (slot in 0 until count) {
                // 前两条弧线相位相差 180°；第三条起用补角公式，让局部弧线分布在圆的不同象限。
                val angle = if (slot < 2) {
                    start + slot * 180f + arcAngles[i]
                } else {
                    (180f - sweep) - start + (slot - 2) * 180f + arcAngles[i]
                }
                // 只绘制会经过屏幕下半部分的弧线，避免夜空被过多弧线填满。
                if (angle <= 90f || angle + sweep >= 270f) {
                    canvas.drawArc(arcRect, angle, sweep, false, arcPaint)
                }
            }
        }
    }

    private fun haloColor(index: Int): Int {
        // 在起始色和结束色之间做线性插值，colorT 由 update 中的三角波驱动。
        val r = (haloFromR[index] + (haloToR[index] - haloFromR[index]) * colorT).toInt()
        val g = (haloFromG[index] + (haloToG[index] - haloFromG[index]) * colorT).toInt()
        val b = (haloFromB[index] + (haloToB[index] - haloFromB[index]) * colorT).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
