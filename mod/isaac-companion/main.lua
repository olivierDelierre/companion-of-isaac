-- Isaac Companion: exposes the level map and inventory over a local TCP socket.
-- Protocol: docs/protocol.md (NDJSON, the mod is the server, the app the client).
--
-- Rules this file follows:
--   * runs in the game thread: no blocking call, socket timeouts are always 0;
--   * never crashes the game: network and collection code only run under pcall;
--   * without --luadebug (no `socket`) the mod logs one line and does nothing.

local VERSION = "0.1.0"
local PROTOCOL = 1
local HOST = "127.0.0.1"
local PORT = 47777

local COLLECT_EVERY = 10 -- game frames between snapshots (~3 per second)
local PING_EVERY_MS = 2000
local REBIND_EVERY_MS = 5000
local MAX_GRID_INDEX = 168
local MAX_EID_LENGTH = 600

-- RoomDescriptor.DisplayFlags bit that shows the room icon on the minimap (1 = shape, 2 = shadow;
-- to verify in game). Without it the room type is withheld from the app.
local DISPLAY_ICON = 4

local mod = RegisterMod("Isaac Companion", 1)

local function log(msg)
  pcall(Isaac.DebugString, "[IsaacCompanion] " .. tostring(msg))
end

local okSocket, socket = pcall(require, "socket")
if not okSocket or type(socket) ~= "table" then
  log("socket unavailable (missing --luadebug?), mod disabled")
  return
end
local okJson, json = pcall(require, "json")
if not okJson then
  json = nil
end

---------------------------------------------------------------------------
-- JSON encoder: fixed key order (sorted) so that equal states give equal
-- strings, and explicit arrays so an empty table is never ambiguous.
---------------------------------------------------------------------------

local ARRAY = {}

local function list(t)
  return setmetatable(t or {}, ARRAY)
end

local ESCAPES = { ['"'] = '\\"', ["\\"] = "\\\\", ["\n"] = "\\n", ["\r"] = "\\r", ["\t"] = "\\t" }

local function escapeChar(c)
  return ESCAPES[c] or string.format("\\u%04x", string.byte(c))
end

local function encode(v)
  local t = type(v)
  if t == "nil" then
    return "null"
  elseif t == "boolean" then
    return v and "true" or "false"
  elseif t == "number" then
    if v ~= v or v == math.huge or v == -math.huge then
      return "0"
    end
    if v == math.floor(v) and v > -9007199254740992 and v < 9007199254740992 then
      return string.format("%d", v)
    end
    return string.format("%.4g", v)
  elseif t == "string" then
    return '"' .. v:gsub('[%c"\\]', escapeChar) .. '"'
  elseif t == "table" then
    local out = {}
    if getmetatable(v) == ARRAY then
      for i = 1, #v do
        out[i] = encode(v[i])
      end
      return "[" .. table.concat(out, ",") .. "]"
    end
    local keys = {}
    for k in pairs(v) do
      keys[#keys + 1] = k
    end
    table.sort(keys)
    for _, k in ipairs(keys) do
      out[#out + 1] = encode(tostring(k)) .. ":" .. encode(v[k])
    end
    return "{" .. table.concat(out, ",") .. "}"
  end
  return "null"
end

---------------------------------------------------------------------------
-- Safe wrappers
---------------------------------------------------------------------------

local lastError, lastErrorAt = nil, 0

local function reportError(where, err)
  local now = Isaac.GetTime()
  local msg = where .. ": " .. tostring(err)
  if msg ~= lastError or now - lastErrorAt > 5000 then
    lastError, lastErrorAt = msg, now
    log(msg)
  end
end

local function safe(where, fn)
  return function(...)
    local ok, err = pcall(fn, ...)
    if not ok then
      reportError(where, err)
    end
  end
end

---------------------------------------------------------------------------
-- Network
---------------------------------------------------------------------------

-- Mod reload (luamod): release the sockets of the previous load first.
if _G.__IsaacCompanionServer then
  pcall(function() _G.__IsaacCompanionServer:close() end)
  _G.__IsaacCompanionServer = nil
end
if _G.__IsaacCompanionClient then
  pcall(function() _G.__IsaacCompanionClient:close() end)
  _G.__IsaacCompanionClient = nil
end

local server, client
local nextBindAt = 0
local sendBuf, sendPos = "", 1
local lastSentAt = 0
local clientKnown = {} -- itemdb entries this client already has: key -> signature
local pending -- latest snapshot not yet queued: { line =, needed = }
local lastState -- latest snapshot ever produced: { line =, needed = }, or nil when idle
local itemdbBuilder -- assigned below

local function dropClient()
  if client then
    pcall(function() client:close() end)
  end
  client = nil
  _G.__IsaacCompanionClient = nil
  sendBuf, sendPos = "", 1
  clientKnown = {}
end

local function buffered()
  return sendPos <= #sendBuf
end

local function queue(data)
  if not buffered() then
    sendBuf, sendPos = data, 1
  else
    sendBuf = sendBuf:sub(sendPos) .. data
    sendPos = 1
  end
end

local function queueState(snap)
  local db = itemdbBuilder(snap.needed)
  if db then
    queue(db .. "\n")
  end
  queue(snap.line .. "\n")
end

local function bind()
  -- socket.bind sets SO_REUSEADDR before binding and starts listening (backlog 1)
  local s, err = socket.bind(HOST, PORT, 1)
  if not s then
    error("bind failed: " .. tostring(err))
  end
  s:settimeout(0)
  server = s
  _G.__IsaacCompanionServer = s
  log("listening on " .. HOST .. ":" .. PORT)
end

local function accept()
  local c = server:accept()
  if not c then
    return
  end
  c:settimeout(0)
  dropClient()
  client = c
  _G.__IsaacCompanionClient = c
  log("client connected")
  queue(encode({ type = "hello", v = PROTOCOL, mod = VERSION, eid = EID ~= nil }) .. "\n")
  if lastState then
    queueState(lastState)
  else
    queue(encode({ type = "idle", v = PROTOCOL }) .. "\n")
  end
end

local function flush()
  if not client or not buffered() then
    return
  end
  local sent, err, partial = client:send(sendBuf, sendPos)
  if sent then
    sendPos = sent + 1
    lastSentAt = Isaac.GetTime()
  elseif err == "timeout" then
    if partial and partial >= sendPos then
      sendPos = partial + 1
      lastSentAt = Isaac.GetTime()
    end
  else
    log("send failed: " .. tostring(err))
    dropClient()
  end
end

local function pump()
  local now = Isaac.GetTime()
  if not server then
    if now >= nextBindAt then
      nextBindAt = now + REBIND_EVERY_MS
      local ok, err = pcall(bind)
      if not ok then
        reportError("bind", err)
      end
    end
    return
  end
  accept()
  if client and not buffered() then
    if pending then
      queueState(pending)
      pending = nil
    elseif now - lastSentAt >= PING_EVERY_MS then
      queue(encode({ type = "ping", v = PROTOCOL }) .. "\n")
    end
  end
  flush()
end

---------------------------------------------------------------------------
-- Item metadata (itemdb messages)
---------------------------------------------------------------------------

local entryCache = {}

local function str(v)
  if v == nil then
    return nil
  end
  return tostring(v)
end

local function eidText(variant, subtype)
  if EID == nil or type(EID.getDescriptionObj) ~= "function" then
    return nil
  end
  local ok, obj = pcall(EID.getDescriptionObj, EID, 5, variant, subtype)
  if ok and type(obj) == "table" and type(obj.Description) == "string" then
    return obj.Description:sub(1, MAX_EID_LENGTH)
  end
  return nil
end

local function describe(kind, id)
  local cfg = Isaac.GetItemConfig()
  local e = { kind = kind, id = id }
  if kind == "c" then
    local item = cfg:GetCollectible(id)
    if item then
      e.name, e.desc, e.gfx = str(item.Name), str(item.Description), str(item.GfxFileName)
      e.active = item.Type == ItemType.ITEM_ACTIVE
    end
    e.eid = eidText(100, id)
  elseif kind == "t" then
    local item = cfg:GetTrinket(id)
    if item then
      e.name, e.desc, e.gfx = str(item.Name), str(item.Description), str(item.GfxFileName)
    end
    e.eid = eidText(350, id)
  elseif kind == "k" then
    local card = cfg:GetCard(id)
    if card then
      e.name, e.desc = str(card.Name), str(card.Description)
    end
    e.eid = eidText(300, id)
  elseif kind == "p" then
    -- name only once the pill is identified, otherwise the app shows "???"
    local pool = Game():GetItemPool()
    if pool:IsPillIdentified(id) then
      local effect = cfg:GetPillEffect(pool:GetPillEffect(id, Isaac.GetPlayer(0)))
      if effect then
        e.name = str(effect.Name)
        e.desc = str(effect.Description)
      end
    end
  end
  return e
end

local function entryFor(kind, id)
  local key = kind .. ":" .. id
  if kind == "p" then
    return key, describe(kind, id) -- identification can change: never cached
  end
  local e = entryCache[key]
  if not e then
    e = describe(kind, id)
    entryCache[key] = e
  end
  return key, e
end

-- Returns the itemdb line for the entries the current client does not have yet, or nil.
itemdbBuilder = function(needed)
  local fresh = list()
  for _, ref in ipairs(needed) do
    local ok, key, e = pcall(entryFor, ref[1], ref[2])
    if ok then
      local sig = encode(e)
      if clientKnown[key] ~= sig then
        clientKnown[key] = sig
        fresh[#fresh + 1] = e
      end
    else
      reportError("itemdb", key)
    end
  end
  if #fresh == 0 then
    return nil
  end
  return encode({ type = "itemdb", v = PROTOCOL, items = fresh })
end

---------------------------------------------------------------------------
-- Pickup order (reconstructed, persisted per seed)
---------------------------------------------------------------------------

local pickupOrder = {} -- [playerIndex] = { id, ... }
local orderSeed = nil
local orderDirty = false

local function saveOrder()
  if not json or not orderDirty then
    return
  end
  orderDirty = false
  local data = { seed = orderSeed, players = {} }
  for i, ids in pairs(pickupOrder) do
    data.players[tostring(i)] = ids
  end
  local ok, err = pcall(function() mod:SaveData(json.encode(data)) end)
  if not ok then
    reportError("SaveData", err)
  end
end

local function loadOrder(seed)
  pickupOrder, orderSeed, orderDirty = {}, seed, false
  if not json then
    return
  end
  local ok, data = pcall(function()
    if mod:HasData() then
      return json.decode(mod:LoadData())
    end
  end)
  if ok and type(data) == "table" and data.seed == seed and type(data.players) == "table" then
    for k, ids in pairs(data.players) do
      local idx = tonumber(k)
      if idx and type(ids) == "table" then
        pickupOrder[idx] = ids
      end
    end
  end
end

-- Keeps the list in sync with what the player owns: new ids go last, lost ids disappear.
local function updateOrder(playerIndex, owned)
  local ids = pickupOrder[playerIndex] or {}
  local kept, seen = {}, {}
  for _, id in ipairs(ids) do
    if owned[id] and not seen[id] then
      kept[#kept + 1] = id
      seen[id] = true
    end
  end
  local fresh = {}
  for id in pairs(owned) do
    if not seen[id] then
      fresh[#fresh + 1] = id
    end
  end
  table.sort(fresh)
  for _, id in ipairs(fresh) do
    kept[#kept + 1] = id
  end
  if #fresh > 0 or #kept ~= #ids then
    orderDirty = true
  end
  pickupOrder[playerIndex] = kept
  return kept
end

---------------------------------------------------------------------------
-- Snapshot collection
---------------------------------------------------------------------------

local function collectMap(level, hidden)
  local rooms = list()
  local currentIndex = level:GetCurrentRoomIndex()
  local currentGrid = -1
  local descs = level:GetRooms()
  for i = 0, descs.Size - 1 do
    local listed = descs:Get(i)
    local desc = listed and level:GetRoomByIdx(listed.SafeGridIndex) or nil
    -- GetRoomByIdx may return a descriptor with no data for slots that are not rooms
    if desc and desc.Data and desc.SafeGridIndex >= 0 and desc.SafeGridIndex <= MAX_GRID_INDEX then
      local visited = desc.VisitedCount > 0
      local flags = desc.DisplayFlags
      local isCurrent = desc.SafeGridIndex == currentIndex
      if isCurrent then
        currentGrid = desc.GridIndex
      end
      if (flags > 0 or visited) and (not hidden or isCurrent) then
        local known = visited or (flags & DISPLAY_ICON) ~= 0
        rooms[#rooms + 1] = {
          gridIndex = desc.GridIndex,
          safeGridIndex = desc.SafeGridIndex,
          type = known and desc.Data.Type or -1,
          shape = desc.Data.Shape,
          displayFlags = flags,
          visited = visited,
          clear = desc.Clear,
          current = isCurrent,
        }
      end
    end
  end
  table.sort(rooms, function(a, b) return a.gridIndex < b.gridIndex end)
  return { currentRoom = currentGrid, mapHidden = hidden, rooms = rooms }
end

local function slot(player, slotId)
  local id = player:GetActiveItem(slotId)
  local out = { id = id, charge = 0, maxCharge = 0, battery = 0 }
  if id > 0 then
    out.charge = player:GetActiveCharge(slotId)
    out.battery = player:GetBatteryCharge(slotId)
    local item = Isaac.GetItemConfig():GetCollectible(id)
    out.maxCharge = item and item.MaxCharges or 0
  end
  return out
end

local function addNeeded(needed, kind, id)
  if id and id > 0 then
    needed[#needed + 1] = { kind, id }
  end
end

local function collectPlayer(index, needed)
  local player = Isaac.GetPlayer(index)
  local cfg = Isaac.GetItemConfig()
  local owned = {}
  local maxId = cfg:GetCollectibles().Size - 1
  for id = 1, maxId do
    local item = cfg:GetCollectible(id)
    -- actives have their own slot; hidden items are internal
    if item and not item.Hidden and item.Type ~= ItemType.ITEM_ACTIVE and player:GetCollectibleNum(id, true) > 0 then
      owned[id] = player:GetCollectibleNum(id, true)
    end
  end
  local order = updateOrder(index, owned)
  local items = list()
  for _, id in ipairs(order) do
    items[#items + 1] = { id = id, count = owned[id] }
    addNeeded(needed, "c", id)
  end

  local active, pocket = slot(player, ActiveSlot.SLOT_PRIMARY), slot(player, ActiveSlot.SLOT_POCKET)
  local trinkets = list({ player:GetTrinket(0), player:GetTrinket(1) })
  local card, pill = player:GetCard(0), player:GetPill(0)
  addNeeded(needed, "c", active.id)
  addNeeded(needed, "c", pocket.id)
  addNeeded(needed, "t", trinkets[1])
  addNeeded(needed, "t", trinkets[2])
  addNeeded(needed, "k", card)
  addNeeded(needed, "p", pill)

  local isChild = false
  pcall(function() isChild = player.Parent ~= nil end)
  local isGhost = false
  pcall(function() isGhost = player:IsCoopGhost() end)

  return {
    index = index,
    name = player:GetName(),
    playerType = player:GetPlayerType(),
    isChild = isChild,
    isCoopGhost = isGhost,
    coins = player:GetNumCoins(),
    bombs = player:GetNumBombs(),
    keys = player:GetNumKeys(),
    active = active,
    pocketActive = pocket,
    trinkets = trinkets,
    card = card,
    pill = pill,
    pillIdentified = pill > 0 and Game():GetItemPool():IsPillIdentified(pill),
    items = items,
    pickupOrder = list(order),
  }
end

local function collect()
  local game = Game()
  local level = game:GetLevel()
  local curses = level:GetCurses()
  local hidden = (curses & LevelCurse.CURSE_OF_THE_LOST) ~= 0
  local needed = {}
  local players = list()
  for i = 0, game:GetNumPlayers() - 1 do
    players[#players + 1] = collectPlayer(i, needed)
  end
  local state = {
    type = "state",
    v = PROTOCOL,
    meta = {
      stage = level:GetStage(),
      stageType = level:GetStageType(),
      levelName = level:GetName(),
      curses = curses,
      seed = game:GetSeeds():GetStartSeedString(),
    },
    map = collectMap(level, hidden),
    players = players,
  }
  return { line = encode(state), needed = needed }
end

local function publish(snap)
  if lastState and lastState.line == snap.line then
    return
  end
  lastState = snap
  pending = snap
  saveOrder()
end

---------------------------------------------------------------------------
-- Callbacks
---------------------------------------------------------------------------

mod:AddCallback(ModCallbacks.MC_POST_GAME_STARTED, safe("gameStarted", function(_, isContinued)
  local seed = Game():GetSeeds():GetStartSeedString()
  if isContinued then
    loadOrder(seed)
  else
    pickupOrder, orderSeed, orderDirty = {}, seed, true
  end
  lastState, pending = nil, nil
end))

mod:AddCallback(ModCallbacks.MC_POST_UPDATE, safe("update", function()
  if Game():GetFrameCount() % COLLECT_EVERY ~= 0 then
    return
  end
  publish(collect())
end))

mod:AddCallback(ModCallbacks.MC_POST_RENDER, safe("render", pump))

mod:AddCallback(ModCallbacks.MC_PRE_GAME_EXIT, safe("gameExit", function()
  saveOrder()
  lastState, pending = nil, nil
  if client then
    queue(encode({ type = "idle", v = PROTOCOL }) .. "\n")
    flush()
  end
end))

do -- bind at load time; on failure `pump` retries every few seconds
  local ok, err = pcall(bind)
  if not ok then
    nextBindAt = Isaac.GetTime() + REBIND_EVERY_MS
    reportError("bind", err)
  end
end

log("loaded v" .. VERSION)
