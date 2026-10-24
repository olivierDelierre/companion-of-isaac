package app.isaaccompanion

import app.isaaccompanion.protocol.Message
import app.isaaccompanion.protocol.MessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageParserTest {
    private val state = """{"type":"state","v":1,
      "meta":{"stage":3,"stageType":0,"levelName":"Caves I","curses":0,"seed":"ABCD 1234"},
      "map":{"currentRoom":84,"mapHidden":false,"rooms":[
        {"gridIndex":84,"safeGridIndex":84,"type":1,"shape":1,"displayFlags":5,"visited":true,"clear":true,"current":true},
        {"gridIndex":100,"safeGridIndex":101,"type":-1,"shape":9,"displayFlags":2}]},
      "players":[{"index":0,"name":"Isaac","playerType":0,"coins":7,"bombs":1,"keys":2,
        "active":{"id":105,"charge":6,"maxCharge":6,"battery":0},"trinkets":[1,0],"card":0,"pill":0,
        "items":[{"id":1,"count":2}],"pickupOrder":[1]}]}""".replace("\n", "")

    @Test
    fun parsesState() {
        val m = MessageParser.parse(state) as Message.State
        assertEquals("Caves I", m.state.meta.levelName)
        assertEquals(2, m.state.map.rooms.size)
        assertEquals(101, m.state.map.rooms[1].safeGridIndex)
        assertEquals(-1, m.state.map.rooms[1].type)
        assertEquals(105, m.state.players[0].active.id)
        assertEquals(2, m.state.players[0].items[0].count)
        assertEquals(listOf(1, 0), m.state.players[0].trinkets)
    }

    @Test
    fun ignoresUnknownFieldsAndMissingOnes() {
        val m = MessageParser.parse("""{"type":"state","v":7,"extra":{"a":[1,2]},"meta":{"stage":2,"newThing":true},"players":[{"name":"X","future":1}]}""")
        m as Message.State
        assertEquals(2, m.state.meta.stage)
        assertEquals("X", m.state.players[0].name)
        assertEquals(0, m.state.players[0].coins)
        assertTrue(m.state.map.rooms.isEmpty())
    }

    @Test
    fun toleratesNullsAndWrongTypes() {
        val m = MessageParser.parse("""{"type":"state","meta":{"stage":null,"levelName":null},"players":[{"coins":null}]}""")
        assertTrue(m is Message.State)
    }

    @Test
    fun malformedLinesGiveNull() {
        assertNull(MessageParser.parse("this is not json"))
        assertNull(MessageParser.parse("""{"type":"state","meta":"""))
        assertNull(MessageParser.parse(""))
        assertNull(MessageParser.parse("[1,2,3]"))
        assertNull(MessageParser.parse("""{"type":"state","players":"nope"}"""))
    }

    @Test
    fun unknownTypesAreIgnored() {
        assertNull(MessageParser.parse("""{"type":"future-message","v":9}"""))
        assertNull(MessageParser.parse("""{"v":1}"""))
    }

    @Test
    fun parsesSmallMessages() {
        assertEquals(Message.Ping, MessageParser.parse("""{"type":"ping","v":1}"""))
        assertEquals(Message.Idle, MessageParser.parse("""{"type":"idle"}"""))
        assertEquals(Message.Hello("0.1.0", true), MessageParser.parse("""{"type":"hello","v":1,"mod":"0.1.0","eid":true}"""))
    }

    @Test
    fun parsesItemDb() {
        val m = MessageParser.parse(
            """{"type":"itemdb","v":1,"items":[{"kind":"c","id":1,"name":"The Sad Onion","desc":"Tears up","gfx":"gfx/a.png","eid":"x"},{"kind":"p","id":3}]}""",
        ) as Message.ItemDb
        assertEquals("The Sad Onion", m.items[0].name)
        assertEquals("x", m.items[0].eid)
        assertNull(m.items[1].name)
        assertEquals("p", m.items[1].key.kind)
    }
}
