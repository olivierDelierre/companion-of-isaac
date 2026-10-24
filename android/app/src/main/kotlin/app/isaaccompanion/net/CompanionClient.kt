package app.isaaccompanion.net

import app.isaaccompanion.protocol.GameState
import app.isaaccompanion.protocol.ItemInfo
import app.isaaccompanion.protocol.ItemKey
import app.isaaccompanion.protocol.Message
import app.isaaccompanion.protocol.MessageParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

enum class ConnectionStatus {
    /** Nothing listens on the port: the game (or the mod) is not running. */
    GAME_CLOSED,

    /** The socket is open but silent: main menu, loading, or the game is stuck. */
    WAITING_FOR_GAME,

    /** The mod answers, no run in progress. */
    NO_RUN,

    /** The mod streams a run. */
    CONNECTED,
}

/** TCP client of the mod. Reconnects forever; state is exposed through flows. */
class CompanionClient(
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 500,
    private val retryDelayMs: Long = 1000,
    private val silenceTimeoutMs: Long = 5000,
    private val readPollMs: Int = 500,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val _status = MutableStateFlow(ConnectionStatus.GAME_CLOSED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _items = MutableStateFlow<Map<ItemKey, ItemInfo>>(emptyMap())
    val items: StateFlow<Map<ItemKey, ItemInfo>> = _items.asStateFlow()

    private val _eidAvailable = MutableStateFlow(false)
    val eidAvailable: StateFlow<Boolean> = _eidAvailable.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { runForever() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runForever() {
        while (coroutineContext.isActive) {
            try {
                session()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // connect refused, reset, broken pipe...: fall through to the retry delay
            }
            _status.value = ConnectionStatus.GAME_CLOSED
            _state.value = null
            delay(retryDelayMs)
        }
    }

    private suspend fun session() {
        val socket = Socket()
        try {
            withContext(Dispatchers.IO) { socket.connect(InetSocketAddress(host, port), connectTimeoutMs) }
            socket.soTimeout = readPollMs
            socket.tcpNoDelay = true
            _status.value = ConnectionStatus.WAITING_FOR_GAME
            _items.value = emptyMap()
            readLoop(socket)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun readLoop(socket: Socket) {
        val input = socket.getInputStream()
        val buffer = ByteArray(16 * 1024)
        val lines = LineBuffer()
        var lastMessageAt = nanoTime()
        while (coroutineContext.isActive) {
            val n = try {
                input.read(buffer)
            } catch (e: SocketTimeoutException) {
                0
            }
            if (n < 0) return // the game closed the socket
            if (n > 0) {
                lines.feed(buffer, n) { line ->
                    val message = MessageParser.parse(line) ?: return@feed
                    lastMessageAt = nanoTime()
                    handle(message)
                }
            }
            val silentMs = (nanoTime() - lastMessageAt) / 1_000_000
            if (silentMs > silenceTimeoutMs && _status.value != ConnectionStatus.WAITING_FOR_GAME) {
                _status.value = ConnectionStatus.WAITING_FOR_GAME
            }
        }
    }

    internal fun handle(message: Message) {
        when (message) {
            is Message.Hello -> {
                _eidAvailable.value = message.eid
                if (_status.value == ConnectionStatus.WAITING_FOR_GAME) _status.value = ConnectionStatus.NO_RUN
            }
            Message.Ping -> if (_status.value == ConnectionStatus.WAITING_FOR_GAME) {
                _status.value = if (_state.value != null) ConnectionStatus.CONNECTED else ConnectionStatus.NO_RUN
            }
            Message.Idle -> {
                _state.value = null
                _status.value = ConnectionStatus.NO_RUN
            }
            is Message.ItemDb -> _items.value = _items.value + message.items.associateBy { it.key }
            is Message.State -> {
                _state.value = message.state
                _status.value = ConnectionStatus.CONNECTED
            }
        }
    }

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 47777
    }
}
