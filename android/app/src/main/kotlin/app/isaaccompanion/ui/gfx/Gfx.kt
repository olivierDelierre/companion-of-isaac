package app.isaaccompanion.ui.gfx

import app.isaaccompanion.resources.BitmapFont

/** Straight (non premultiplied) ARGB pixels, the only image format the UI code knows about. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixel count does not match ${width}x$height" }
    }
}

/** A rectangular region of an image. */
data class Sprite(val image: ArgbImage, val sx: Int, val sy: Int, val sw: Int, val sh: Int) {
    constructor(image: ArgbImage) : this(image, 0, 0, image.width, image.height)
}

data class IRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h
    fun contains(px: Int, py: Int) = px >= x && py >= y && px < x + w && py < y + h
    fun inset(d: Int) = IRect(x + d, y + d, w - 2 * d, h - 2 * d)
}

const val WHITE = 0xFFFFFFFF.toInt()
const val BLACK = 0xFF000000.toInt()

fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
fun rgb(hex: Int) = hex or (0xFF shl 24)

/**
 * Drawing surface in *logical* pixels. A backend scales every call by an integer factor and never
 * smooths, so pixel art stays crisp.
 */
interface Gfx {
    fun fillRect(x: Int, y: Int, w: Int, h: Int, argb: Int)

    /** Draws [sprite] at [x],[y], each source pixel becoming [scale]x[scale] logical pixels, multiplied by [tint]. */
    fun draw(sprite: Sprite, x: Int, y: Int, scale: Int = 1, tint: Int = WHITE)

    fun pushClip(x: Int, y: Int, w: Int, h: Int)
    fun popClip()
}

fun Gfx.fillRect(r: IRect, argb: Int) = fillRect(r.x, r.y, r.w, r.h, argb)

/** One pixel wide rectangle outline. */
fun Gfx.strokeRect(r: IRect, argb: Int) {
    fillRect(r.x, r.y, r.w, 1, argb)
    fillRect(r.x, r.bottom - 1, r.w, 1, argb)
    fillRect(r.x, r.y, 1, r.h, argb)
    fillRect(r.right - 1, r.y, 1, r.h, argb)
}

/** A font that can lay text out and expose its glyphs as sprites. */
interface GameFont {
    val lineHeight: Int
    fun measure(text: String): Int
    fun glyphs(text: String): List<Pair<Sprite, Pair<Int, Int>>>
}

class BitmapGameFont(private val font: BitmapFont, private val pages: Map<Int, ArgbImage>) : GameFont {
    override val lineHeight: Int get() = font.lineHeight
    override fun measure(text: String) = font.measure(text)
    override fun glyphs(text: String): List<Pair<Sprite, Pair<Int, Int>>> =
        font.layout(text).mapNotNull { p ->
            val img = pages[p.glyph.page] ?: return@mapNotNull null
            Sprite(img, p.glyph.x, p.glyph.y, p.glyph.width, p.glyph.height) to (p.x to p.y)
        }
}

fun Gfx.text(font: GameFont, text: String, x: Int, y: Int, scale: Int = 1, color: Int = WHITE) {
    for ((sprite, pos) in font.glyphs(text)) draw(sprite, x + pos.first * scale, y + pos.second * scale, scale, color)
}

/** Text with a 1px outline, as the game draws it over busy backgrounds. */
fun Gfx.outlinedText(font: GameFont, text: String, x: Int, y: Int, scale: Int = 1, color: Int = WHITE, outline: Int = BLACK) {
    for ((dx, dy) in OUTLINE) text(font, text, x + dx * scale, y + dy * scale, scale, outline)
    text(font, text, x, y, scale, color)
}

private val OUTLINE = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)

fun GameFont.width(text: String, scale: Int = 1) = measure(text) * scale
