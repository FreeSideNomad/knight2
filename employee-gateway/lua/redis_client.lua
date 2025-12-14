-- Redis client module for session management

local redis = require "resty.redis"
local cjson = require "cjson"
local config = require "config"
local utils = require "utils"

local _M = {}

-- Connect to Redis
function _M.connect()
    local red = redis:new()
    red:set_timeout(1000) -- 1 second timeout

    local host = config.get_redis_host()
    local port = config.get_redis_port()

    local ok, err = red:connect(host, port)
    if not ok then
        utils.log_error("Failed to connect to Redis: " .. (err or "unknown"))
        return nil, err
    end

    return red
end

-- Return connection to pool
function _M.keepalive(red)
    if red then
        local ok, err = red:set_keepalive(10000, 100) -- 10s idle, 100 pool size
        if not ok then
            utils.log_error("Failed to set Redis keepalive: " .. (err or "unknown"))
        end
    end
end

-- Store session in Redis
function _M.set_session(session_id, data, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    ttl = ttl or config.get_session_ttl()
    local key = "session:" .. session_id
    local json_data = cjson.encode(data)

    local ok, err = red:setex(key, ttl, json_data)
    _M.keepalive(red)

    if not ok then
        utils.log_error("Failed to set session: " .. (err or "unknown"))
        return nil, err
    end

    utils.log_info("Session created: " .. string.sub(session_id, 1, 8) .. "...")
    return true
end

-- Get session from Redis
function _M.get_session(session_id)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local key = "session:" .. session_id
    local json_data, err = red:get(key)
    _M.keepalive(red)

    if not json_data or json_data == ngx.null then
        return nil, "Session not found"
    end

    local ok, data = pcall(cjson.decode, json_data)
    if not ok then
        utils.log_error("Failed to decode session data")
        return nil, "Invalid session data"
    end

    return data
end

-- Refresh session TTL (sliding window)
function _M.refresh_session(session_id, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    ttl = ttl or config.get_session_ttl()
    local key = "session:" .. session_id

    local ok, err = red:expire(key, ttl)
    _M.keepalive(red)

    if not ok then
        utils.log_error("Failed to refresh session TTL: " .. (err or "unknown"))
        return nil, err
    end

    return true
end

-- Update session data
function _M.update_session(session_id, data, ttl)
    return _M.set_session(session_id, data, ttl)
end

-- Delete session from Redis
function _M.delete_session(session_id)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local key = "session:" .. session_id
    local ok, err = red:del(key)
    _M.keepalive(red)

    if not ok then
        utils.log_error("Failed to delete session: " .. (err or "unknown"))
        return nil, err
    end

    utils.log_info("Session deleted: " .. string.sub(session_id, 1, 8) .. "...")
    return true
end

-- Store OAuth state in Redis (short-lived)
function _M.set_oauth_state(state, data, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    ttl = ttl or 300 -- 5 minutes default
    local key = "oauth_state:" .. state
    local json_data = cjson.encode(data)

    local ok, err = red:setex(key, ttl, json_data)
    _M.keepalive(red)

    if not ok then
        utils.log_error("Failed to set OAuth state: " .. (err or "unknown"))
        return nil, err
    end

    utils.log_debug("OAuth state stored: " .. string.sub(state, 1, 8) .. "...")
    return true
end

-- Get and delete OAuth state (one-time use)
function _M.consume_oauth_state(state)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local key = "oauth_state:" .. state

    -- Get the state data
    local json_data, err = red:get(key)
    if not json_data or json_data == ngx.null then
        _M.keepalive(red)
        return nil, "OAuth state not found or expired"
    end

    -- Delete the state (one-time use)
    red:del(key)
    _M.keepalive(red)

    local ok, data = pcall(cjson.decode, json_data)
    if not ok then
        utils.log_error("Failed to decode OAuth state data")
        return nil, "Invalid OAuth state data"
    end

    utils.log_debug("OAuth state consumed: " .. string.sub(state, 1, 8) .. "...")
    return data
end

return _M
