package app.isaaccompanion.ui

import app.isaaccompanion.protocol.ItemInfo
import app.isaaccompanion.resources.BitmapFont
import app.isaaccompanion.resources.ResourceSource
import app.isaaccompanion.resources.resolveRelative
import app.isaaccompanion.ui.art.BuiltinArt
import app.isaaccompanion.ui.gfx.ArgbImage
import app.isaaccompanion.ui.gfx.BitmapGameFont
import app.isaaccompanion.ui.gfx.GameFont
import app.isaaccompanion.ui.gfx.Sprite
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Everything the screen draws from. Anything missing from the user's resources falls back to built-in art. */
interface GameAssets {
    val font: GameFont

    /** True when the user's extracted game resources are in use. */
    val hasGameResources: Boolean

    val coin: Sprite
    val bomb: Sprite
    val key: Sprite
    val card: Sprite
    val pill: Sprite
    val lostMark: Sprite

    /** The 32x32 icon of an item, or null when unavailable (the screen then draws an id tile). */
    fun itemIcon(info: ItemInfo?): Sprite?

    fun roomIcon(type: Int): Sprite?
}

object BuiltinAssets : GameAssets {
    override val font: GameFont get() = BuiltinArt.font
    override val hasGameResources = false
    override val coin get() = BuiltinArt.coin
    override val bomb get() = BuiltinArt.bomb
    override val key get() = BuiltinArt.key
    override val card get() = BuiltinArt.card
    override val pill get() = BuiltinArt.pill
    override val lostMark get() = BuiltinArt.lostMark
    override fun itemIcon(info: ItemInfo?): Sprite? = null
    override fun roomIcon(type: Int) = BuiltinArt.roomIcon(type)
}

/**
 * Names of the extracted files this app reads. The exact names of UI sprites in the user's
 * extraction are not known yet: only things the mod reports itself (item icon paths) and fonts
 * (first candidate that exists and parses) are wired; the rest stays on built-in art.
 */
object ResourceMap {
    val FONT_CANDIDATES = listOf(
        "font/pftempestasevencondensed.fnt",
        "font/teammeatfont10.fnt",
        "font/teammeatfont12.fnt",
        "font/upheavalmini.fnt",
        "font/luamini.fnt",
    )
    const val STRING_TABLE = "stringtable.sta"
}

/**
 * Assets backed by the user's resources. Image decoding is injected (BitmapFactory on Android) and
 * item icons are loaded ahead of drawing with [prefetch], so drawing never touches storage.
 */
class ResourceAssets(
    private val source: ResourceSource,
    private val decode: (InputStream) -> ArgbImage?,
    override val font: GameFont,
    private val fallback: GameAssets = BuiltinAssets,
) : GameAssets by fallback {
    override val hasGameResources = true

    private val icons = ConcurrentHashMap<String, Sprite>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** Loads the icons of [infos]; returns true when something new became available. */
    fun prefetch(infos: Collection<ItemInfo>): Boolean {
        var changed = false
        for (info in infos) {
            val path = info.gfx ?: continue
            val key = path.lowercase()
            if (icons.containsKey(key) || key in missing) continue
            val image = source.open(path)?.use { decode(it) }
            if (image == null) missing += key else { icons[key] = Sprite(image); changed = true }
        }
        return changed
    }

    override fun itemIcon(info: ItemInfo?): Sprite? = info?.gfx?.let { icons[it.lowercase()] }

    companion object {
        /** Builds assets from [source]; the font falls back to the built-in one when none can be loaded. */
        fun load(source: ResourceSource, decode: (InputStream) -> ArgbImage?): ResourceAssets =
            ResourceAssets(source, decode, loadFont(source, decode) ?: BuiltinAssets.font)

        private fun loadFont(source: ResourceSource, decode: (InputStream) -> ArgbImage?): GameFont? {
            for (path in ResourceMap.FONT_CANDIDATES) {
                val bm = source.open(path)?.use { BitmapFont.parse(it) } ?: continue
                val pages = bm.pageFiles.mapNotNull { (id, file) ->
                    val img = source.open(resolveRelative(path, file))?.use { decode(it) } ?: return@mapNotNull null
                    id to img
                }.toMap()
                if (pages.size == bm.pageFiles.size && pages.isNotEmpty()) return BitmapGameFont(bm, pages)
            }
            return null
        }
    }
}
