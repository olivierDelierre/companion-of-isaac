package app.isaaccompanion.ui

import app.isaaccompanion.ui.gfx.GameFont

/** Greedy word wrap on measured width; words longer than the line are cut. */
fun wrapText(font: GameFont, text: String, maxWidth: Int): List<String> {
    val out = ArrayList<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            var w = word
            while (font.measure(w) > maxWidth && w.length > 1) {
                var cut = w.length - 1
                while (cut > 1 && font.measure(w.substring(0, cut)) > maxWidth) cut--
                if (line.isNotEmpty()) { out += line; line = "" }
                out += w.substring(0, cut)
                w = w.substring(cut)
            }
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (font.measure(candidate) <= maxWidth || line.isEmpty()) line = candidate else { out += line; line = w }
        }
        out += line
    }
    while (out.size > 1 && out.last().isEmpty()) out.removeAt(out.size - 1)
    return out
}

/**
 * Turns External Item Descriptions markup into plain lines: `#` separates lines, `{{Icon}}` and
 * colour markers are dropped (icons have no counterpart in the companion font), `↑`/`↓` become +/-.
 */
fun eidToLines(markup: String): List<String> =
    markup.replace("↑", "+").replace("↓", "-")
        .replace(Regex("\\{\\{[^}]*}}"), "")
        .replace(Regex("[ \\t]+"), " ")
        .split('#')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
