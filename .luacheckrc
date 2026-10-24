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

globals = {
  "__IsaacCompanionServer", -- also accessed as _G.__IsaacCompanionServer
  "_G",
}

files["mod/**"] = {
  allow_defined_top = true,
}
files["tests/**"] = {
  std = "+busted",
  globals = { "RegisterMod", "Isaac", "Game", "ModCallbacks" },
}
