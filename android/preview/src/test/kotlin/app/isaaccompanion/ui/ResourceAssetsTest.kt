package app.isaaccompanion.ui

import app.isaaccompanion.net.ConnectionStatus
import app.isaaccompanion.protocol.ActiveSlot
import app.isaaccompanion.protocol.GameState
import app.isaaccompanion.protocol.ItemCount
import app.isaaccompanion.protocol.ItemInfo
import app.isaaccompanion.protocol.ItemKey
import app.isaaccompanion.protocol.Meta
import app.isaaccompanion.protocol.Player
import app.isaaccompanion.resources.fileResourceSource
import app.isaaccompanion.resources.locateResourceRoot
import app.isaaccompanion.ui.gfx.ArgbImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/** Synthetic resource tree (nothing from the game): icons are coloured squares, the font is a solid block font. */
class ResourceAssetsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun decode(input: InputStream): ArgbImage? {
        val img = ImageIO.read(input) ?: return null
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return ArgbImage(img.width, img.height, px)
    }

    private fun png(file: File, w: Int, h: Int, color: Int) {
        file.parentFile.mkdirs()
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, if (x == 0 || y == 0 || x == w - 1 || y == h - 1) 0xFF000000.toInt() else color)
        ImageIO.write(img, "png", file)
    }

    private fun tree(): File {
        val root = tmp.newFolder("extracted_resources")
        val res = File(root, "resources")
        png(File(res, "gfx/items/collectibles/Collectibles_001_TheSadOnion.PNG"), 32, 32, 0xFFE8C23A.toInt())
        png(File(res, "gfx/items/trinkets/trinket_001.png"), 32, 32, 0xFF6B4423.toInt())
        png(File(res, "font/block.png"), 8, 8, 0xFFFFFFFF.toInt())
        File(res, "font").mkdirs()
        File(res, "font/pftempestasevencondensed.fnt").writeText(
            buildString {
                appendLine("info face=\"block\" size=8")
                appendLine("common lineHeight=9 base=7 scaleW=8 scaleH=8 pages=1")
                appendLine("page id=0 file=\"block.png\"")
                for (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?- :.+/'x,!") {
                    appendLine("char id=${c.code} x=0 y=0 width=5 height=7 xoffset=0 yoffset=0 xadvance=6 page=0 chnl=15")
                    if (c.isLetter()) appendLine("char id=${c.lowercaseChar().code} x=0 y=0 width=5 height=7 xoffset=0 yoffset=0 xadvance=6 page=0 chnl=15")
                }
            },
        )
        return root
    }

    @Test
    fun locatesResourcesInsideTheExtractorFolder() {
        val root = tree()
        assertNotNull(locateResourceRoot(fileResourceSource(root)))
        assertNotNull(locateResourceRoot(fileResourceSource(File(root, "resources"))))
        assertNull(locateResourceRoot(fileResourceSource(tmp.newFolder("empty"))))
    }

    @Test
    fun loadsFontAndIconsCaseInsensitively() {
        val src = locateResourceRoot(fileResourceSource(tree()))!!
        val assets = ResourceAssets.load(src, ::decode)
        assertTrue(assets.hasGameResources)
        assertEquals(12, assets.font.measure("AB"))
        val onion = ItemInfo("c", 1, "The Sad Onion", gfx = "gfx/items/collectibles/collectibles_001_thesadonion.png")
        val missing = ItemInfo("c", 2, gfx = "gfx/items/collectibles/nope.png")
        assertNull(assets.itemIcon(onion)) // not loaded before prefetch: drawing never touches storage
        assertTrue(assets.prefetch(listOf(onion, missing)))
        assertNotNull(assets.itemIcon(onion))
        assertNull(assets.itemIcon(missing))
        assertTrue(!assets.prefetch(listOf(onion, missing))) // nothing new the second time
    }

    @Test
    fun screenUsesLoadedIconsAndFont() {
        val src = locateResourceRoot(fileResourceSource(tree()))!!
        val assets = ResourceAssets.load(src, ::decode)
        val infos = listOf(
            ItemInfo("c", 1, "The Sad Onion", "Tears up", "gfx/items/collectibles/collectibles_001_thesadonion.png"),
            ItemInfo("t", 1, "Swallowed Penny", gfx = "gfx/items/trinkets/trinket_001.png"),
        )
        assets.prefetch(infos)
        val state = GameState(
            Meta(1, 0, "Basement I", 0, "AAAA 0000"),
            players = listOf(
                Player(
                    name = "Isaac", coins = 3, active = ActiveSlot(),
                    trinkets = listOf(1), items = listOf(ItemCount(1)), pickupOrder = listOf(1),
                ),
            ),
        )
        val g = AwtGfx(310, 270, 4)
        val result = drawScreen(
            g, 310, 270,
            ScreenInput(ConnectionStatus.CONNECTED, state, infos.associateBy { it.key }, assets, selected = ItemKey("c", 1)),
        )
        File(System.getProperty("screens.dir") ?: "build/screens").mkdirs()
        ImageIO.write(g.image, "png", File(System.getProperty("screens.dir") ?: "build/screens", "resource_assets.png"))
        assertTrue(result.hits.any { it.action is HitAction.Item })
        // the icon colour must have reached the framebuffer
        var found = false
        for (y in 0 until g.image.height) for (x in 0 until g.image.width) if (g.image.getRGB(x, y) == 0xFFE8C23A.toInt()) found = true
        assertTrue(found)
    }
}
