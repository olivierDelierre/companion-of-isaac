-- Fake Isaac API, just enough to drive mod/isaac-companion/main.lua outside the game.
local S = {}

S.log = {}
S.time = 1000
S.frame = 0
S.callbacks = {}
S.saved = nil

S.world = nil -- set by tests: { stage, curses, rooms = {...}, current = idx, players = {...} }

local function newWorld()
  return {
    stage = 3, stageType = 0, name = "Caves I", curses = 0, seed = "ABCD 1234",
    current = 84,
    rooms = {
      { grid = 84, safe = 84, type = 1, shape = 1, flags = 1, visited = 1, clear = true },
      { grid = 83, safe = 83, type = 1, shape = 1, flags = 1, visited = 1, clear = true },
      { grid = 82, safe = 82, type = 4, shape = 1, flags = 2, visited = 0, clear = false }, -- shadow, no icon
      { grid = 70, safe = 70, type = 5, shape = 1, flags = 5, visited = 0, clear = false }, -- icon shown
      { grid = 10, safe = 10, type = 7, shape = 1, flags = 0, visited = 0, clear = false }, -- hidden
      { grid = 100, safe = 101, type = 1, shape = 9, flags = 1, visited = 1, clear = true }, -- LTL
      { grid = 300, safe = 300, type = 1, shape = 1, flags = 1, visited = 1, clear = true }, -- off grid
    },
    players = {
      { name = "Isaac", ptype = 0, coins = 7, bombs = 1, keys = 2, items = { [1] = 1, [2] = 1 },
        active = 105, charge = 3, trinkets = { 1, 0 }, card = 0, pill = 0 },
    },
    items = {
      [1] = { Name = "The Sad Onion", Description = "Tears up", GfxFileName = "gfx/items/collectibles/c1.png", Type = 1 },
      [2] = { Name = "The Inner Eye", Description = "#INNER_EYE_DESC", GfxFileName = "gfx/items/collectibles/c2.png", Type = 1 },
      [3] = { Name = "Hidden", Description = "x", GfxFileName = "x.png", Type = 1, Hidden = true },
      [105] = { Name = "The D6", Description = "Reroll", GfxFileName = "d6.png", Type = 3, MaxCharges = 6 },
    },
    maxId = 200,
  }
end

function S.reset()
  S.log, S.callbacks, S.saved, S.time, S.frame = {}, {}, nil, 1000, 0
  S.world = newWorld()
  S.fail = nil
end

local function maybeFail(what)
  if S.fail == what then error("simulated failure in " .. what) end
end

local function descriptor(r)
  return {
    GridIndex = r.grid, SafeGridIndex = r.safe, DisplayFlags = r.flags, VisitedCount = r.visited, Clear = r.clear,
    Data = { Type = r.type, Shape = r.shape },
  }
end

function S.install()
  _G.ModCallbacks = {
    MC_POST_RENDER = "render", MC_POST_UPDATE = "update", MC_POST_GAME_STARTED = "started", MC_PRE_GAME_EXIT = "exit",
  }
  _G.ActiveSlot = { SLOT_PRIMARY = 0, SLOT_POCKET = 2 }
  _G.ItemType = { ITEM_PASSIVE = 1, ITEM_ACTIVE = 3 }
  _G.LevelCurse = { CURSE_OF_THE_LOST = 4 }

  _G.RegisterMod = function()
    local mod = {}
    function mod:AddCallback(id, fn) S.callbacks[id] = fn end
    function mod:SaveData(s) S.saved = s end
    function mod:LoadData() return S.saved end
    function mod:HasData() return S.saved ~= nil end
    return mod
  end

  _G.Isaac = {
    DebugString = function(s) S.log[#S.log + 1] = s end,
    GetTime = function() return S.time end,
    GetPlayer = function(i) return S.players()[i + 1] end,
    GetItemConfig = function()
      local w = S.world
      return {
        GetCollectibles = function() return { Size = w.maxId + 1 } end,
        GetCollectible = function(_, id) return w.items[id] end,
        GetTrinket = function(_, id) return { Name = "Trinket" .. id, Description = "t", GfxFileName = "t.png" } end,
        GetCard = function(_, id) return { Name = "Card" .. id, Description = "c" } end,
        GetPillEffect = function(_, id) return { Name = "Effect" .. id, Description = "e" } end,
      }
    end,
  }

  _G.Game = function()
    maybeFail("Game")
    local w = S.world
    return {
      GetFrameCount = function() return S.frame end,
      GetNumPlayers = function() return #w.players end,
      GetSeeds = function() return { GetStartSeedString = function() return w.seed end } end,
      GetItemPool = function()
        return { IsPillIdentified = function() return S.pillIdentified or false end, GetPillEffect = function() return 9 end }
      end,
      GetLevel = function()
        return {
          GetCurses = function() return w.curses end,
          GetStage = function() return w.stage end,
          GetStageType = function() return w.stageType end,
          GetName = function() return w.name end,
          GetCurrentRoomIndex = function() return w.current end,
          GetRooms = function()
            return {
              Size = #w.rooms,
              Get = function(_, i) return descriptor(w.rooms[i + 1]) end,
            }
          end,
          GetRoomByIdx = function(_, idx)
            for _, r in ipairs(w.rooms) do
              if r.safe == idx then return descriptor(r) end
            end
            return { SafeGridIndex = idx, GridIndex = idx, DisplayFlags = 0, VisitedCount = 0, Clear = false } -- no Data
          end,
        }
      end,
    }
  end

  S.players = function()
    local out = {}
    for _, p in ipairs(S.world.players) do
      out[#out + 1] = {
        GetName = function() return p.name end,
        GetPlayerType = function() return p.ptype end,
        GetNumCoins = function() return p.coins end,
        GetNumBombs = function() return p.bombs end,
        GetNumKeys = function() return p.keys end,
        GetActiveItem = function(_, slot) return slot == 0 and p.active or 0 end,
        GetActiveCharge = function() return p.charge end,
        GetBatteryCharge = function() return 0 end,
        GetTrinket = function(_, i) return p.trinkets[i + 1] or 0 end,
        GetCard = function() return p.card end,
        GetPill = function() return p.pill end,
        GetCollectibleNum = function(_, id) return p.items[id] or 0 end,
        IsCoopGhost = function() return false end,
        Parent = nil,
      }
    end
    return out
  end
end

return S
