-- Mod tests: lua5.3 tests/run.lua   (needs luasocket; run from the repository root)
package.path = "./tests/?.lua;" .. package.path
local S = require("isaac_stub")
local json = require("json")
local realSocket = require("socket")
package.loaded.json = json

local failures, passed = 0, 0
local function test(name, fn)
  S.reset()
  S.install()
  local ok, err = xpcall(fn, debug.traceback)
  if ok then
    passed = passed + 1
    print("ok    " .. name)
  else
    failures = failures + 1
    print("FAIL  " .. name .. "\n" .. tostring(err))
  end
  -- release the port for the next test
  if _G.__IsaacCompanionServer then pcall(function() _G.__IsaacCompanionServer:close() end) end
  if _G.__IsaacCompanionClient then pcall(function() _G.__IsaacCompanionClient:close() end) end
  _G.__IsaacCompanionServer, _G.__IsaacCompanionClient = nil, nil
end

local function loadMod()
  S.callbacks = {}
  assert(loadfile("mod/isaac-companion/main.lua"))()
end

-- one game frame: update every 10th frame, render every frame
local function frame(n)
  for _ = 1, n or 1 do
    S.frame = S.frame + 1
    S.time = S.time + 16
    if S.callbacks.update then S.callbacks.update() end
    if S.callbacks.render then S.callbacks.render() end
  end
end

local function connect()
  local c = assert(realSocket.tcp())
  c:settimeout(1)
  assert(c:connect("127.0.0.1", 47777))
  c:settimeout(0)
  return c
end

-- pump frames and collect complete lines from the client until `want(lines)` or frames run out
local function readLines(c, want, maxFrames)
  local lines, buf = {}, ""
  for _ = 1, maxFrames or 60 do
    frame(1)
    local data, err, partial = c:receive(65536)
    data = data or partial
    if data and #data > 0 then buf = buf .. data end
    for l in buf:gmatch("([^\n]*)\n") do lines[#lines + 1] = l end
    buf = buf:match("[^\n]*$")
    if want(lines) then break end
    if err == "closed" then break end
  end
  return lines
end

local function parse(lines)
  local out = {}
  for _, l in ipairs(lines) do out[#out + 1] = json.decode(l) end
  return out
end

local function ofType(msgs, t)
  local r = {}
  for _, m in ipairs(msgs) do if m.type == t then r[#r + 1] = m end end
  return r
end

local function hasType(t)
  return function(lines)
    for _, l in ipairs(lines) do if l:find('"type":"' .. t .. '"', 1, true) then return true end end
    return false
  end
end

test("without socket the mod loads silently and does nothing", function()
  local saved = package.loaded.socket
  package.loaded.socket = nil
  package.preload.socket = function() error("module 'socket' not found") end
  local ok = pcall(loadMod)
  package.preload.socket = nil
  package.loaded.socket = saved
  assert(ok, "loading must not raise")
  assert(next(S.callbacks) == nil, "no callback registered")
  assert(#S.log == 1 and S.log[1]:find("socket unavailable"), "exactly one warning line")
end)

test("hello, then itemdb, then state on connect", function()
  loadMod()
  local c = connect()
  local lines = readLines(c, hasType("state"))
  local msgs = parse(lines)
  assert(msgs[1].type == "hello" and msgs[1].v == 1, "hello first")
  local db, st = ofType(msgs, "itemdb")[1], ofType(msgs, "state")[1]
  assert(db and st, "itemdb and state received")
  local dbIndex, stIndex
  for i, m in ipairs(msgs) do
    if m.type == "itemdb" and not dbIndex then dbIndex = i end
    if m.type == "state" and not stIndex then stIndex = i end
  end
  assert(dbIndex < stIndex, "itemdb before the state that references it")
  local names = {}
  for _, e in ipairs(db.items) do names[e.kind .. e.id] = e.name end
  assert(names.c1 == "The Sad Onion" and names.c105 == "The D6" and names.t1, "entries for items, active, trinket")
  assert(st.meta.levelName == "Caves I" and st.meta.stage == 3)
  assert(st.players[1].coins == 7 and st.players[1].active.id == 105 and st.players[1].active.maxCharge == 6)
  assert(#st.players[1].items == 2, "hidden and active items excluded")
  c:close()
end)

test("map anti-spoil and grid rules", function()
  loadMod()
  local c = connect()
  local st = ofType(parse(readLines(c, hasType("state"))), "state")[1]
  local byGrid = {}
  for _, r in ipairs(st.map.rooms) do byGrid[r.gridIndex] = r end
  assert(byGrid[84] and byGrid[84].current and byGrid[84].type == 1, "current room, type known")
  assert(byGrid[82] and byGrid[82].type == -1, "shadow room without icon: type withheld")
  assert(byGrid[70] and byGrid[70].type == 5, "icon flag: type sent")
  assert(byGrid[10] == nil, "room with no display flags and not visited is not sent")
  assert(byGrid[300] == nil, "room outside 0..168 dropped")
  assert(byGrid[100] and byGrid[100].safeGridIndex == 101, "L room keeps GridIndex and SafeGridIndex")
  assert(st.map.currentRoom == 84 and st.map.mapHidden == false)
  c:close()
end)

test("curse of the lost: only the current room", function()
  S.world.curses = 4
  loadMod()
  local c = connect()
  local st = ofType(parse(readLines(c, hasType("state"))), "state")[1]
  assert(st.map.mapHidden == true and #st.map.rooms == 1 and st.map.rooms[1].current)
  c:close()
end)

test("current room outside the grid gives -1", function()
  S.world.current = -1
  loadMod()
  local c = connect()
  local st = ofType(parse(readLines(c, hasType("state"))), "state")[1]
  assert(st.map.currentRoom == -1)
  c:close()
end)

test("a state is only sent when it changes; ping fills the gaps", function()
  loadMod()
  local c = connect()
  local first = readLines(c, hasType("state"))
  assert(#ofType(parse(first), "state") == 1)
  local more = readLines(c, function() return false end, 40) -- ~640 ms, several collections
  assert(#ofType(parse(more), "state") == 0, "unchanged state is not resent")
  S.time = S.time + 2500
  local pings = readLines(c, hasType("ping"), 5)
  assert(#ofType(parse(pings), "ping") >= 1, "heartbeat after 2 s of silence")
  S.world.players[1].coins = 8
  local changed = readLines(c, hasType("state"), 40)
  assert(ofType(parse(changed), "state")[1].players[1].coins == 8)
  c:close()
end)

test("pickup order follows pickups and survives a continue", function()
  loadMod()
  S.callbacks.started(nil, false) -- the game always fires this before any update
  local c = connect()
  readLines(c, hasType("state"))
  S.world.players[1].items = { [2] = 1 }
  readLines(c, function() return false end, 12)
  S.world.players[1].items = { [2] = 1, [1] = 1 }
  local st = ofType(parse(readLines(c, hasType("state"), 40)), "state")
  local order = st[#st].players[1].pickupOrder
  assert(order[1] == 2 and order[2] == 1, "2 then 1, got " .. json.encode(order))
  assert(S.saved, "order persisted")
  c:close()
  _G.__IsaacCompanionServer:close()
  _G.__IsaacCompanionServer = nil
  -- new mod load = game restart, "Continue" with the same seed
  loadMod()
  S.callbacks.started(nil, true)
  c = connect()
  local st2 = ofType(parse(readLines(c, hasType("state"))), "state")[1]
  local o2 = st2.players[1].pickupOrder
  assert(o2[1] == 2 and o2[2] == 1, "order restored, got " .. json.encode(o2))
  c:close()
end)

test("a new run resets the order", function()
  loadMod()
  S.world.players[1].items = { [2] = 1 }
  S.callbacks.started(nil, false)
  local c = connect()
  local st = ofType(parse(readLines(c, hasType("state"))), "state")[1]
  assert(#st.players[1].pickupOrder == 1)
  c:close()
end)

test("API failures never escape the callbacks", function()
  loadMod()
  S.fail = "Game"
  S.frame = 9
  frame(30)
  assert(#S.log >= 2, "errors are logged")
  S.fail = nil
  local c = connect()
  assert(#readLines(c, hasType("state")) > 0, "recovers once the API works again")
  c:close()
end)

test("a slow client never blocks the game and gets the latest state", function()
  loadMod()
  for id = 4, 190 do
    S.world.items[id] = { Name = ("Item %d "):format(id):rep(20), Description = ("d"):rep(300), GfxFileName = "g.png", Type = 1 }
  end
  local c = connect() -- never reads while the game runs
  local worst = 0
  for n = 1, 600 do
    S.world.players[1].items[(n % 180) + 4] = 1
    local t0 = os.clock()
    frame(1)
    worst = math.max(worst, os.clock() - t0)
  end
  assert(worst < 0.25, "frame took too long: " .. worst)
  S.world.players[1].coins = 42
  local lines = readLines(c, function(l)
    local m = parse({ l[#l] or '{"type":"x"}' })[1]
    return m.type == "state" and m.players[1].coins == 42
  end, 4000)
  local st = ofType(parse(lines), "state")
  assert(st[#st].players[1].coins == 42, "the latest state arrives once the client reads")
  c:close()
end)

test("a new client replaces the old one and gets the full state", function()
  loadMod()
  local a = connect()
  readLines(a, hasType("state"))
  local b = connect()
  local msgs = parse(readLines(b, hasType("state")))
  assert(msgs[1].type == "hello" and #ofType(msgs, "state") == 1 and #ofType(msgs, "itemdb") == 1)
  a:close()
  b:close()
end)

test("client disconnect is survived, a reconnect works", function()
  loadMod()
  local a = connect()
  readLines(a, hasType("state"))
  a:close()
  frame(200)
  local b = connect()
  assert(#ofType(parse(readLines(b, hasType("state"))), "state") == 1)
  b:close()
end)

test("mod reload releases the port", function()
  loadMod()
  loadMod()
  local c = connect()
  assert(#ofType(parse(readLines(c, hasType("hello"))), "hello") == 1)
  c:close()
end)

test("idle on game exit, and idle for a client that connects with no run", function()
  loadMod()
  local c = connect()
  readLines(c, hasType("state"))
  S.callbacks.exit()
  local msgs = parse(readLines(c, hasType("idle"), 10))
  assert(#ofType(msgs, "idle") == 1)
  local d = connect()
  local m2 = parse(readLines(d, hasType("idle"), 20))
  assert(#ofType(m2, "idle") == 1 and #ofType(m2, "state") == 0)
  c:close()
  d:close()
end)

test("pill identification resends its itemdb entry", function()
  S.world.players[1].pill = 3
  loadMod()
  local c = connect()
  local db = ofType(parse(readLines(c, hasType("state"))), "itemdb")[1]
  local pill
  for _, e in ipairs(db.items) do if e.kind == "p" then pill = e end end
  assert(pill and pill.name == nil, "unidentified pill has no name")
  S.pillIdentified = true
  local more = parse(readLines(c, hasType("itemdb"), 40))
  S.pillIdentified = nil
  local again = ofType(more, "itemdb")[1]
  assert(again and again.items[1].kind == "p" and again.items[1].name == "Effect9")
  c:close()
end)

print(("\n%d passed, %d failed"):format(passed, failures))
os.exit(failures == 0 and 0 or 1)
