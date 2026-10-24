package app.isaaccompanion

import app.isaaccompanion.resources.Anm2
import app.isaaccompanion.resources.BitmapFont
import app.isaaccompanion.resources.StringTable
import app.isaaccompanion.resources.exists
import app.isaaccompanion.resources.fileResourceSource
import app.isaaccompanion.resources.normalizePath
import app.isaaccompanion.resources.readText
import app.isaaccompanion.resources.resolveRelative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

class ResourcesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pathsAreNormalisedAndResolved() {
        assertEquals("gfx/ui/a.png", normalizePath("GFX\\UI/./a.png"))
        assertEquals("gfx/b.png", normalizePath("gfx/ui/../b.png"))
        assertEquals("gfx/ui/minimap1.png", resolveRelative("gfx/ui/Minimap1.anm2", "minimap1.png"))
        assertEquals("gfx/shared.png", resolveRelative("gfx/ui/x.anm2", "../shared.png"))
    }

    @Test
    fun fileSourceIsCaseInsensitive() {
        val dir = tmp.newFolder("res")
        java.io.File(dir, "Gfx/UI").mkdirs()
        java.io.File(dir, "Gfx/UI/Hello.TXT").writeText("hi")
        val src = fileResourceSource(dir)
        assertEquals("hi", src.readText("gfx/ui/hello.txt"))
        assertEquals("hi", src.readText("GFX/ui/HELLO.txt"))
        assertNull(src.open("gfx/ui/missing.txt"))
        assertNull(src.open("gfx"))
        assertTrue(src.exists("gfx/ui/hello.txt"))
        assertFalse(src.exists("gfx/nope"))
        assertEquals(listOf("hello.txt"), src.list("gfx/ui"))
    }

    private val anm2 = """<?xml version="1.0" encoding="UTF-8"?>
<AnimatedActor>
  <Info CreatedBy="x" Fps="30" Version="1"/>
  <Content>
    <Spritesheets><Spritesheet Path="gfx/ui/minimap1.png" Id="0"/></Spritesheets>
    <Layers><Layer Name="Room" Id="0" SpritesheetId="0"/><Layer Name="Icon" Id="1" SpritesheetId="0"/></Layers>
    <Nulls/><Events/>
  </Content>
  <Animations DefaultAnimation="Rooms">
    <Animation Name="Rooms" FrameNum="3" Loop="false">
      <RootAnimation/>
      <LayerAnimations>
        <LayerAnimation LayerId="0" Visible="true">
          <Frame XPosition="0" YPosition="0" XPivot="0" YPivot="0" XCrop="16" YCrop="0" Width="16" Height="16" XScale="100" YScale="100" Delay="2" Visible="true" Rotation="0"/>
          <Frame XPosition="1" YPosition="2" XPivot="3" YPivot="4" XCrop="32" YCrop="0" Width="8" Height="9" XScale="200" YScale="50" Delay="1" Visible="false" Rotation="90" AlphaTint="128"/>
        </LayerAnimation>
      </LayerAnimations>
    </Animation>
  </Animations>
</AnimatedActor>"""

    @Test
    fun anm2IsParsed() {
        val a = Anm2.parse(anm2.byteInputStream())!!
        assertEquals("gfx/ui/minimap1.png", a.spritesheets[0])
        assertEquals(listOf("Room", "Icon"), a.layers.map { it.name })
        val anim = a.animation(null)!!
        assertEquals("Rooms", anim.name)
        assertEquals(3, anim.frameNum)
        val layer = anim.layers.single()
        assertEquals(2, layer.frames.size)
        val f0 = layer.frameAt(0)!!
        assertEquals(16, f0.xCrop)
        assertEquals(16, f0.width)
        assertEquals(f0, layer.frameAt(1)) // delay 2 covers times 0 and 1
        val f1 = layer.frameAt(2)!!
        assertEquals(32, f1.xCrop)
        assertEquals(200f, f1.xScale)
        assertFalse(f1.visible)
        assertEquals(128, f1.alphaTint)
        assertEquals(f1, layer.frameAt(99)) // clamped to the last frame
    }

    @Test
    fun anm2GarbageGivesNull() {
        assertNull(Anm2.parse("not xml".byteInputStream()))
        assertNull(Anm2.parse("""<!DOCTYPE x [<!ENTITY a "b">]><x/>""".byteInputStream()))
    }

    private val fntText = """info face="Test" size=8 bold=0
common lineHeight=10 base=8 scaleW=64 scaleH=64 pages=1
page id=0 file="test.png"
chars count=3
char id=65 x=0 y=0 width=5 height=7 xoffset=0 yoffset=1 xadvance=6 page=0 chnl=15
char id=66 x=6 y=0 width=5 height=7 xoffset=1 yoffset=1 xadvance=6 page=0 chnl=15
char id=63 x=12 y=0 width=5 height=7 xoffset=0 yoffset=1 xadvance=6 page=0 chnl=15
kerning first=65 second=66 amount=-1
"""

    @Test
    fun bitmapFontTextFormat() {
        val f = BitmapFont.parse(fntText.byteInputStream())!!
        assertEquals(10, f.lineHeight)
        assertEquals("test.png", f.pageFiles[0])
        assertEquals(11, f.measure("AB")) // 6 - 1 kerning + 6
        val placed = f.layout("AB")
        assertEquals(2, placed.size)
        assertEquals(0, placed[0].x)
        assertEquals(5 + 1, placed[1].x) // advance 6, kerning -1, xoffset 1
        assertEquals('?'.code, f.glyph('Z'.code)!!.id) // unknown glyph falls back to '?'
    }

    @Test
    fun bitmapFontXmlFormat() {
        val xml = """<font><common lineHeight="9" base="7"/><pages><page id="0" file="x.png"/></pages>
            <chars count="1"><char id="97" x="1" y="2" width="3" height="4" xoffset="0" yoffset="0" xadvance="4" page="0"/></chars></font>"""
        val f = BitmapFont.parse(xml.byteInputStream())!!
        assertEquals(9, f.lineHeight)
        assertEquals(4, f.measure("a"))
    }

    @Test
    fun bitmapFontGarbage() {
        assertNull(BitmapFont.parse("nothing here".byteInputStream()))
    }

    private val sta = """<stringtable>
  <languages><language name="English" code="en"/><language name="French" code="fr"/></languages>
  <category name="Items">
    <key name="#SAD_ONION_NAME"><string>The Sad Onion</string><string>L'Oignon Triste</string></key>
    <key name="#SAD_ONION_DESC"><string>Tears up</string><string></string></key>
  </category>
</stringtable>"""

    @Test
    fun stringTableResolvesByLanguageWithFallback() {
        val fr = StringTable.parse(sta.byteInputStream(), Locale.FRENCH)
        assertEquals("L'Oignon Triste", fr.resolve("#SAD_ONION_NAME"))
        assertEquals("Tears up", fr.resolve("#SAD_ONION_DESC")) // empty French string: English fallback
        val de = StringTable.parse(sta.byteInputStream(), Locale.GERMAN)
        assertEquals("The Sad Onion", de.resolve("#SAD_ONION_NAME"))
        assertEquals("Plain name", de.resolve("Plain name"))
        assertNull(de.resolve("#UNKNOWN"))
        assertNotNull(StringTable.parse("broken".byteInputStream()))
    }
}
