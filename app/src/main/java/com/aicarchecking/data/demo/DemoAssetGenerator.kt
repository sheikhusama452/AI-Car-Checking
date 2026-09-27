package com.aicarchecking.data.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.aicarchecking.media.image.ImageUtils
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates the bundled Demo Mode evidence on device (illustrations, not real vehicle photos),
 * so the APK stays small and no copyrighted imagery is shipped. Every image is stamped "DEMO".
 */
class DemoAssetGenerator(private val dir: File) {

    private val w = 1280
    private val h = 960

    private fun canvasBitmap(block: Canvas.() -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply(block)
        return bmp
    }

    private fun save(name: String, bmp: Bitmap): File {
        val f = ImageUtils.saveJpeg(bmp, File(dir, name), 90)
        bmp.recycle()
        return f
    }

    private fun Canvas.stamp(caption: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 183, 3); textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
        drawText("DEMO", 32f, 64f, p)
        p.apply { color = Color.argb(220, 255, 255, 255); textSize = 30f; typeface = Typeface.DEFAULT }
        drawText(caption, 32f, h - 36f, p)
    }

    private fun Canvas.backdrop() {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, Color.rgb(170, 190, 210), Color.rgb(220, 225, 230), Shader.TileMode.CLAMP)
        drawRect(0f, 0f, w.toFloat(), h * 0.6f, p)
        p.shader = null
        p.color = Color.rgb(90, 92, 95)
        drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), p)
    }

    /** Side view of body panels; each entry is (label, colour). */
    fun panels(name: String, caption: String, segments: List<Pair<String, Int>>): File = save(name, canvasBitmap {
        backdrop()
        val top = h * 0.28f
        val bottom = h * 0.68f
        val segW = (w - 160f) / segments.size
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        segments.forEachIndexed { i, (text, colour) ->
            val left = 80f + i * segW
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, bottom, lighten(colour, 0.25f), colour, Shader.TileMode.CLAMP)
            }
            drawRoundRect(RectF(left + 3, top, left + segW - 3, bottom), 18f, 18f, body)
            // reflection streak
            val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255); strokeWidth = 10f }
            drawLine(left + 20, top + 40, left + segW - 20, top + 60, streak)
            drawText(text, left + 20, bottom - 24, label)
        }
        val wheel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 25, 25) }
        drawCircle(260f, bottom, 90f, wheel)
        drawCircle(w - 260f, bottom, 90f, wheel)
        stamp(caption)
    })

    fun dashboard(name: String, odometerText: String, checkEngineOn: Boolean): File = save(name, canvasBitmap {
        drawColor(Color.rgb(12, 14, 18))
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12f; color = Color.rgb(0, 180, 216) }
        drawCircle(360f, 430f, 230f, ring)
        drawCircle(920f, 430f, 230f, ring)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 46f; typeface = Typeface.MONOSPACE }
        drawText("km/h", 310f, 440f, t)
        drawText("x1000 rpm", 800f, 440f, t)
        val odoBox = Paint().apply { color = Color.rgb(30, 34, 40) }
        drawRect(420f, 700f, 860f, 790f, odoBox)
        val odo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 60f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        drawText(odometerText, 440f, 770f, odo)
        if (checkEngineOn) {
            val amber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 0) }
            drawRoundRect(RectF(600f, 560f, 700f, 630f), 10f, 10f, amber)
            drawRect(580f, 580f, 600f, 610f, amber)
            drawRect(700f, 580f, 720f, 610f, amber)
            val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 0); textSize = 28f }
            drawText("CHECK", 610f, 670f, lbl)
        }
        stamp("Demo dashboard — ignition on, engine running")
    })

    fun exhaustFrame(name: String, smokeAlpha: Int, frameCaption: String): File = save(name, canvasBitmap {
        backdrop()
        val bumper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 200, 205) }
        drawRoundRect(RectF(100f, 380f, 1180f, 600f), 30f, 30f, bumper)
        val pipe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 60, 60) }
        drawOval(RectF(880f, 580f, 1000f, 640f), pipe)
        val rnd = Random(smokeAlpha)
        repeat(18) { i ->
            val smoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((smokeAlpha - i * 6).coerceAtLeast(10), 150, 160, 190)
            }
            drawCircle(900f - i * 38f + rnd.nextInt(-12, 12), 640f + rnd.nextInt(-20, 40), 40f + i * 6f, smoke)
        }
        stamp(frameCaption)
    })

    fun engineBayFrame(name: String, caption: String, seed: Int): File = save(name, canvasBitmap {
        drawColor(Color.rgb(40, 42, 45))
        val block = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 112, 115) }
        drawRoundRect(RectF(360f, 260f, 900f, 640f), 20f, 20f, block)
        val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 22) }
        drawRoundRect(RectF(420f, 300f, 840f, 420f), 16f, 16f, cover)
        val hose = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 15, 15); style = Paint.Style.STROKE; strokeWidth = 26f }
        drawLine(900f, 400f, 1120f, 300f, hose)
        drawLine(360f, 500f, 160f, 620f, hose)
        val battery = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 35) }
        drawRect(980f, 520f, 1200f, 700f, battery)
        val term = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 60, 50) }
        drawCircle(1020f + seed * 4, 520f, 16f, term)
        stamp(caption)
    })

    fun tyre(name: String, caption: String): File = save(name, canvasBitmap {
        drawColor(Color.rgb(70, 72, 75))
        val tyre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 28, 30) }
        drawRoundRect(RectF(340f, 80f, 940f, 880f), 60f, 60f, tyre)
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 12, 12); strokeWidth = 14f }
        for (y in 120..840 step 60) {
            drawLine(560f, y.toFloat(), 900f, y + 30f, groove) // outer: full depth
            drawLine(380f, y.toFloat(), 520f, y + 20f, Paint(groove).apply { color = Color.rgb(24, 24, 26); strokeWidth = 5f }) // inner: worn
        }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        drawText("inner edge", 360f, 930f, lbl)
        drawText("outer edge", 780f, 930f, lbl)
        stamp(caption)
    })

    fun serviceInvoice(name: String, lines: List<String>): File = save(name, canvasBitmap {
        drawColor(Color.WHITE)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        drawText(lines.first(), 80f, 120f, t)
        t.apply { textSize = 34f; typeface = Typeface.DEFAULT }
        lines.drop(1).forEachIndexed { i, s -> drawText(s, 80f, 200f + i * 60f, t) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 255, 140, 0); textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
        drawText("DEMO DOCUMENT", 760f, 900f, p)
    })

    /** 16-bit PCM mono WAV: idle hum with a light rhythmic tick (scripted demo sample). */
    fun engineSound(name: String, seconds: Int = 12): File {
        val rate = 16_000
        val samples = rate * seconds
        val file = File(dir, name)
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            fun le32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
            fun le16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
            out.writeBytes("RIFF"); le32(36 + samples * 2); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(samples * 2)
            val rnd = Random(7)
            val tickPeriod = rate / 12
            for (i in 0 until samples) {
                val t = i.toDouble() / rate
                var s = 0.35 * sin(2 * PI * 28 * t) + 0.2 * sin(2 * PI * 56 * t) + 0.05 * (rnd.nextDouble() - 0.5)
                val sinceTick = i % tickPeriod
                if (sinceTick < 60) s += 0.25 * (1 - sinceTick / 60.0) * sin(2 * PI * 2400 * t)
                le16((s.coerceIn(-1.0, 1.0) * 30000).toInt())
            }
        }
        return file
    }

    private fun lighten(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt(),
    )
}
