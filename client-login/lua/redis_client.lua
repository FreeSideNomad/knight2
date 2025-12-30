-- Redis client module for session storage
local redis = require "resty.redis"
local config = require "config"

local _M = {}

-- Get a Redis connection
function _M.connect()
    local red = redis:new()
    red:set_timeout(1000) -- 1 second timeout

    local host = config.get("redis_host") or "redis"
    local port = tonumber(config.get("redis_port")) or 6379

    local ok, err = red:connect(host, port)
    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to connect: ", err)
        return nil, err
    end

    return red
end

-- Return connection to pool
function _M.keepalive(red)
    if red then
        -- Put connection back in pool (max idle time: 10s, pool size: 100)
        local ok, err = red:set_keepalive(10000, 100)
        if not ok then
            ngx.log(ngx.WARN, "[redis] Failed to set keepalive: ", err)
        end
    end
end

-- Store session data
function _M.set_session(session_id, data, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local cjson = require "cjson"
    local json_data = cjson.encode(data)

    ttl = ttl or config.get("session_ttl") or 1200

    local ok, err = red:setex("session:" .. session_id, ttl, json_data)
    _M.keepalive(red)

    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to set session: ", err)
        return nil, err
    end

    return true
end

-- Get session data
function _M.get_session(session_id)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local data, err = red:get("session:" .. session_id)
    if not data or data == ngx.null then
        _M.keepalive(red)
        return nil, "Session not found"
    end

    _M.keepalive(red)

    local cjson = require "cjson"
    local ok, session = pcall(cjson.decode, data)
    if not ok then
        return nil, "Failed to decode session"
    end

    return session
end

-- Refresh session TTL (sliding window expiration)
function _M.refresh_session(session_id, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    ttl = ttl or config.get("session_ttl") or 1200

    local ok, err = red:expire("session:" .. session_id, ttl)
    _M.keepalive(red)

    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to refresh session: ", err)
        return nil, err
    end

    return true
end

-- Delete session
function _M.delete_session(session_id)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local ok, err = red:del("session:" .. session_id)
    _M.keepalive(red)

    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to delete session: ", err)
        return nil, err
    end

    return true
end

-- Store OAuth state (short-lived for CSRF protection)
function _M.set_oauth_state(state, data, ttl)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local cjson = require "cjson"
    local json_data = cjson.encode(data)

    ttl = ttl or 300 -- 5 minutes for OAuth flow

    local ok, err = red:setex("oauth_state:" .. state, ttl, json_data)
    _M.keepalive(red)

    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to set OAuth state: ", err)
        return nil, err
    end

    return true
end

-- Get OAuth state without deleting (for retryable operations)
function _M.get_oauth_state(state)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    -- Get the state data (don't delete - allow retries)
    local data, err = red:get("oauth_state:" .. state)
    if not data or data == ngx.null then
        _M.keepalive(red)
        return nil, "State not found or expired"
    end

    _M.keepalive(red)

    local cjson = require "cjson"
    local ok, state_data = pcall(cjson.decode, data)
    if not ok then
        return nil, "Failed to decode state"
    end

    return state_data
end

-- Get and delete OAuth state (one-time use, for callbacks)
function _M.consume_oauth_state(state)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    -- Get the state data
    local data, err = red:get("oauth_state:" .. state)
    if not data or data == ngx.null then
        _M.keepalive(red)
        return nil, "State not found or expired"
    end

    -- Delete it immediately (one-time use)
    red:del("oauth_state:" .. state)
    _M.keepalive(red)

    local cjson = require "cjson"
    local ok, state_data = pcall(cjson.decode, data)
    if not ok then
        return nil, "Failed to decode state"
    end

    return state_data
end

-- Delete OAuth state
function _M.delete_oauth_state(state)
    local red, err = _M.connect()
    if not red then
        return nil, err
    end

    local ok, err = red:del("oauth_state:" .. state)
    _M.keepalive(red)

    if not ok then
        ngx.log(ngx.ERR, "[redis] Failed to delete OAuth state: ", err)
        return nil, err
    end

    return true
end

return _M
