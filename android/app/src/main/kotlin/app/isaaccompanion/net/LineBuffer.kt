package app.isaaccompanion.net

import java.io.ByteArrayOutputStream

/**
 * Splits a byte stream into lines. Partial lines survive between [feed] calls (a socket read
 * timeout never loses data), and a line longer than [maxLine] is discarded instead of growing forever.
 */
class LineBuffer(private val maxLine: Int = 4 * 1024 * 1024) {
    private val current = ByteArrayOutputStream()
    private var overflow = false

    fun feed(data: ByteArray, length: Int, onLine: (String) -> Unit) {
        var start = 0
        for (i in 0 until length) {
            if (data[i] == NEWLINE) {
                append(data, start, i - start)
                if (!overflow) onLine(current.toString(Charsets.UTF_8.name()))
                current.reset()
                overflow = false
                start = i + 1
            }
        }
        append(data, start, length - start)
    }

    private fun append(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0 || overflow) return
        if (current.size() + count > maxLine) {
            overflow = true
            current.reset()
            return
        }
        current.write(data, offset, count)
    }

    private companion object {
        const val NEWLINE = '\n'.code.toByte()
    }
}
