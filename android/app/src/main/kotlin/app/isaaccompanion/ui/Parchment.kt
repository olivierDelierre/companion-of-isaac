package app.isaaccompanion.ui

import app.isaaccompanion.ui.gfx.ArgbImage
import app.isaaccompanion.ui.gfx.argb
import kotlin.random.Random

/**
 * Procedural parchment with burnt edges, pre-rendered once per size. Original art, standing in for
 * the game's paper texture until one is mapped from the user's resources.
 */
object Parchment {
    private val cache = HashMap<Long, ArgbImage>()

    fun render(w: Int, h: Int): ArgbImage = synchronized(cache) {
        cache.getOrPut(w.toLong() shl 32 or h.toLong()) { build(w, h) }
    }

    private fun build(w: Int, h: Int): ArgbImage {
        val rnd = Random(7)
        val px = IntArray(w * h)
        for (i in px.indices) {
            val r = rnd.nextFloat()
            px[i] = when {
                r < 0.07f -> argb(255, 0xc9, 0xb2, 0x86)
                r < 0.14f -> argb(255, 0xe6, 0xd6, 0xb2)
                else -> argb(255, 0xd9, 0xc7, 0xa0)
            }
        }
        fun burn(x: Int, y: Int, depth: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            px[y * w + x] = when (depth) {
                0 -> argb(255, 0, 0, 0)
                1 -> argb(255, 0x3a, 0x24, 0x14)
                else -> argb(255, 0x8a, 0x6a, 0x40)
            }
        }
        for (x in 0 until w) {
            val top = 1 + rnd.nextInt(3)
            val bottom = 1 + rnd.nextInt(3)
            for (d in 0 until top + 2) burn(x, d, if (d < top) 0 else d - top + 1)
            for (d in 0 until bottom + 2) burn(x, h - 1 - d, if (d < bottom) 0 else d - bottom + 1)
        }
        for (y in 0 until h) {
            val left = 1 + rnd.nextInt(3)
            val right = 1 + rnd.nextInt(3)
            for (d in 0 until left + 2) burn(d, y, if (d < left) 0 else d - left + 1)
            for (d in 0 until right + 2) burn(w - 1 - d, y, if (d < right) 0 else d - right + 1)
        }
        return ArgbImage(w, h, px)
    }
}
