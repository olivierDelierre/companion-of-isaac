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

## Done (autonomous session)

- [x] `README.md`, `tools/fake_server.py`, `.github/workflows/build.yml`.
- [x] `mod/isaac-companion`: server, collection, anti-spoil, pickup order, itemdb, EID, tests (`lua5.3 tests/run.lua`, 16 passing).
- [x] Android: protocol parser, reconnecting TCP client, `.anm2` / BMFont / `stringtable.sta` parsers, SAF source, UI variant (c), tap bubble, tabs, scroll, immersive + keep-screen-on. 39 JVM tests (incl. contract tests on the real mod output and the fake server); the debug APK builds.

## Remaining

1. [ ] **Real sprites once the extraction is available** (`ResourceMap` in `android/.../ui/GameAssets.kt`): HUD pickups (`gfx/ui/hudpickups.png`), charge bar, cards/pills, minimap room/icon frames (via `Anm2`), paper texture. Today only item/trinket icons (paths come from the mod), the font (first of `FONT_CANDIDATES` that parses) and names (`stringtable.sta`) use game files; everything else is original placeholder art.
2. [ ] **Verify against the real files**: BMFont flavour of `font/*.fnt`, `stringtable.sta` layout (`StringTable`), `.anm2` sprite sheet paths.
3. [ ] **Verify in game** (see `docs/DECISIONS.md`): `GridIndex`/`SafeGridIndex` for L rooms, `DisplayFlags` bits, `GfxFileName` shape, `EID:getDescriptionObj`.
4. [ ] Card and pill sprites (placeholders now), pill/trinket naming without resources.
5. [ ] Tune the layout on the real Thor screen (map size, item grid, empty space in the right column).
6. [ ] Optional: persist the item database between launches, refine the setup screen, release signing.

## Environment notes

- Dev container: Lua 5.3, luacheck, LuaSocket, JDK 21, Gradle 8.14.3 + wrapper, Android SDK 34 in `/opt/android-sdk`.
- Maven Central answers 429 from the sandbox; a local init script (`~/.gradle/init.d/mirror.gradle`, not in the repo) points Gradle at Google's Maven Central mirror. GitHub Actions needs nothing special.
- The mockups (variants a/b/c) are not in the repo.
