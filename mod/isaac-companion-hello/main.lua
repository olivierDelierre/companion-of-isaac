-- Isaac Companion: step 0, bridge feasibility test.
-- Binds 127.0.0.1:PORT and sends "hello from isaac\n" to whoever connects.
-- Needs the game to run with --luadebug, otherwise require("socket") fails
-- and the mod silently does nothing.

local PORT = 47777
local RETRY_FRAMES = 300

local mod = RegisterMod("Isaac Companion Hello", 1)

local okSocket, socket = pcall(require, "socket")
if not okSocket or type(socket) ~= "table" then
  Isaac.DebugString("[IsaacCompanionHello] socket unavailable (missing --luadebug?), mod disabled")
  return
end

-- Mod reload (luamod): close the previous server before binding again.
if _G.__IsaacCompanionHelloServer then
  pcall(function() _G.__IsaacCompanionHelloServer:close() end)
  _G.__IsaacCompanionHelloServer = nil
end

local server
local clients = {}
local nextBind = 0
local frame = 0

local function tryBind()
  -- socket.bind sets SO_REUSEADDR before binding and starts listening (backlog 1)
  local s, err = socket.bind("127.0.0.1", PORT, 1)
  if not s then
    Isaac.DebugString("[IsaacCompanionHello] bind failed: " .. tostring(err))
    return
  end
  s:settimeout(0)
  server = s
  _G.__IsaacCompanionHelloServer = s
  Isaac.DebugString("[IsaacCompanionHello] listening on 127.0.0.1:" .. PORT)
end

local function onRender()
  frame = frame + 1
  local ok, err = pcall(function()
    if not server then
      if frame >= nextBind then
        nextBind = frame + RETRY_FRAMES
        tryBind()
      end
      return
    end
    local c = server:accept()
    if c then
      c:settimeout(0)
      c:send("hello from isaac\n")
      clients[#clients + 1] = c -- kept open so nc stays connected
      Isaac.DebugString("[IsaacCompanionHello] client connected")
    end
  end)
  if not ok then
    Isaac.DebugString("[IsaacCompanionHello] error: " .. tostring(err))
  end
end

mod:AddCallback(ModCallbacks.MC_POST_RENDER, onRender)
