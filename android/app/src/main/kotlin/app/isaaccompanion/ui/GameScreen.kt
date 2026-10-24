package app.isaaccompanion.ui

import app.isaaccompanion.net.ConnectionStatus
import app.isaaccompanion.protocol.ActiveSlot
import app.isaaccompanion.protocol.GameState
import app.isaaccompanion.protocol.ItemInfo
import app.isaaccompanion.protocol.ItemKey
import app.isaaccompanion.protocol.Player
import app.isaaccompanion.resources.StringTable
import app.isaaccompanion.ui.gfx.Gfx
import app.isaaccompanion.ui.gfx.IRect
import app.isaaccompanion.ui.gfx.Sprite
import app.isaaccompanion.ui.gfx.argb
import app.isaaccompanion.ui.gfx.fillRect
import app.isaaccompanion.ui.gfx.outlinedText
import app.isaaccompanion.ui.gfx.strokeRect
import app.isaaccompanion.ui.gfx.text
import app.isaaccompanion.ui.gfx.width

const val CURSE_OF_THE_LOST = 4
const val ICON = 32

sealed interface HitAction {
    data class Tab(val index: Int) : HitAction
    data class Item(val key: ItemKey) : HitAction
    data object ChooseResources : HitAction

    /** The scrollable passive grid (drag area). */
    data object Grid : HitAction
}

data class Hit(val rect: IRect, val action: HitAction)

data class ScreenInput(
    val status: ConnectionStatus,
    val state: GameState?,
    val items: Map<ItemKey, ItemInfo>,
    val assets: GameAssets,
    val names: StringTable = StringTable.EMPTY,
    val tab: Int = 0,
    val selected: ItemKey? = null,
    val scroll: Int = 0,
    val resourcesConfigured: Boolean = false,
)

class ScreenResult(val hits: List<Hit>, val maxScroll: Int)

private val WHITE_TEXT = argb(255, 255, 255, 255)
private val DIM_TEXT = argb(255, 0x88, 0x88, 0x88)
private val INK = argb(255, 0x2a, 0x1a, 0x10)
private val INK_SOFT = argb(255, 0x6e, 0x57, 0x3c)
private val BACKGROUND = argb(255, 0x0a, 0x09, 0x09)
private val TAB_ON_FRAME = argb(255, 0xf4, 0xf4, 0xf4)
private val TAB_ON = argb(255, 0x2b, 0x2b, 0x2b)
private val TAB_OFF = argb(255, 0x14, 0x14, 0x14)
private val TILE = argb(255, 0x1b, 0x18, 0x16)
private val TILE_EDGE = argb(255, 0x4a, 0x40, 0x38)
private val BUBBLE_FILL = argb(255, 0xef, 0xe2, 0xc4)

/** Draws the whole companion screen in a [w]x[h] logical area and reports what can be tapped. */
fun drawScreen(g: Gfx, w: Int, h: Int, input: ScreenInput): ScreenResult {
    g.fillRect(0, 0, w, h, BACKGROUND)
    val hits = ArrayList<Hit>()
    val assets = input.assets
    val font = assets.font
    val lh = font.lineHeight
    val m = 6

    val state = input.state
    drawHeader(g, input, state, w, m, hits)
    val bodyTop = m + lh * 2 + 6

    if (state == null) {
        drawWaiting(g, input, w, h, bodyTop, hits)
        return ScreenResult(hits, 0)
    }

    val players = state.players.filter { !it.isChild && !it.isCoopGhost }.ifEmpty { state.players }
    val tab = input.tab.coerceIn(0, (players.size - 1).coerceAtLeast(0))
    val player = players.getOrNull(tab)

    val available = h - bodyTop - m
    val topH = (available * 46 / 100).coerceAtLeast(96).coerceAtMost(available - 60)
    val mapArea = IRect(m, bodyTop, (w * 45 / 100), topH)
    if (state.map.rooms.isNotEmpty()) drawMap(g, assets, state.map, mapArea)
    val caption = when {
        state.map.mapHidden -> "CURSE OF THE LOST"
        state.map.currentRoom < 0 && state.map.rooms.isNotEmpty() -> "OUTSIDE THE MAP"
        else -> null
    }
    if (caption != null) g.outlinedText(font, caption, mapArea.x + 2, mapArea.bottom - lh - 1, 1, DIM_TEXT)

    val rx = mapArea.right + 8
    var y = bodyTop
    if (players.size > 1) {
        var tx = rx
        players.forEachIndexed { i, p ->
            val label = "P${i + 1} ${p.name}".uppercase()
            val tw = font.width(label) + 8
            val r = IRect(tx, y, tw, lh + 4)
            val on = i == tab
            g.fillRect(r, if (on) TAB_ON_FRAME else argb(255, 0, 0, 0))
            g.fillRect(r.inset(1), if (on) TAB_ON else TAB_OFF)
            g.text(font, label, r.x + 4, r.y + 2, 1, if (on) WHITE_TEXT else DIM_TEXT)
            hits += Hit(r, HitAction.Tab(i))
            tx += tw + 3
        }
        y += lh + 10
    }
    if (player != null) drawHud(g, input, player, rx, y, w - m - rx, hits)

    val panel = IRect(m - 2, mapArea.bottom + 4, w - 2 * (m - 2), h - (mapArea.bottom + 4) - (m - 2))
    val maxScroll = if (player != null) drawPassives(g, input, player, panel, hits) else 0

    input.selected?.let { drawBubble(g, input, it, player, panel, w, h, m) }
    return ScreenResult(hits, maxScroll)
}

private fun drawHeader(g: Gfx, input: ScreenInput, state: GameState?, w: Int, m: Int, hits: MutableList<Hit>) {
    val font = input.assets.font
    val title = (state?.meta?.levelName ?: "ISAAC COMPANION").uppercase()
    g.outlinedText(font, title, m, m, 2, WHITE_TEXT)
    var x = m + font.width(title, 2) + 6
    if (state != null && state.meta.curses and CURSE_OF_THE_LOST != 0) {
        g.draw(input.assets.lostMark, x, m + font.lineHeight - input.assets.lostMark.sh, 2)
        x += input.assets.lostMark.sw * 2 + 4
    }
    val seed = state?.meta?.seed.orEmpty().uppercase()
    val seedW = font.width(seed)
    val dot = when (input.status) {
        ConnectionStatus.CONNECTED -> argb(255, 0x46, 0xc9, 0x4f)
        ConnectionStatus.WAITING_FOR_GAME -> argb(255, 0xe7, 0xd3, 0x3c)
        ConnectionStatus.NO_RUN -> argb(255, 0x6f, 0x8f, 0xd0)
        ConnectionStatus.GAME_CLOSED -> argb(255, 0xd6, 0x3a, 0x2f)
    }
    val dotX = w - m - 5 - if (seed.isEmpty()) 0 else seedW + 4
    g.fillRect(dotX, m + 2, 5, 5, argb(255, 0, 0, 0))
    g.fillRect(dotX + 1, m + 3, 3, 3, dot)
    if (seed.isNotEmpty()) g.text(font, seed, w - m - seedW, m + 1, 1, DIM_TEXT)
    hits += Hit(IRect(dotX - 8, 0, w - dotX + 8, m + font.lineHeight * 2), HitAction.ChooseResources)
}

private fun drawWaiting(g: Gfx, input: ScreenInput, w: Int, h: Int, top: Int, hits: MutableList<Hit>) {
    val font = input.assets.font
    val lh = font.lineHeight
    val (title, hint) = when (input.status) {
        ConnectionStatus.GAME_CLOSED -> "GAME NOT RUNNING" to "START ISAAC WITH --LUADEBUG AND THE COMPANION MOD"
        ConnectionStatus.WAITING_FOR_GAME -> "WAITING FOR THE GAME" to "CONNECTED, NOTHING RECEIVED YET (MENU OR LOADING)"
        ConnectionStatus.NO_RUN -> "NO RUN IN PROGRESS" to "START OR CONTINUE A RUN"
        ConnectionStatus.CONNECTED -> "LOADING" to ""
    }
    var y = top + (h - top) / 4
    for (line in wrapText(font, title, w - 24)) {
        g.outlinedText(font, line, (w - font.width(line, 2)) / 2, y, 2, WHITE_TEXT)
        y += lh * 2 + 2
    }
    y += 4
    for (line in wrapText(font, hint, w - 32)) {
        g.text(font, line, (w - font.width(line)) / 2, y, 1, DIM_TEXT)
        y += lh + 2
    }
    if (!input.resourcesConfigured) {
        y += 12
        val label = "CHOOSE RESOURCES FOLDER"
        val bw = font.width(label) + 16
        val r = IRect((w - bw) / 2, y, bw, lh + 10)
        g.fillRect(r, TAB_ON_FRAME)
        g.fillRect(r.inset(1), TAB_ON)
        g.text(font, label, r.x + 8, r.y + 5, 1, WHITE_TEXT)
        hits += Hit(r, HitAction.ChooseResources)
        val note = wrapText(font, "WITHOUT YOUR EXTRACTED GAME FILES THE APP USES PLACEHOLDER ART", w - 32)
        var ny = r.bottom + 8
        for (line in note) {
            g.text(font, line, (w - font.width(line)) / 2, ny, 1, DIM_TEXT)
            ny += lh + 2
        }
    }
}

private fun iconSlot(g: Gfx, input: ScreenInput, r: IRect, key: ItemKey, selected: Boolean, hits: MutableList<Hit>, badge: String? = null) {
    val info = input.items[key]
    val icon = input.assets.itemIcon(info)
    if (icon != null) {
        g.draw(icon, r.x + (r.w - icon.sw) / 2, r.y + (r.h - icon.sh) / 2, 1)
    } else {
        g.fillRect(r, TILE_EDGE)
        g.fillRect(r.inset(1), TILE)
        val label = key.id.toString()
        val font = input.assets.font
        g.text(font, label, r.x + (r.w - font.width(label)) / 2, r.y + (r.h - font.lineHeight) / 2, 1, argb(255, 0xb0, 0xa0, 0x90))
    }
    if (badge != null) {
        val font = input.assets.font
        g.outlinedText(font, badge, r.right - font.width(badge) - 1, r.bottom - font.lineHeight, 1, WHITE_TEXT)
    }
    if (selected) g.strokeRect(r.inset(-1), WHITE_TEXT)
    hits += Hit(r, HitAction.Item(key))
}

private fun drawHud(g: Gfx, input: ScreenInput, p: Player, x: Int, y: Int, width: Int, hits: MutableList<Hit>) {
    val assets = input.assets
    val font = assets.font
    val rowH = maxOf(font.lineHeight + 7, 14)
    listOf(assets.coin to p.coins, assets.bomb to p.bombs, assets.key to p.keys).forEachIndexed { i, (sprite, n) ->
        val ry = y + i * rowH
        g.draw(sprite, x + 2, ry + (rowH - sprite.sh) / 2, 1)
        g.outlinedText(font, "%02d".format(n), x + 14, ry + (rowH - font.lineHeight) / 2, 1, WHITE_TEXT)
    }
    val ax = x + 42
    if (p.active.id > 0) {
        val r = IRect(ax, y, ICON, ICON)
        iconSlot(g, input, r, ItemKey("c", p.active.id), input.selected == ItemKey("c", p.active.id), hits)
        drawChargeBar(g, p.active, IRect(r.right + 3, y, 5, ICON))
    }
    if (p.pocketActive.id > 0) {
        val r = IRect(ax + ICON + 14, y, ICON, ICON)
        iconSlot(g, input, r, ItemKey("c", p.pocketActive.id), input.selected == ItemKey("c", p.pocketActive.id), hits)
        drawChargeBar(g, p.pocketActive, IRect(r.right + 3, y, 5, ICON))
    }
    // pocket row: trinkets, card, pill
    var px = x + 2
    val py = y + maxOf(ICON, 3 * rowH) + 6
    for (t in p.trinkets.filter { it > 0 }) {
        val key = ItemKey("t", t)
        iconSlot(g, input, IRect(px, py, ICON, ICON), key, input.selected == key, hits)
        px += ICON + 4
    }
    if (p.card > 0) {
        val key = ItemKey("k", p.card)
        drawSmallSlot(g, input, IRect(px, py, ICON, ICON), assets.card, key, input.selected == key, hits)
        px += ICON + 4
    }
    if (p.pill > 0) {
        val key = ItemKey("p", p.pill)
        drawSmallSlot(g, input, IRect(px, py, ICON, ICON), assets.pill, key, input.selected == key, hits)
    }
}

private fun drawSmallSlot(g: Gfx, input: ScreenInput, r: IRect, sprite: Sprite, key: ItemKey, selected: Boolean, hits: MutableList<Hit>) {
    val scale = (minOf(r.w, r.h) / maxOf(sprite.sw, sprite.sh)).coerceAtLeast(1).coerceAtMost(3)
    g.draw(sprite, r.x + (r.w - sprite.sw * scale) / 2, r.y + (r.h - sprite.sh * scale) / 2, scale)
    if (selected) g.strokeRect(r.inset(-1), WHITE_TEXT)
    hits += Hit(r, HitAction.Item(key))
}

private fun drawChargeBar(g: Gfx, slot: ActiveSlot, r: IRect) {
    if (slot.maxCharge <= 0) return
    g.fillRect(r, argb(255, 0, 0, 0))
    val inner = r.inset(1)
    g.fillRect(inner, argb(255, 0x22, 0x22, 0x22))
    val total = slot.maxCharge + slot.battery
    val filled = (slot.charge + slot.battery).coerceIn(0, total)
    val fh = inner.h * filled / total
    g.fillRect(inner.x, inner.bottom - fh, inner.w, fh, argb(255, 0x46, 0xc9, 0x4f))
    if (slot.battery > 0) {
        val bh = inner.h * slot.battery / total
        g.fillRect(inner.x, inner.bottom - fh, inner.w, bh.coerceAtLeast(1), argb(255, 0x8f, 0xc4, 0xff))
    }
    if (slot.maxCharge in 2..12) {
        for (i in 1 until slot.maxCharge) {
            g.fillRect(inner.x, inner.bottom - inner.h * i / total, inner.w, 1, argb(160, 0, 0, 0))
        }
    }
}

/** Draws the parchment with the passive grid; returns the maximum scroll offset. */
private fun drawPassives(g: Gfx, input: ScreenInput, p: Player, panel: IRect, hits: MutableList<Hit>): Int {
    g.draw(Sprite(Parchment.render(panel.w, panel.h)), panel.x, panel.y, 1)
    val pad = 10
    val inner = IRect(panel.x + pad, panel.y + pad, panel.w - 2 * pad, panel.h - 2 * pad)
    val cell = ICON + 2
    val cols = (inner.w / cell).coerceAtLeast(1)
    val order = p.pickupOrder.ifEmpty { p.items.map { it.id } }
    val counts = p.items.associate { it.id to it.count }
    val rows = (order.size + cols - 1) / cols
    val contentH = rows * cell
    val maxScroll = (contentH - inner.h).coerceAtLeast(0)
    val scroll = input.scroll.coerceIn(0, maxScroll)
    g.pushClip(inner.x, inner.y, inner.w, inner.h)
    hits += Hit(inner, HitAction.Grid)
    val gx = inner.x + (inner.w - cols * cell) / 2
    order.forEachIndexed { i, id ->
        val r = IRect(gx + (i % cols) * cell, inner.y + (i / cols) * cell - scroll, ICON, ICON)
        if (r.bottom < inner.y || r.y > inner.bottom) return@forEachIndexed
        val key = ItemKey("c", id)
        val n = counts[id] ?: 1
        iconSlot(g, input, r, key, input.selected == key, hits, if (n > 1) "x$n" else null)
    }
    g.popClip()
    if (order.isEmpty()) {
        val font = input.assets.font
        g.text(font, "NO ITEMS YET", inner.x + 4, inner.y + 4, 1, INK_SOFT)
    }
    if (maxScroll > 0) { // slim scroll indicator
        val trackX = panel.right - 6
        g.fillRect(trackX, inner.y, 2, inner.h, argb(60, 0, 0, 0))
        val th = (inner.h * inner.h / contentH).coerceAtLeast(8)
        val ty = inner.y + (inner.h - th) * scroll / maxScroll
        g.fillRect(trackX, ty, 2, th, INK)
    }
    return maxScroll
}

private fun drawBubble(g: Gfx, input: ScreenInput, key: ItemKey, player: Player?, panel: IRect, w: Int, h: Int, m: Int) {
    val assets = input.assets
    val font = assets.font
    val info = input.items[key]
    val lh = font.lineHeight
    val bw = w - 2 * (m + 8)
    val textW = bw - ICON - 22

    val fallbackName = when (key.kind) { "t" -> "TRINKET" ; "k" -> "CARD" ; "p" -> "PILL" ; else -> "ITEM" } + " ${key.id}"
    val unidentifiedPill = key.kind == "p" && info?.name == null
    val name = (input.names.resolve(info?.name) ?: if (unidentifiedPill) "???" else fallbackName).uppercase()
    val quote = input.names.resolve(info?.desc)?.takeIf { !unidentifiedPill }.orEmpty()
    val details = info?.eid?.let(::eidToLines).orEmpty()

    val nameLines = wrapText(font, name, textW / 2)
    val quoteLines = if (quote.isEmpty()) emptyList() else wrapText(font, quote, textW)
    val detailLines = details.flatMap { wrapText(font, "- $it", bw - 20) }
    val activeLine = activeCharge(key, player)

    var contentH = nameLines.size * (lh * 2 + 1) + quoteLines.size * (lh + 1) + (if (activeLine != null) lh + 2 else 0)
    contentH = maxOf(contentH, ICON)
    if (detailLines.isNotEmpty()) contentH += 6 + detailLines.size * (lh + 1)
    val bh = (contentH + 16).coerceAtMost(h - 2 * m)
    val bx = (w - bw) / 2
    // below the grid when there is room, otherwise over its lower part
    val by = (h - m - bh).coerceAtLeast(m)

    g.fillRect(bx + 2, by + 2, bw, bh, argb(150, 0, 0, 0))
    g.fillRect(bx, by, bw, bh, INK)
    g.fillRect(bx + 1, by + 1, bw - 2, bh - 2, BUBBLE_FILL)

    val icon = assets.itemIcon(info)
    val ix = bx + 8
    val iy = by + 8
    if (icon != null) g.draw(icon, ix, iy, 1) else {
        g.fillRect(ix, iy, ICON, ICON, TILE_EDGE)
        g.fillRect(ix + 1, iy + 1, ICON - 2, ICON - 2, TILE)
    }
    var ty = iy
    val tx = ix + ICON + 6
    for (line in nameLines) { g.text(font, line, tx, ty, 2, INK); ty += lh * 2 + 1 }
    for (line in quoteLines) { g.text(font, line, tx, ty, 1, INK_SOFT); ty += lh + 1 }
    if (activeLine != null) { g.text(font, activeLine, tx, ty + 1, 1, INK); ty += lh + 2 }
    if (detailLines.isNotEmpty()) {
        ty = maxOf(ty, iy + ICON) + 2
        var sx = bx + 8
        while (sx < bx + bw - 8) { g.fillRect(sx, ty, 2, 1, INK); sx += 4 }
        ty += 4
        for (line in detailLines) {
            if (ty + lh > by + bh - 6) break
            g.text(font, line, bx + 10, ty, 1, INK)
            ty += lh + 1
        }
    }
}

private fun activeCharge(key: ItemKey, player: Player?): String? {
    if (player == null || key.kind != "c") return null
    val slot = listOf(player.active, player.pocketActive).firstOrNull { it.id == key.id && it.maxCharge > 0 } ?: return null
    return "CHARGE ${slot.charge}/${slot.maxCharge}" + if (slot.battery > 0) " +${slot.battery}" else ""
}
