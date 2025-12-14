-- Utility functions module

local cjson = require "cjson"

local _M = {}

-- Get current Unix timestamp
function _M.now()
    return ngx.time()
end

-- URL encode a string
function _M.url_encode(str)
    if str then
        str = string.gsub(str, "\n", "\r\n")
        str = string.gsub(str, "([^%w %-%_%.%~])",
            function(c)
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
        str = string.gsub(str, "%%(%x%x)",
            function(h)
                return string.char(tonumber(h, 16))
            end)
    end
    return str
end

-- Build query string from table
function _M.build_query_string(params)
    local parts = {}
    for key, value in pairs(params) do
        table.insert(parts, _M.url_encode(key) .. "=" .. _M.url_encode(value))
    end
    return table.concat(parts, "&")
end

-- Parse query string into table
function _M.parse_query_string(query_string)
    local params = {}
    if query_string then
        for key, value in string.gmatch(query_string, "([^&=]+)=([^&]*)") do
            params[_M.url_decode(key)] = _M.url_decode(value)
        end
    end
    return params
end

-- Base64 URL decode (used for JWT)
function _M.base64url_decode(input)
    -- Replace URL-safe characters
    local b64 = input:gsub("-", "+"):gsub("_", "/")

    -- Add padding if needed
    local padding = 4 - (#b64 % 4)
    if padding < 4 then
        b64 = b64 .. string.rep("=", padding)
    end

    return ngx.decode_base64(b64)
end

-- Decode JWT payload (without verification - verification done by backend)
function _M.decode_jwt_payload(token)
    if not token then
        return nil, "No token provided"
    end

    -- Split token into parts
    local parts = {}
    for part in string.gmatch(token, "[^%.]+") do
        table.insert(parts, part)
    end

    if #parts ~= 3 then
        return nil, "Invalid JWT format"
    end

    -- Decode payload (second part)
    local payload_json = _M.base64url_decode(parts[2])
    if not payload_json then
        return nil, "Failed to decode JWT payload"
    end

    local ok, payload = pcall(cjson.decode, payload_json)
    if not ok then
        return nil, "Failed to parse JWT payload JSON"
    end

    return payload
end

-- Generate random hex string
function _M.random_string(length)
    local resty_random = require "resty.random"
    local str = require "resty.string"

    local bytes = resty_random.bytes(length / 2, true)
    if not bytes then
        -- Fallback if strong random not available
        bytes = resty_random.bytes(length / 2, false)
    end

    return str.to_hex(bytes)
end

-- Logging helpers
function _M.log_info(msg)
    ngx.log(ngx.INFO, "[entra-auth] " .. msg)
end

function _M.log_error(msg)
    ngx.log(ngx.ERR, "[entra-auth] " .. msg)
end

function _M.log_debug(msg)
    ngx.log(ngx.DEBUG, "[entra-auth] " .. msg)
end

-- JSON encode helper
function _M.json_encode(data)
    local ok, result = pcall(cjson.encode, data)
    if ok then
        return result
    end
    return nil
end

-- JSON decode helper
function _M.json_decode(str)
    local ok, result = pcall(cjson.decode, str)
    if ok then
        return result
    end
    return nil
end

return _M
