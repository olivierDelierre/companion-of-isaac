package app.isaaccompanion.ui

import app.isaaccompanion.ui.gfx.Gfx
import app.isaaccompanion.ui.gfx.Sprite
import java.awt.image.BufferedImage

/** Software [Gfx] used by tests to render the real screen code to PNG files. */
class AwtGfx(private val logicalW: Int, private val logicalH: Int, private val scale: Int) : Gfx {
    val image = BufferedImage(logicalW * scale, logicalH * scale, BufferedImage.TYPE_INT_ARGB)
    private val clips = ArrayList<IntArray>()

    private fun inClip(px: Int, py: Int): Boolean {
        for (c in clips) if (px < c[0] || py < c[1] || px >= c[2] || py >= c[3]) return false
        return true
    }

    private fun blend(dst: Int, src: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        val oa = sa + da * (255 - sa) / 255
        fun ch(shift: Int): Int {
            val s = (src shr shift) and 255
            val d = (dst shr shift) and 255
            return if (oa == 0) 0 else (s * sa + d * da * (255 - sa) / 255) / oa
        }
        return (oa shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun put(lx: Int, ly: Int, argb: Int) {
        if (lx < 0 || ly < 0 || lx >= logicalW || ly >= logicalH) return
        for (dy in 0 until scale) for (dx in 0 until scale) {
            val px = lx * scale + dx
            val py = ly * scale + dy
            if (!inClip(px, py)) continue
            image.setRGB(px, py, blend(image.getRGB(px, py), argb))
        }
    }

    override fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        for (yy in y until y + h) for (xx in x until x + w) put(xx, yy, argb)
    }

    override fun draw(sprite: Sprite, x: Int, y: Int, scale: Int, tint: Int) {
        for (sy in 0 until sprite.sh) for (sx in 0 until sprite.sw) {
            val p = sprite.image.pixels[(sprite.sy + sy) * sprite.image.width + sprite.sx + sx]
            if (p ushr 24 == 0) continue
            val c = tinted(p, tint)
            for (dy in 0 until scale) for (dx in 0 until scale) put(x + sx * scale + dx, y + sy * scale + dy, c)
        }
    }

    private fun tinted(p: Int, t: Int): Int {
        if (t == -1) return p
        fun m(shift: Int) = (((p shr shift) and 255) * ((t shr shift) and 255)) / 255
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    override fun pushClip(x: Int, y: Int, w: Int, h: Int) {
        clips += intArrayOf(x * scale, y * scale, (x + w) * scale, (y + h) * scale)
    }

    override fun popClip() {
        clips.removeAt(clips.size - 1)
    }
}
