package app.isaaccompanion

import app.isaaccompanion.net.CompanionClient
import app.isaaccompanion.net.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class CompanionClientTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: ServerSocket? = null
    private var peer: Socket? = null

    @Before
    fun setUp() {
        server = null
    }

    @After
    fun tearDown() {
        scope.cancel()
        peer?.close()
        server?.close()
    }

    private fun listen(port: Int = 0): Int {
        val s = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
        s.reuseAddress = true
        server = s
        return s.localPort
    }

    private fun accept() {
        peer = server!!.accept()
    }

    private fun send(text: String) {
        peer!!.getOutputStream().apply { write(text.toByteArray()); flush() }
    }

    private suspend fun until(timeoutMs: Long = 5000, cond: () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(20) }
    }

    private fun client(port: Int, silenceMs: Long = 5000) =
        CompanionClient(port = port, retryDelayMs = 100, silenceTimeoutMs = silenceMs, readPollMs = 50)

    private val stateLine =
        """{"type":"state","v":1,"meta":{"stage":2,"levelName":"Basement II"},"map":{"currentRoom":84},"players":[{"name":"Isaac"}]}""" + "\n"

    @Test
    fun streamsStateAndItemDb() = runBlocking {
        val port = listen()
        val c = client(port)
        c.start(scope)
        accept()
        send("""{"type":"hello","v":1,"mod":"x","eid":true}""" + "\n")
        send("""{"type":"itemdb","v":1,"items":[{"kind":"c","id":1,"name":"The Sad Onion"}]}""" + "\n")
        send(stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
        assertEquals("Basement II", c.state.value!!.meta.levelName)
        assertEquals("The Sad Onion", c.items.value.values.single().name)
        assertTrue(c.eidAvailable.value)
    }

    @Test
    fun survivesGarbageBetweenMessages() = runBlocking {
        val port = listen()
        val c = client(port)
        c.start(scope)
        accept()
        send("not json\n" + """{"type":"state","v":1,"meta":""" + "\n" + """{"type":"zzz"}""" + "\n" + stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
        assertEquals(2, c.state.value!!.meta.stage)
    }

    @Test
    fun idleClearsTheState() = runBlocking {
        val port = listen()
        val c = client(port)
        c.start(scope)
        accept()
        send(stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
        send("""{"type":"idle","v":1}""" + "\n")
        until { c.status.value == ConnectionStatus.NO_RUN }
        assertNull(c.state.value)
    }

    @Test
    fun silenceMeansWaitingThenPingRestores() = runBlocking {
        val port = listen()
        val c = client(port, silenceMs = 300)
        c.start(scope)
        accept()
        send(stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
        until { c.status.value == ConnectionStatus.WAITING_FOR_GAME }
        send("""{"type":"ping","v":1}""" + "\n")
        until { c.status.value == ConnectionStatus.CONNECTED }
    }

    @Test
    fun reconnectsAfterTheServerIsKilledAndRestarted() = runBlocking {
        val port = listen()
        val c = client(port)
        c.start(scope)
        accept()
        send(stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
        peer!!.close()
        server!!.close()
        until { c.status.value == ConnectionStatus.GAME_CLOSED }
        assertNull(c.state.value)
        listen(port)
        accept()
        send(stateLine)
        until { c.status.value == ConnectionStatus.CONNECTED }
    }

    @Test
    fun reportsGameClosedWhenNothingListens() = runBlocking {
        val port = listen()
        server!!.close()
        val c = client(port)
        c.start(scope)
        delay(300)
        assertEquals(ConnectionStatus.GAME_CLOSED, c.status.value)
    }
}
