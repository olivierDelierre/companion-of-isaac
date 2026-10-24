package app.isaaccompanion.ui.art

import app.isaaccompanion.resources.BitmapFont
import app.isaaccompanion.ui.gfx.ArgbImage
import app.isaaccompanion.ui.gfx.BitmapGameFont
import app.isaaccompanion.ui.gfx.GameFont
import app.isaaccompanion.ui.gfx.Sprite
import app.isaaccompanion.ui.gfx.rgb

/**
 * Original placeholder art drawn for this project (nothing is taken from the game). It is what the
 * UI shows for anything the user's extracted resources do not provide.
 */
object BuiltinArt {
    private val PALETTE: Map<Char, Int> = mapOf(
        'k' to rgb(0x0d0b0b), 'w' to rgb(0xf2efe8), 'y' to rgb(0xe8c23a), 'Y' to rgb(0xfff1a0), 'o' to rgb(0xb77a1e),
        'g' to rgb(0x9a9a9a), 'G' to rgb(0x4c4c4c), 'r' to rgb(0xd63a2f), 'R' to rgb(0x7d1712), 'b' to rgb(0x3d6fd6),
        'B' to rgb(0x8fc4ff), 'n' to rgb(0x6b4423), 'p' to rgb(0xd77ad6), 'v' to rgb(0x7a3db8), 'e' to rgb(0x3fae49),
    )

    fun fromRows(vararg rows: String, palette: Map<Char, Int> = PALETTE): Sprite {
        val w = rows.maxOf { it.length }
        val h = rows.size
        val px = IntArray(w * h)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c != '.') px[y * w + x] = palette[c] ?: 0 }
        }
        return Sprite(ArgbImage(w, h, px))
    }

    /** Same shape, other colours: `swap` maps palette letters to other palette letters. */
    fun recolor(rows: Array<String>, swap: Map<Char, Char>) = fromRows(*rows.map { r -> r.map { swap[it] ?: it }.joinToString("") }.toTypedArray())

    val coin by lazy {
        fromRows("..kkkk..", ".kyyyyk.", "kyYYyyok", "kyYyyyok", "kyyyyyok", "kyyyyook", ".kooook.", "..kkkk..")
    }
    val bomb by lazy {
        fromRows(".....yk.", "....k...", "..kkkk..", ".kGGGGk.", "kGgwGGGk", "kGgGGGGk", "kGGGGGGk", ".kkkkkk.")
    }
    val key by lazy {
        fromRows(".kkk....", "kyYyk...", "kykyk...", "kyyyk...", ".kyk....", ".kyykk..", ".kyk....", ".kyyk...")
    }
    val card by lazy {
        fromRows(".kkkkkkk.", ".kwwwwwk.", ".kwrrrwk.", ".kwrwrwk.", ".kwrrrwk.", ".kwwwwwk.", ".kkkkkkk.")
    }
    val pill by lazy {
        fromRows("............", ".......kkk..", "......kwwwk.", ".....kwwwwk.", "....kwwwwk..", "...krrwwk...", "..krrrrk....", ".krrrrk.....", ".krrrk......", "..kkk.......")
    }
    val trinket by lazy {
        fromRows("..kkkk..", ".knnnnk.", "knnwnnnk", "knnnnnnk", "knnnnknk", ".knnnnk.", "..kkkk..")
    }
    val activeBook by lazy {
        fromRows(".kkkkkkkkk..", ".kRRRRRRRkk.", ".kRrrrrrRkwk", ".kRrkkkrRkwk", ".kRrkyyrRkwk", ".kRrkkkrRkwk", ".kRrrrrrRkwk", ".kRRRRRRRkwk", ".kkkkkkkkkwk", "..kwwwwwwwwk", "..kkkkkkkkkk")
    }
    val heart by lazy {
        fromRows("............", "..kkk..kkk..", ".kRrrkkrrrk.", "kRrwrrrrrrRk", "kRrrrrrrrrRk", "kRrrrrrrrrRk", ".kRrrrrrrRk.", "..kRrrrrRk..", "...kRrrRk...", "....kRRk....", ".....kk.....")
    }

    // Minimap room-type icons, 7 px wide (the UI keys them by RoomType).
    private val ICONS: Map<Int, Sprite> by lazy {
        val boss = fromRows(".kkkkk.", "kwwwwwk", "kwkwkwk", "kwwwwwk", ".kwkwk.", ".kkkkk.")
        val treasure = fromRows("k..k..k", "kykykyk", "kyyyyyk", "kyYyYyk", "kyyyyyk", "kkkkkkk")
        val shop = fromRows("..kkk..", ".kyyyk.", "kykkkk.", ".kyyyk.", ".kkkkyk", ".kyyyk.", "..kkk..")
        val secret = fromRows(".kkkkk.", "kwwwwwk", "kwkkkwk", "kkkkwwk", "..kwwk.", "..kkkk.", "..kwwk.", "..kkkk..")
        val curse = fromRows("k.....k", ".kkkkk.", "kGwGwGk", "kGGGGGk", ".kGkGk.", "..kkk..")
        val mini = fromRows(".kkkkk.", "kwwwwwk", "kwkwkwk", "kwwwwwk", ".kkkkk.")
        val sacrifice = fromRows("...k...", "..kwk..", ".kwwwk.", "kkkwkkk", "..kwk..", "..kwk..", "..kkk..")
        val devil = fromRows("k.....k", "kr...rk", ".krrrk.", ".krkrk.", ".krrrk.", "..kkk..")
        val angel = fromRows("..kkk..", ".kyyyk.", "..kkk..", ".kwwwk.", "kwwkwwk", ".kk.kk.")
        val library = fromRows("kkkkkkk", "kbbwrrk", "kbbwrrk", "kbbwrrk", "kkkkkkk")
        val challenge = fromRows("k.....k", "kk...kk", ".kkkkk.", ".kkrkk.", ".kkkkk.", "kk...kk")
        val arcade = fromRows(".kkkkk.", "kbbbbbk", "kbwbbbk", "kbbbrbk", "kbbbbbk", ".kkkkk.")
        val unknown = fromRows(".kkkkk.", "kwwwwwk", "kkkkkwk", "..kkwwk", "..kwwk.", "..kkk..", "..kwwk.", "..kkk..")
        mapOf(
            5 to boss, 6 to mini, 4 to treasure, 2 to shop, 7 to secret, 8 to secret, 10 to curse, 13 to sacrifice,
            14 to devil, 15 to angel, 12 to library, 11 to challenge, 9 to arcade, 29 to secret, -1 to unknown,
        )
    }

    fun roomIcon(type: Int): Sprite? = ICONS[type]

    val lostMark by lazy { fromRows(".kkkk.", "kwkkwk", "kwwwwk", ".kwwk.", "kwkkwk") }

    // ---- 3x5 pixel font as a regular BitmapFont so text goes through one code path ----

    private val GLYPHS: Map<Char, String> = mapOf(
        'A' to "010101111101101", 'B' to "110101110101110", 'C' to "011100100100011", 'D' to "110101101101110",
        'E' to "111100110100111", 'F' to "111100110100100", 'G' to "011100101101011", 'H' to "101101111101101",
        'I' to "111010010010111", 'J' to "001001001101010", 'K' to "101101110101101", 'L' to "100100100100111",
        'M' to "101111111101101", 'N' to "110101101101101", 'O' to "010101101101010", 'P' to "110101110100100",
        'Q' to "010101101110011", 'R' to "110101110101101", 'S' to "011100010001110", 'T' to "111010010010010",
        'U' to "101101101101111", 'V' to "101101101101010", 'W' to "101111111111101", 'X' to "101101010101101",
        'Y' to "101101010010010", 'Z' to "111001010100111",
        '0' to "111101101101111", '1' to "010110010010111", '2' to "110001010100111", '3' to "110001010001110",
        '4' to "101101111001001", '5' to "111100110001110", '6' to "011100111101111", '7' to "111001010010010",
        '8' to "111101111101111", '9' to "111101111001110",
        ':' to "000010000010000", '-' to "000000111000000", '.' to "000000000000010", '/' to "001001010100100",
        '\'' to "010010000000000", '+' to "000010111010000", '?' to "110001010000010", '#' to "101111101111101",
        ',' to "000000000010100", '!' to "010010010000010", '(' to "001010010010001", ')' to "100010010010100",
        '%' to "101001010100101", '=' to "000111000111000", ' ' to "000000000000000",
    )

    private const val CELL_W = 4
    private const val CELL_H = 6

    val font: GameFont by lazy {
        val chars = GLYPHS.keys.sorted()
        val atlasW = chars.size * CELL_W
        val px = IntArray(atlasW * CELL_H)
        val defs = StringBuilder("common lineHeight=7 base=5\npage id=0 file=\"builtin\"\n")
        chars.forEachIndexed { i, c ->
            val bits = GLYPHS.getValue(c)
            for (n in 0 until 15) if (bits[n] == '1') px[(n / 3) * atlasW + i * CELL_W + n % 3] = rgb(0xFFFFFF)
            // lowercase maps to the same glyphs below
            defs.append("char id=${c.code} x=${i * CELL_W} y=0 width=3 height=5 xoffset=0 yoffset=0 xadvance=4 page=0\n")
            if (c in 'A'..'Z') {
                defs.append("char id=${c.lowercaseChar().code} x=${i * CELL_W} y=0 width=3 height=5 xoffset=0 yoffset=0 xadvance=4 page=0\n")
            }
        }
        val bm = BitmapFont.parse(defs.toString().byteInputStream())!!
        BitmapGameFont(bm, mapOf(0 to ArgbImage(atlasW, CELL_H, px)))
    }
}
