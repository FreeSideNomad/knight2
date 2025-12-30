-- Utility functions for Auth Gateway
local _M = {}

-- Generate a random string for state/nonce
function _M.generate_random_string(length)
    length = length or 32
    local chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    local result = {}

    for i = 1, length do
        local idx = math.random(1, #chars)
        result[i] = chars:sub(idx, idx)
    end

    return table.concat(result)
end

-- URL encode a string
function _M.url_encode(str)
    if str then
        str = string.gsub(str, "\n", "\r\n")
        str = string.gsub(str, "([^%w%-%.%_%~ ])", function(c)
            return string.format("%%%02X", string.byte(c))
        end)
        str = string.gsub(str, " ", "+")
    end
    return str
end

-- URL decode a string
function _M.url_decode(str)
    if str then
        str = string.gsub(str, "+", " ")
        str = string.gsub(str, "%%(%x%x)", function(h)
            return string.char(tonumber(h, 16))
        end)
    end
    return str
end

-- Parse query string into table
function _M.parse_query_string(query)
    local result = {}
    if not query then return result end

    for pair in string.gmatch(query, "[^&]+") do
        local key, value = string.match(pair, "([^=]+)=?(.*)")
        if key then
            result[_M.url_decode(key)] = _M.url_decode(value or "")
        end
    end

    return result
end

-- Build query string from table
function _M.build_query_string(params)
    local parts = {}
    for key, value in pairs(params) do
        table.insert(parts, _M.url_encode(key) .. "=" .. _M.url_encode(tostring(value)))
    end
    return table.concat(parts, "&")
end

-- Base64 URL encode (for JWT)
function _M.base64url_encode(input)
    local b64 = ngx.encode_base64(input)
    -- Convert to URL-safe variant
    b64 = b64:gsub("+", "-"):gsub("/", "_"):gsub("=", "")
    return b64
end

-- Base64 URL decode (for JWT)
function _M.base64url_decode(input)
    -- Convert from URL-safe variant
    local b64 = input:gsub("-", "+"):gsub("_", "/")
    -- Add padding if needed
    local pad = #b64 % 4
    if pad > 0 then
        b64 = b64 .. string.rep("=", 4 - pad)
    end
    return ngx.decode_base64(b64)
end

-- Decode JWT payload (without verification - verification done separately)
function _M.decode_jwt_payload(token)
    if not token then return nil end

    local parts = {}
    for part in string.gmatch(token, "[^%.]+") do
        table.insert(parts, part)
    end

    if #parts ~= 3 then
        return nil, "Invalid JWT format"
    end

    local payload_json = _M.base64url_decode(parts[2])
    if not payload_json then
        return nil, "Failed to decode payload"
    end

    local cjson = require "cjson"
    local ok, payload = pcall(cjson.decode, payload_json)
    if not ok then
        return nil, "Failed to parse payload JSON"
    end

    return payload
end

-- Get current timestamp
function _M.now()
    return ngx.time()
end

-- Log helper
function _M.log(level, msg)
    ngx.log(level, "[auth-gateway] " .. msg)
end

function _M.log_info(msg)
    _M.log(ngx.INFO, msg)
end

function _M.log_error(msg)
    _M.log(ngx.ERR, msg)
end

function _M.log_debug(msg)
    _M.log(ngx.DEBUG, msg)
end

return _M
