# TODO

Reference docs: `docs/protocol.md` (wire format), `docs/DECISIONS.md` (choices taken and things to verify in game).

## Done

- [x] Step 0: `mod/isaac-companion-hello` (bridge feasibility test), `.luacheckrc`, protocol v1 doc, decisions doc.

## Waiting on the user

- [ ] **Bridge test**: copy `mod/isaac-companion-hello/` into the game's `mods/` folder (GameNative container), launch with `--luadebug`, start a run, then in Termux `nc 127.0.0.1 47777`. Expect `hello from isaac`. If not, send `nc` output and the game's `log.txt`.
- [ ] **Extraction**: run the game's `ResourceExtractor` and copy `resources/` to the Thor (e.g. `Documents/IsaacResources/`).
- [ ] Send `isaac_files.txt` (`find . -type f | sort`) and `isaac_text.tgz` (`gfx/ui/*.anm2`, `font/*.fnt`, `items.xml`, `pocketitems.xml`, `stringtable.sta`).
- [ ] Send 3 reference screenshots (in-game HUD + minimap, full map, pause screen), and one under Curse of the Lost if possible.
- [ ] Confirm the defaults in `docs/DECISIONS.md` (or change them), and say whether External Item Descriptions (EID) is installed.

## Remaining implementation (in order)

1. [ ] `README.md`: install the mod, `--luadebug` in GameNative, the `nc` test, resource extraction, warning that `--luadebug` gives mods `io`/`os` access (trusted mods only).
2. [ ] `tools/fake_server.py`: port 47777, realistic scenario (stage changes, rooms discovered, L-shaped and out-of-grid rooms, Curse of the Lost, 2 players + Jacob & Esau, items added/removed), pings, `itemdb`, optional malformed lines and unknown fields, "mute" mode (menu).
3. [ ] `mod/isaac-companion/` (`metadata.xml`, `main.lua`): non-blocking server with retry and reload handling, send buffer (latest snapshot only), custom JSON encoder with fixed key order, collection under `pcall` (meta, filtered map, players, items), `GetCollectibleNum(id, true)`, pickup order persisted per seed, `idle` on game exit, `itemdb` messages, optional EID descriptions, anti-spoil rules. Constants for `DisplayFlags` bits at the top.
4. [ ] Mod tests: Lua 5.3 harness with stubbed Isaac API and LuaSocket (no `socket`, slow client, disconnect, reload). `luacheck mod/` clean.
5. [ ] Android data layer (`android/`, Kotlin, Compose, coroutines, kotlinx.serialization): lenient models (`ignoreUnknownKeys`), TCP client with 1 s reconnect and 500 ms connect timeout, `StateFlow`, connection states (game closed / waiting for game / no run / connected), `itemdb` cache. JVM unit tests (malformed JSON, unknown fields) and a test against `fake_server.py`.
6. [ ] Resource pipeline: SAF folder picker with persisted permission, case-insensitive file index via `DocumentsContract`, bitmap cache, `.anm2` parser, BMFont renderer, `stringtable.sta` name resolution. Tests on a synthetic resource tree (nothing proprietary).
7. [ ] UI variant **(c)** (see mockups): HUD-style stage header, map (shapes, 3 grey levels, type icons, current room, integer scale, bounding-box crop), counters, active with charge bar, trinkets, card, pill, player tabs, parchment passive grid in pickup order. Drawn on `Canvas`, nearest neighbour, no visible Material components.
8. [ ] Tap an item -> parchment description bubble (name, in-game quote, EID text when available); tap elsewhere closes it. Pill not identified -> `???`.
9. [ ] Setup screen when no resource folder is configured, plain-tile fallback for debugging, immersive fullscreen, `FLAG_KEEP_SCREEN_ON`, resizable activity.
10. [ ] `ResourceMap.kt` (single mapping file of sprite/frame names), fix it once the real file list is available.
11. [ ] CI (`.github/workflows/build.yml`): luacheck, mod tests, app unit tests, debug APK artifact.

## Environment notes

- Installed in the dev container: Lua 5.3/5.4, luacheck, LuaSocket, Java, Gradle 8.14.3. The Android SDK still has to be downloaded for the app build.
- Mockups live outside the repo (scratchpad); regenerate or move into `docs/` if wanted.
