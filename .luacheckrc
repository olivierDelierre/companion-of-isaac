-- Isaac mod globals. See https://wofsauge.github.io/IsaacDocs/rep/
std = "lua53"
max_line_length = 120

read_globals = {
  "RegisterMod", "Isaac", "Game", "Vector", "Color", "RNG", "Input", "SFXManager", "MusicManager",
  "ModCallbacks", "ActiveSlot", "RoomType", "RoomShape", "LevelCurse", "CollectibleType",
  "TrinketType", "Card", "PillColor", "PillEffect", "PlayerType", "Challenge", "Difficulty",
  "DisplayFlag", "LevelStage", "StageType", "EntityType", "EntityFlag", "ItemType", "ItemPoolType",
  "EID", "REPENTOGON",
}

files["mod/**"] = {
  allow_defined_top = true,
}
files["tests/**"] = {
  -- the test harness installs a fake Isaac API into _G
  globals = { "RegisterMod", "Isaac", "Game", "ModCallbacks", "ActiveSlot", "ItemType", "LevelCurse", "EID" },
  ignore = { "212", "431", "631" },
}
