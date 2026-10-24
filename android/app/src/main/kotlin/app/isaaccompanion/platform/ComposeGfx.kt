package app.isaaccompanion.platform

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.isaaccompanion.ui.gfx.ArgbImage
import app.isaaccompanion.ui.gfx.Gfx
import app.isaaccompanion.ui.gfx.Sprite
import app.isaaccompanion.ui.gfx.WHITE
import java.util.WeakHashMap

private val bitmaps = WeakHashMap<ArgbImage, ImageBitmap>()

private fun ArgbImage.toImageBitmap(): ImageBitmap = synchronized(bitmaps) {
    bitmaps.getOrPut(this) {
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** [Gfx] on a Compose [DrawScope]: logical pixels times an integer [scale], never smoothed. */
class ComposeGfx(private val scope: DrawScope, private val scale: Int, private val originX: Int, private val originY: Int) : Gfx {
    private fun fx(x: Int) = (originX + x * scale).toFloat()
    private fun fy(y: Int) = (originY + y * scale).toFloat()

    override fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        if (w <= 0 || h <= 0) return
        scope.drawRect(Color(argb), Offset(fx(x), fy(y)), Size((w * scale).toFloat(), (h * scale).toFloat()))
    }

    override fun draw(sprite: Sprite, x: Int, y: Int, scale: Int, tint: Int) {
        val filter = if (tint == WHITE) null else ColorFilter.tint(Color(tint), BlendMode.Modulate)
        scope.drawImage(
            image = sprite.image.toImageBitmap(),
            srcOffset = IntOffset(sprite.sx, sprite.sy),
            srcSize = IntSize(sprite.sw, sprite.sh),
            dstOffset = IntOffset(originX + x * this.scale, originY + y * this.scale),
            dstSize = IntSize(sprite.sw * scale * this.scale, sprite.sh * scale * this.scale),
            filterQuality = FilterQuality.None,
            colorFilter = filter,
        )
    }

    override fun pushClip(x: Int, y: Int, w: Int, h: Int) {
        val canvas = scope.drawContext.canvas
        canvas.save()
        canvas.clipRect(fx(x), fy(y), fx(x + w), fy(y + h), ClipOp.Intersect)
    }

    override fun popClip() {
        scope.drawContext.canvas.restore()
    }
}
