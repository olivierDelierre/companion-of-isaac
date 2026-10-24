package app.isaaccompanion.platform

import android.graphics.BitmapFactory
import app.isaaccompanion.ui.gfx.ArgbImage
import java.io.InputStream

/** Decodes a PNG without any density scaling or filtering. */
fun decodeArgb(input: InputStream): ArgbImage? {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPremultiplied = false
    }
    val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val image = ArgbImage(bitmap.width, bitmap.height, pixels)
    bitmap.recycle()
    return image
}
