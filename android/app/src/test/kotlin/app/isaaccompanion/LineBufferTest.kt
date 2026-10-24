package app.isaaccompanion

import app.isaaccompanion.net.LineBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class LineBufferTest {
    private fun feed(b: LineBuffer, s: String, out: MutableList<String>) {
        val bytes = s.toByteArray()
        b.feed(bytes, bytes.size) { out += it }
    }

    @Test
    fun joinsPartialLinesAcrossReads() {
        val b = LineBuffer()
        val out = mutableListOf<String>()
        feed(b, "{\"a\":", out)
        assertEquals(0, out.size)
        feed(b, "1}\n{\"b\":2}\n{\"c\"", out)
        feed(b, ":3}\n", out)
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}", "{\"c\":3}"), out)
    }

    @Test
    fun keepsMultiByteCharactersSplitAcrossReads() {
        val b = LineBuffer()
        val out = mutableListOf<String>()
        val bytes = "é\n".toByteArray()
        b.feed(bytes, 1) { out += it }
        b.feed(bytes.copyOfRange(1, bytes.size), bytes.size - 1) { out += it }
        assertEquals(listOf("é"), out)
    }

    @Test
    fun dropsOversizedLinesAndRecovers() {
        val b = LineBuffer(maxLine = 8)
        val out = mutableListOf<String>()
        feed(b, "0123456789012345\nok\n", out)
        assertEquals(listOf("ok"), out)
    }
}
