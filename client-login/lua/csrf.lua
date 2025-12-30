-- CSRF Protection Module
-- Lapis-style implementation with HMAC-signed tokens
-- Based on https://github.com/leafo/lapis/blob/master/lapis/csrf.lua

local _M = {}
local encoding = require "encoding"
local utils = require "utils"

-- Cookie names
local LOGIN_CSRF_COOKIE = "LOGIN_CSRF_KEY"
local SESSION_CSRF_COOKIE = "SESSION_CSRF_KEY"

-- Token expiration (in seconds)
local LOGIN_TOKEN_TTL = 300    -- 5 minutes for login flow
local SESSION_TOKEN_TTL = 1200  -- 20 minutes for authenticated sessions

-- Generate a new CSRF token for login flow (pre-authentication)
-- This creates a signed token that can be validated without server-side state
function _M.generate_login_token()
    -- Get or create the random key from cookie
    local key = ngx.var["cookie_" .. LOGIN_CSRF_COOKIE]

    if not key or key == "" then
        -- Generate new random key
        local random_bytes = encoding.random_bytes(32)
        if not random_bytes then
            utils.log_error("Failed to generate random bytes for CSRF key")
            return nil, "Failed to generate random key"
        end
        key = ngx.encode_base64(random_bytes)
    end

    -- Create token with key, timestamp and type
    local token_data = {
        k = key,
        t = ngx.time(),
        type = "login"
    }

    local token = encoding.encode_with_secret(token_data)
    if not token then
        utils.log_error("Failed to encode CSRF token")
        return nil, "Failed to encode token"
    end

    return token, key
end

-- Validate login CSRF token (pre-authentication)
function _M.validate_login()
    -- Skip for safe methods (read-only)
    local method = ngx.req.get_method()
    if method == "GET" or method == "HEAD" or method == "OPTIONS" then
        return true
    end

    -- Get CSRF token from header
    local header_token = ngx.req.get_headers()["X-Login-CSRF-Token"]
    if not header_token then
        utils.log_error("Login CSRF validation failed: Missing X-Login-CSRF-Token header")
        return false, "Missing X-Login-CSRF-Token header"
    end

    -- Get the key from cookie
    local cookie_key = ngx.var["cookie_" .. LOGIN_CSRF_COOKIE]
    if not cookie_key or cookie_key == "" then
        utils.log_error("Login CSRF validation failed: Missing CSRF cookie")
        return false, "Missing CSRF cookie"
    end

    -- Decode and verify the token
    local token_data, err = encoding.decode_with_secret(header_token)
    if not token_data then
        utils.log_error("Login CSRF validation failed: " .. (err or "Invalid token"))
        return false, "Invalid CSRF token"
    end

    -- Verify the key matches
    if token_data.k ~= cookie_key then
        utils.log_error("Login CSRF validation failed: Key mismatch")
        return false, "CSRF key mismatch"
    end

    -- Verify token type
    if token_data.type ~= "login" then
        utils.log_error("Login CSRF validation failed: Wrong token type")
        return false, "Wrong token type"
    end

    -- Check expiration
    if token_data.t and (ngx.time() - token_data.t) > LOGIN_TOKEN_TTL then
        utils.log_error("Login CSRF validation failed: Token expired")
        return false, "CSRF token expired"
    end

    utils.log_info("Login CSRF validation successful")
    return true
end

-- Generate a new CSRF token for authenticated sessions
function _M.generate_session_token()
    -- Get or create the random key from cookie
    local key = ngx.var["cookie_" .. SESSION_CSRF_COOKIE]

    if not key or key == "" then
        -- Generate new random key
        local random_bytes = encoding.random_bytes(32)
        if not random_bytes then
            utils.log_error("Failed to generate random bytes for session CSRF key")
            return nil, "Failed to generate random key"
        end
        key = ngx.encode_base64(random_bytes)
    end

    -- Create token with key, timestamp and type
    local token_data = {
        k = key,
        t = ngx.time(),
        type = "session"
    }

    local token = encoding.encode_with_secret(token_data)
    if not token then
        utils.log_error("Failed to encode session CSRF token")
        return nil, "Failed to encode token"
    end

    return token, key
end

-- Validate session CSRF token (authenticated requests)
function _M.validate()
    -- Skip for safe methods (read-only)
    local method = ngx.req.get_method()
    if method == "GET" or method == "HEAD" or method == "OPTIONS" then
        return true
    end

    -- Get CSRF token from header
    local header_token = ngx.req.get_headers()["X-CSRF-Token"]
    if not header_token then
        utils.log_error("CSRF validation failed: Missing X-CSRF-Token header")
        return false, "Missing X-CSRF-Token header"
    end

    -- Get the key from cookie
    local cookie_key = ngx.var["cookie_" .. SESSION_CSRF_COOKIE]
    if not cookie_key or cookie_key == "" then
        utils.log_error("CSRF validation failed: Missing session CSRF cookie")
        return false, "Missing CSRF cookie"
    end

    -- Decode and verify the token
    local token_data, err = encoding.decode_with_secret(header_token)
    if not token_data then
        utils.log_error("CSRF validation failed: " .. (err or "Invalid token"))
        return false, "Invalid CSRF token"
    end

    -- Verify the key matches
    if token_data.k ~= cookie_key then
        utils.log_error("CSRF validation failed: Key mismatch")
        return false, "CSRF key mismatch"
    end

    -- Verify token type
    if token_data.type ~= "session" then
        utils.log_error("CSRF validation failed: Wrong token type")
        return false, "Wrong token type"
    end

    -- Check expiration
    if token_data.t and (ngx.time() - token_data.t) > SESSION_TOKEN_TTL then
        utils.log_error("CSRF validation failed: Token expired")
        return false, "CSRF token expired"
    end

    utils.log_info("CSRF validation successful")
    return true
end

-- Legacy function for backward compatibility (generates simple random token)
function _M.generate_token()
    local random_bytes = encoding.random_bytes(32)
    if random_bytes then
        return ngx.encode_base64(random_bytes)
    end
    return utils.generate_random_string(32)
end

return _M
