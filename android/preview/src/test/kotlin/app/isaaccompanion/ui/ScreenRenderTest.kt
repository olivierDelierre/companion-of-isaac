package app.isaaccompanion.ui

import app.isaaccompanion.net.ConnectionStatus
import app.isaaccompanion.protocol.ActiveSlot
import app.isaaccompanion.protocol.GameState
import app.isaaccompanion.protocol.ItemCount
import app.isaaccompanion.protocol.ItemInfo
import app.isaaccompanion.protocol.ItemKey
import app.isaaccompanion.protocol.MapState
import app.isaaccompanion.protocol.Message
import app.isaaccompanion.protocol.MessageParser
import app.isaaccompanion.protocol.Meta
import app.isaaccompanion.protocol.Player
import app.isaaccompanion.protocol.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ScreenRenderTest {
    private val outDir = File(System.getProperty("screens.dir") ?: "build/screens").apply { mkdirs() }

    private fun room(x: Int, y: Int, type: Int, shape: Int = 1, flags: Int = 1, visited: Boolean = true, current: Boolean = false) =
        Room(gridIndex = y * 13 + x, type = type, shape = shape, displayFlags = flags, visited = visited, clear = visited, current = current)

    private val map = MapState(
        currentRoom = 6 * 13 + 6,
        rooms = listOf(
            room(6, 6, 1, current = true), room(5, 6, 1), room(7, 6, 1, shape = 6), room(6, 5, 1), room(6, 4, 1),
            room(5, 7, 1, shape = 11), room(6, 3, 5, flags = 5, visited = false), room(4, 6, 4, flags = 5, visited = false),
            room(9, 6, 2, flags = 5, visited = false), room(7, 4, -1, flags = 2, visited = false),
            room(9, 7, -1, shape = 4, flags = 1, visited = false), room(4, 8, 10, flags = 5, visited = false),
        ),
    )

    private fun player(name: String, items: List<Int>, active: Int = 105) = Player(
        name = name, coins = 12, bombs = 3, keys = 2,
        active = ActiveSlot(active, 4, 6), trinkets = listOf(1, 5), card = 11, pill = 3,
        items = items.map { ItemCount(it, if (it == 4) 2 else 1) }, pickupOrder = items,
    )

    private fun db(): Map<ItemKey, ItemInfo> = buildList {
        add(ItemInfo("c", 1, "The Sad Onion", "Tears up", eid = "{{Tears}} +0.7 Tears#Also increases range slightly"))
        add(ItemInfo("c", 2, "The Inner Eye", "Triple shot"))
        add(ItemInfo("c", 105, "The D6", "Reroll", active = true, eid = "Rerolls all items in the room"))
        add(ItemInfo("t", 1, "Swallowed Penny", "Drops coins"))
        add(ItemInfo("k", 11, "XI - Justice", "May fortune find you"))
        add(ItemInfo("p", 3, "Bombs are keys", "Bombs and keys swap"))
    }.associateBy { it.key }

    private fun render(name: String, input: ScreenInput, w: Int = 310, h: Int = 270, scale: Int = 4): ScreenResult {
        val g = AwtGfx(w, h, scale)
        val result = drawScreen(g, w, h, input)
        ImageIO.write(g.image, "png", File(outDir, "$name.png"))
        return result
    }

    private fun base(items: List<Int> = listOf(1, 2, 3, 4, 5, 7, 9, 34, 118, 168)) = ScreenInput(
        status = ConnectionStatus.CONNECTED,
        state = GameState(Meta(3, 0, "Caves I", 0, "ABCD 1234"), map, listOf(player("Isaac", items))),
        items = db(),
        assets = BuiltinAssets,
    )

    @Test
    fun connectedSinglePlayer() {
        val r = render("single", base())
        assertTrue(r.hits.any { it.action is HitAction.Item })
        assertEquals(0, r.maxScroll)
    }

    @Test
    fun twoPlayersHaveTabs() {
        val input = base().let {
            it.copy(state = it.state!!.copy(players = listOf(player("Isaac", listOf(1, 2)), player("Cain", listOf(3)))))
        }
        val r = render("coop", input)
        assertEquals(2, r.hits.count { it.action is HitAction.Tab })
        render("coop_tab2", input.copy(tab = 1))
    }

    @Test
    fun childAndGhostPlayersAreHidden() {
        val input = base().let {
            it.copy(state = it.state!!.copy(players = listOf(player("Isaac", listOf(1)), player("Strawman", listOf(2)).copy(isChild = true))))
        }
        val r = render("child_hidden", input)
        assertEquals(0, r.hits.count { it.action is HitAction.Tab })
    }

    @Test
    fun lostCurseShowsOnlyTheCurrentRoom() {
        val lost = map.copy(mapHidden = true, rooms = map.rooms.filter { it.current })
        val input = base().let { it.copy(state = it.state!!.copy(meta = it.state!!.meta.copy(curses = 4), map = lost)) }
        render("lost", input)
    }

    @Test
    fun waitingScreens() {
        for (s in ConnectionStatus.entries) {
            render("waiting_$s", ScreenInput(s, null, emptyMap(), BuiltinAssets))
        }
        val r = render("waiting_configured", ScreenInput(ConnectionStatus.NO_RUN, null, emptyMap(), BuiltinAssets, resourcesConfigured = true))
        assertTrue(r.hits.none { it.action == HitAction.ChooseResources && it.rect.y > 20 })
    }

    @Test
    fun bubbleForPassiveAndActive() {
        render("bubble_passive", base().copy(selected = ItemKey("c", 1)))
        render("bubble_active", base().copy(selected = ItemKey("c", 105)))
        render("bubble_pill", base().copy(selected = ItemKey("p", 3)))
        render("bubble_unknown", base().copy(selected = ItemKey("c", 999)))
    }

    @Test
    fun manyItemsScroll() {
        val many = (1..60).toList()
        val r = render("scroll0", base(many))
        assertTrue(r.maxScroll > 0)
        render("scroll_end", base(many).copy(scroll = r.maxScroll))
    }

    @Test
    fun tapResolvesToItems() {
        val r = render("tap", base())
        val item = r.hits.last { it.action is HitAction.Item && (it.action as HitAction.Item).key == ItemKey("c", 2) }
        assertNotNull(item)
    }

    @Test
    fun otherSizesDoNotCrash() {
        for ((w, h) in listOf(240 to 200, 400 to 300, 200 to 360, 310 to 270)) {
            render("size_${w}x$h", base(), w, h, 2)
        }
    }

    @Test
    fun parsedFakeServerStateRenders() {
        val line = """{"type":"state","v":1,"meta":{"stage":2,"levelName":"Basement II"},"map":{"currentRoom":84,"rooms":[{"gridIndex":84,"type":1,"shape":1,"visited":true,"current":true,"displayFlags":1}]},"players":[{"name":"Isaac"}]}"""
        val st = (MessageParser.parse(line) as Message.State).state
        render("minimal", ScreenInput(ConnectionStatus.CONNECTED, st, emptyMap(), BuiltinAssets))
    }
}
