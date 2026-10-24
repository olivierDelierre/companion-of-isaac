# Wire protocol v1

Transport: TCP, the mod is the **server** on `127.0.0.1:47777`, the app is the client.
Framing: NDJSON, one JSON object per line, UTF-8, `\n` terminated.
Every message has `type` and `v` (protocol version, currently `1`). Receivers ignore unknown fields and unknown message types.

## Messages (mod -> app)

### `hello`
Sent first on every new connection.
```json
{"type":"hello","v":1,"mod":"0.1.0","eid":true}
```
`eid` says whether External Item Descriptions was detected.

### `ping`
Heartbeat, at least every ~2 s while the game renders. The app treats 5 s of silence as "waiting for the game".
```json
{"type":"ping","v":1}
```

### `idle`
No run in progress (sent on game exit; not sent from the main menu, where no mod callback runs).
```json
{"type":"idle","v":1}
```

### `itemdb`
Static metadata for items, sent **before** the first `state` that references them (per connection), and for every referenced item right after `hello` on connect. The app caches entries by `(kind, id)`.
```json
{"type":"itemdb","v":1,"items":[
  {"kind":"c","id":1,"name":"The Sad Onion","desc":"Tears up","gfx":"gfx/items/collectibles/collectibles_001_thesadonion.png","active":false,"eid":"{{Tears}} +0.7 Tears"}
]}
```
- `kind`: `c` collectible, `t` trinket, `k` card, `p` pill color.
- `name` / `desc` may be localisation keys starting with `#`; the app resolves them with `stringtable.sta` when available.
- `eid` is present only when EID is installed, raw EID markup (`{{Icon}}`, `#` line separators).
- Pills: `name`/`desc` only when the pill is identified; otherwise omitted.

### `state`
Full snapshot, sent when it differs from the previous one and once on connect.
```json
{"type":"state","v":1,
 "meta":{"stage":3,"stageType":0,"levelName":"Caves I","curses":0,"seed":"ABCD 1234"},
 "map":{"currentRoom":84,"mapHidden":false,"rooms":[
   {"gridIndex":84,"safeGridIndex":84,"type":1,"shape":1,"displayFlags":1,"visited":true,"clear":true,"current":true}]},
 "players":[{"index":0,"name":"Isaac","playerType":0,"isChild":false,"isCoopGhost":false,
   "coins":7,"bombs":1,"keys":2,
   "active":{"id":105,"charge":6,"maxCharge":6,"battery":0},
   "pocketActive":{"id":0,"charge":0,"maxCharge":0},
   "trinkets":[0,0],"card":0,"pill":0,"pillIdentified":false,
   "items":[{"id":1,"count":1}],
   "pickupOrder":[1]}]}
```
Details:
- `map.rooms` only contains rooms the game's minimap would show (`displayFlags > 0` or visited). `type` is `-1` when the minimap would show the room without its icon (anti-spoil). Rooms outside `0..168` are dropped.
- `gridIndex` is `RoomDescriptor.GridIndex`, the top-left cell of the room's bounding box. `safeGridIndex` is `SafeGridIndex` (first occupied cell; differs for L-shaped rooms).
- `currentRoom` is the `gridIndex` of the room the player is in, or `-1` when outside the grid (devil/angel, crawlspace, black market...).
- `mapHidden` is `true` under Curse of the Lost: only the current room is sent.
- `items` are collectibles with count > 0; `pickupOrder` lists their ids in pickup order (reconstructed by the mod, persisted per seed).
- Pocket active / active: `id` 0 means empty.
- `card` is a card id, `pill` a pill color; 0 means none. `pillIdentified` flips when the pill is identified, which makes the mod resend its `itemdb` entry.
