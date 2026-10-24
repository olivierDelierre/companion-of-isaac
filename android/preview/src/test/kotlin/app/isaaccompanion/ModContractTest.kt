package app.isaaccompanion

import app.isaaccompanion.net.CompanionClient
import app.isaaccompanion.net.ConnectionStatus
import app.isaaccompanion.protocol.Message
import app.isaaccompanion.protocol.MessageParser
import app.isaaccompanion.ui.AwtGfx
import app.isaaccompanion.ui.BuiltinAssets
import app.isaaccompanion.ui.ScreenInput
import app.isaaccompanion.ui.drawScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import javax.imageio.ImageIO

/** The real mod output (captured from the Lua test harness) and the real fake server against the app code. */
class ModContractTest {
    @Test
    fun everyLineOfTheModOutputParses() {
        val lines = javaClass.getResource("/mod_output.ndjson")!!.readText().lines().filter { it.isNotBlank() }
        val messages = lines.map { MessageParser.parse(it) ?: error("unparsed line: $it") }
        assertTrue(messages.first() is Message.Hello)
        assertTrue(messages.last() is Message.Idle)
        val state = messages.filterIsInstance<Message.State>().first().state
        assertEquals(2, state.players.size)
        assertEquals("Caves I", state.meta.levelName)
        val room = state.map.rooms.first { it.gridIndex == 100 }
        assertEquals(101, room.safeGridIndex)
        assertEquals(9, room.shape)
        assertEquals(-1, state.map.rooms.first { it.gridIndex == 82 }.type) // anti-spoil value reaches the app
        assertTrue(state.map.rooms.none { it.gridIndex > 168 })
        val p1 = state.players[0]
        assertEquals(105, p1.active.id)
        assertEquals(6, p1.active.maxCharge)
        assertEquals(listOf(1, 2), p1.pickupOrder)
        val pills = messages.filterIsInstance<Message.ItemDb>().flatMap { it.items }.filter { it.kind == "p" }
        assertTrue(pills.any { it.name == null })
        assertTrue(pills.any { it.name == "Effect9" })
    }

    @Test
    fun appTalksToTheFakeServer(): Unit = runBlocking {
        val script = File("../../tools/fake_server.py")
        assumeTrue(script.exists())
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false)
        }
        assumeTrue(python != null)
        val port = ServerSocket(0).use { it.localPort }
        val proc = ProcessBuilder(python, script.path, "--port", port.toString(), "--players", "2", "--chaos", "--interval", "0.05")
            .redirectErrorStream(true).start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val client = CompanionClient(port = port, retryDelayMs = 100, readPollMs = 50)
            client.start(scope)
            withTimeout(15000) {
                while (client.status.value != ConnectionStatus.CONNECTED || client.items.value.size < 3) delay(50)
            }
            delay(1200) // let the scripted run progress
            val state = client.state.value!!
            assertEquals(2, state.players.size)
            assertTrue(state.map.rooms.isNotEmpty())
            val g = AwtGfx(310, 270, 4)
            drawScreen(g, 310, 270, ScreenInput(client.status.value, state, client.items.value, BuiltinAssets))
            val dir = File(System.getProperty("screens.dir") ?: "build/screens").apply { mkdirs() }
            ImageIO.write(g.image, "png", File(dir, "fake_server_live.png"))
            Unit
        } finally {
            scope.cancel()
            proc.destroy()
        }
    }
}
