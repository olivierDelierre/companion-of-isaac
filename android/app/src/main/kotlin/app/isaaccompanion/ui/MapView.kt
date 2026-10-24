package app.isaaccompanion.ui

import app.isaaccompanion.protocol.MapState
import app.isaaccompanion.protocol.Room
import app.isaaccompanion.ui.gfx.Gfx
import app.isaaccompanion.ui.gfx.IRect
import app.isaaccompanion.ui.gfx.argb
import app.isaaccompanion.ui.gfx.fillRect

const val GRID = 13
private const val DISPLAY_ICON = 4
private const val DISPLAY_VISIBLE = 1

/**
 * Cells a room occupies relative to its `gridIndex`, which is the top-left cell of its bounding box.
 * One place to fix if the game turns out to number L-shaped rooms differently.
 */
val ROOM_SHAPE_CELLS: Map<Int, List<Pair<Int, Int>>> = mapOf(
    1 to listOf(0 to 0), // 1x1
    2 to listOf(0 to 0), // IH
    3 to listOf(0 to 0), // IV
    4 to listOf(0 to 0, 0 to 1), // 1x2
    5 to listOf(0 to 0, 0 to 1), // IIV
    6 to listOf(0 to 0, 1 to 0), // 2x1
    7 to listOf(0 to 0, 1 to 0), // IIH
    8 to listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), // 2x2
    9 to listOf(1 to 0, 0 to 1, 1 to 1), // LTL
    10 to listOf(0 to 0, 0 to 1, 1 to 1), // LTR
    11 to listOf(0 to 0, 1 to 0, 1 to 1), // LBL
    12 to listOf(0 to 0, 1 to 0, 0 to 1), // LBR
)

/** Absolute grid cells (x, y) of [room]; empty when its index is outside the 13x13 grid. */
fun roomCells(room: Room): List<Pair<Int, Int>> {
    if (room.gridIndex < 0 || room.gridIndex >= GRID * GRID) return emptyList()
    val x0 = room.gridIndex % GRID
    val y0 = room.gridIndex / GRID
    return (ROOM_SHAPE_CELLS[room.shape] ?: ROOM_SHAPE_CELLS.getValue(1))
        .map { (dx, dy) -> x0 + dx to y0 + dy }
        .filter { (x, y) -> x in 0 until GRID && y in 0 until GRID }
}

/** Where the rooms of a map land inside an area: bounding box of the known rooms, uniform cell size. */
class MapGeometry(rooms: List<Room>, private val area: IRect) {
    private val minX: Int
    private val minY: Int
    val cols: Int
    val rows: Int
    val cw: Int
    val ch: Int
    private val originX: Int
    private val originY: Int

    init {
        val cells = rooms.flatMap { roomCells(it) }
        if (cells.isEmpty()) {
            minX = 0; minY = 0; cols = 0; rows = 0; cw = 0; ch = 0; originX = area.x; originY = area.y
        } else {
            minX = cells.minOf { it.first }
            minY = cells.minOf { it.second }
            cols = cells.maxOf { it.first } - minX + 1
            rows = cells.maxOf { it.second } - minY + 1
            // room cells are a bit wider than tall, like the game's minimap
            val byHeight = area.h / rows
            val byWidth = (area.w * 100 / 115) / cols
            ch = minOf(byHeight, byWidth).coerceIn(5, 24)
            cw = ch * 115 / 100
            originX = area.x + (area.w - cols * cw) / 2
            originY = area.y + (area.h - rows * ch) / 2
        }
    }

    fun cellRect(x: Int, y: Int) = IRect(originX + (x - minX) * cw, originY + (y - minY) * ch, cw, ch)
}

private val VISITED = argb(255, 0x8a, 0x8a, 0x8a)
private val VISITED_EDGE = argb(255, 0xa8, 0xa8, 0xa8)
private val SEEN = argb(255, 0x3f, 0x3f, 0x3f)
private val SHADOW = argb(255, 0x26, 0x26, 0x26)
private val CURRENT = argb(255, 0xf4, 0xf4, 0xf4)
private val PANEL = argb(140, 0, 0, 0)
private val OUTLINE = argb(255, 0, 0, 0)

/** Draws the minimap in [area]. Never draws more than the mod sent, which is never more than the game's own map. */
fun drawMap(g: Gfx, assets: GameAssets, map: MapState, area: IRect) {
    val rooms = map.rooms.filter { roomCells(it).isNotEmpty() }
    if (rooms.isEmpty()) return
    val geo = MapGeometry(rooms, area)
    g.fillRect(area, PANEL)

    // outline pass first so neighbouring rooms keep a visible black gap
    for (room in rooms) for ((x, y) in roomCells(room)) g.fillRect(geo.cellRect(x, y), OUTLINE)

    for (room in rooms) {
        val cells = roomCells(room).toSet()
        val color = when {
            room.current -> CURRENT
            room.visited -> VISITED
            room.displayFlags and DISPLAY_VISIBLE != 0 || room.displayFlags and DISPLAY_ICON != 0 -> SEEN
            else -> SHADOW
        }
        for ((x, y) in cells) {
            val r = geo.cellRect(x, y)
            val left = (x - 1 to y) in cells
            val right = (x + 1 to y) in cells
            val top = (x to y - 1) in cells
            val bottom = (x to y + 1) in cells
            val ix = r.x + if (left) 0 else 2
            val iy = r.y + if (top) 0 else 2
            val iw = r.w - (if (left) 0 else 2) - (if (right) 0 else 2)
            val ih = r.h - (if (top) 0 else 2) - (if (bottom) 0 else 2)
            g.fillRect(ix, iy, iw, ih, color)
            if (room.visited && !room.current && !top) g.fillRect(ix, iy, iw, 1, VISITED_EDGE)
        }
        // icon on the first cell, only when the mod says the game would show it
        val icon = if (room.type > 1) assets.roomIcon(room.type) else null
        if (icon != null) {
            val (x, y) = roomCells(room).first()
            val r = geo.cellRect(x, y)
            val scale = (minOf(r.w - 4, r.h - 4) / maxOf(icon.sw, icon.sh)).coerceIn(1, 2)
            g.draw(icon, r.x + (r.w - icon.sw * scale) / 2, r.y + (r.h - icon.sh * scale) / 2, scale)
        }
    }
}
