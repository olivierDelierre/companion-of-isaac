# Decisions

Choices taken without waiting for an answer. Each one is cheap to change; say so and it gets reworked.

| # | Question | Default taken |
|---|----------|---------------|
| 1 | UI style | Variant **(c)**: HUD style (dark) for stage name, map and counters, parchment for the passive grid and the description bubble. Confirmed. |
| 2 | Curse of the Lost | The mod sends only the current room and `mapHidden: true`; the app shows only that room. Strictest reading of "never more than the game's minimap". |
| 3 | Dropped actives in the passive grid | Not shown. The grid holds passives only; the active item has its own slot. |
| 4 | Item name language | Device language, English fallback. |
| 5 | CI | GitHub Actions: luacheck, mod tests, app unit tests, debug APK as an artifact. |
| 6 | Item descriptions | Short in-game quote (always available) plus the detailed text from External Item Descriptions (EID) when that mod is installed. No bundled description database. |
| 7 | Tap on an item | Opens the description bubble; tapping elsewhere closes it. Replaces the long press of the original spec. |
| 8 | Protocol changes vs. the original spec | See `docs/protocol.md`: `gridIndex` is `Descriptor.GridIndex` (top-left of the bounding box), `safeGridIndex` is sent too, `current` flag on the room, item metadata moved to `itemdb` messages, custom JSON encoder in the mod. |

## Things to verify in game

These cannot be tested without the game; the mod and the app keep them in one place each so they are easy to fix.

- `GridIndex` vs `SafeGridIndex` for L-shaped rooms (`ROOM_SHAPE_CELLS` in the app, `docs/protocol.md`).
- `DisplayFlags` bit meaning (`DISPLAY_*` constants at the top of `mod/isaac-companion/main.lua`).
- Whether the ResourceExtractor outputs `stringtable.sta`, and the exact file names under `gfx/ui` (`ResourceMap.kt` in the app).
- Shape of `GfxFileName` in Repentance+.
