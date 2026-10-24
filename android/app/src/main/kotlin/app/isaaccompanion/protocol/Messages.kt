package app.isaaccompanion.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Wire protocol v1, see docs/protocol.md. Every field has a default: unknown or missing fields never fail a parse. */

@Serializable
data class Meta(
    val stage: Int = 0,
    val stageType: Int = 0,
    val levelName: String = "",
    val curses: Int = 0,
    val seed: String = "",
)

@Serializable
data class Room(
    val gridIndex: Int = -1,
    val safeGridIndex: Int = gridIndex,
    /** RoomType, or -1 when the game's minimap would not show the room's icon. */
    val type: Int = -1,
    val shape: Int = 1,
    val displayFlags: Int = 0,
    val visited: Boolean = false,
    val clear: Boolean = false,
    val current: Boolean = false,
)

@Serializable
data class MapState(
    val currentRoom: Int = -1,
    val mapHidden: Boolean = false,
    val rooms: List<Room> = emptyList(),
)

@Serializable
data class ActiveSlot(
    val id: Int = 0,
    val charge: Int = 0,
    val maxCharge: Int = 0,
    val battery: Int = 0,
)

@Serializable
data class ItemCount(val id: Int = 0, val count: Int = 1)

@Serializable
data class Player(
    val index: Int = 0,
    val name: String = "",
    val playerType: Int = 0,
    val isChild: Boolean = false,
    val isCoopGhost: Boolean = false,
    val coins: Int = 0,
    val bombs: Int = 0,
    val keys: Int = 0,
    val active: ActiveSlot = ActiveSlot(),
    val pocketActive: ActiveSlot = ActiveSlot(),
    val trinkets: List<Int> = emptyList(),
    val card: Int = 0,
    val pill: Int = 0,
    val pillIdentified: Boolean = false,
    val items: List<ItemCount> = emptyList(),
    val pickupOrder: List<Int> = emptyList(),
)

@Serializable
data class GameState(
    val meta: Meta = Meta(),
    val map: MapState = MapState(),
    val players: List<Player> = emptyList(),
)

/** kind: c collectible, t trinket, k card, p pill color. */
@Serializable
data class ItemInfo(
    val kind: String = "c",
    val id: Int = 0,
    val name: String? = null,
    val desc: String? = null,
    val gfx: String? = null,
    val active: Boolean = false,
    val eid: String? = null,
) {
    val key: ItemKey get() = ItemKey(kind, id)
}

data class ItemKey(val kind: String, val id: Int)

sealed interface Message {
    data class Hello(val mod: String, val eid: Boolean) : Message
    data object Ping : Message
    data object Idle : Message
    data class ItemDb(val items: List<ItemInfo>) : Message
    data class State(val state: GameState) : Message
}

object MessageParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Returns null for malformed lines and for message types this version does not know. */
    fun parse(line: String): Message? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val obj: JsonObject = json.parseToJsonElement(trimmed).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "hello" -> Message.Hello(
                    mod = obj["mod"]?.jsonPrimitive?.content.orEmpty(),
                    eid = obj["eid"]?.jsonPrimitive?.content == "true",
                )
                "ping" -> Message.Ping
                "idle" -> Message.Idle
                "itemdb" -> Message.ItemDb(
                    json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(ItemInfo.serializer()),
                        obj["items"] ?: return null,
                    ),
                )
                "state" -> Message.State(json.decodeFromJsonElement(GameState.serializer(), obj))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
