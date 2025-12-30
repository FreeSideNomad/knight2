-- Encoding module for HMAC-based token signing
-- Based on Lapis encoding pattern

local _M = {}
local cjson = require "cjson"
local resty_random = require "resty.random"
local resty_string = require "resty.string"

-- Get the CSRF secret from environment or use a default (should be configured in production)
local function get_secret()
    return os.getenv("CSRF_SECRET") or "change-this-secret-in-production"
end

-- Generate cryptographically secure random bytes
function _M.random_bytes(length)
    length = length or 32
    return resty_random.bytes(length, true)
end

-- Encode object with HMAC signature
-- Format: base64(json) .. "." .. base64(hmac_sha1(base64(json), secret))
function _M.encode_with_secret(object, secret)
    secret = secret or get_secret()

    local json_str = cjson.encode(object)
    local encoded = ngx.encode_base64(json_str)

    -- Create HMAC signature
    local signature = ngx.hmac_sha1(secret, encoded)
    local encoded_sig = ngx.encode_base64(signature)

    return encoded .. "." .. encoded_sig
end

-- Decode object and verify HMAC signature
function _M.decode_with_secret(encoded_string, secret)
    secret = secret or get_secret()

    if not encoded_string or encoded_string == "" then
        return nil, "Empty token"
    end

    -- Split into payload and signature
    local dot_pos = string.find(encoded_string, ".", 1, true)
    if not dot_pos then
        return nil, "Invalid token format"
    end

    local encoded_payload = string.sub(encoded_string, 1, dot_pos - 1)
    local encoded_sig = string.sub(encoded_string, dot_pos + 1)

    -- Verify signature
    local expected_sig = ngx.encode_base64(ngx.hmac_sha1(secret, encoded_payload))

    -- Constant-time comparison to prevent timing attacks
    if #expected_sig ~= #encoded_sig then
        return nil, "Invalid signature"
    end

    local match = true
    for i = 1, #expected_sig do
        if string.byte(expected_sig, i) ~= string.byte(encoded_sig, i) then
            match = false
        end
    end

    if not match then
        return nil, "Invalid signature"
    end

    -- Decode payload
    local json_str = ngx.decode_base64(encoded_payload)
    if not json_str then
        return nil, "Invalid base64 encoding"
    end

    local ok, object = pcall(cjson.decode, json_str)
    if not ok then
        return nil, "Invalid JSON"
    end

    return object
end

return _M
