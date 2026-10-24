-- Minimal JSON for the test harness only (the game provides its own json.lua).
local M = {}

local function enc(v)
  local t = type(v)
  if t == "table" then
    local out = {}
    if #v > 0 or next(v) == nil then
      for i = 1, #v do out[i] = enc(v[i]) end
      return "[" .. table.concat(out, ",") .. "]"
    end
    for k, x in pairs(v) do out[#out + 1] = enc(tostring(k)) .. ":" .. enc(x) end
    return "{" .. table.concat(out, ",") .. "}"
  elseif t == "string" then
    return '"' .. v:gsub('[%c"\\]', function(c) return string.format("\\u%04x", c:byte()) end) .. '"'
  end
  return tostring(v)
end
M.encode = enc

function M.decode(s)
  local pos = 1
  local function ws() pos = s:find("%S", pos) or #s + 1 end
  local value
  local function str()
    local out = {}
    pos = pos + 1
    while true do
      local c = s:sub(pos, pos)
      if c == '"' then pos = pos + 1; return table.concat(out) end
      if c == "\\" then
        local n = s:sub(pos + 1, pos + 1)
        if n == "u" then
          out[#out + 1] = utf8.char(tonumber(s:sub(pos + 2, pos + 5), 16)); pos = pos + 6
        else
          out[#out + 1] = ({ n = "\n", r = "\r", t = "\t" })[n] or n; pos = pos + 2
        end
      elseif c == "" then
        error("unterminated string")
      else
        out[#out + 1] = c; pos = pos + 1
      end
    end
  end
  function value()
    ws()
    local c = s:sub(pos, pos)
    if c == "{" then
      local t = {}
      pos = pos + 1; ws()
      if s:sub(pos, pos) == "}" then pos = pos + 1; return t end
      while true do
        ws(); local k = str(); ws()
        assert(s:sub(pos, pos) == ":", "expected ':' at " .. pos); pos = pos + 1
        t[k] = value(); ws()
        local d = s:sub(pos, pos); pos = pos + 1
        if d == "}" then return t end
        assert(d == ",", "expected ',' at " .. pos)
      end
    elseif c == "[" then
      local t = {}
      pos = pos + 1; ws()
      if s:sub(pos, pos) == "]" then pos = pos + 1; return t end
      while true do
        t[#t + 1] = value(); ws()
        local d = s:sub(pos, pos); pos = pos + 1
        if d == "]" then return t end
        assert(d == ",", "expected ',' at " .. pos)
      end
    elseif c == '"' then
      return str()
    elseif s:sub(pos, pos + 3) == "true" then pos = pos + 4; return true
    elseif s:sub(pos, pos + 4) == "false" then pos = pos + 5; return false
    elseif s:sub(pos, pos + 3) == "null" then pos = pos + 4; return nil
    end
    local num = s:match("^-?%d+%.?%d*[eE]?[+-]?%d*", pos)
    assert(num and #num > 0, "bad json at " .. pos)
    pos = pos + #num
    return tonumber(num)
  end
  local v = value()
  ws()
  assert(pos > #s, "trailing data")
  return v
end

return M
