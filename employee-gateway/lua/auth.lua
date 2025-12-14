-- Session Validation Middleware
-- Validates session and injects headers for downstream

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get session cookie name
local cookie_name = config.get_session_cookie_name()

-- Extract session ID from cookie
local session_id = ngx.var["cookie_" .. cookie_name]

-- If no session cookie, redirect to login
if not session_id or session_id == "" then
    local return_to = ngx.var.request_uri
    utils.log_info("No session cookie, redirecting to login")
    return ngx.redirect("/oauth2/login?return_to=" .. utils.url_encode(return_to))
end

-- Get session from Redis
local session, err = redis_client.get_session(session_id)

-- If session not found or expired, clear cookie and redirect to login
if not session then
    utils.log_info("Session not found or expired: " .. (err or "unknown"))

    -- Clear the invalid cookie
    ngx.header["Set-Cookie"] = cookie_name .. "=; Path=/; HttpOnly; Max-Age=0"

    local return_to = ngx.var.request_uri
    return ngx.redirect("/oauth2/login?return_to=" .. utils.url_encode(return_to))
end

-- Refresh session TTL (sliding window expiration)
local session_ttl = config.get_session_ttl()
local ok, err = redis_client.refresh_session(session_id, session_ttl)
if not ok then
    utils.log_error("Failed to refresh session TTL: " .. (err or "unknown"))
end

-- Update last activity timestamp
session.last_activity = utils.now()
redis_client.update_session(session_id, session, session_ttl)

-- Set headers for downstream application
if session.access_token then
    ngx.req.set_header("Authorization", "Bearer " .. session.access_token)
end

if session.user_id then
    ngx.req.set_header("X-Auth-User-Id", session.user_id)
end

if session.email then
    ngx.req.set_header("X-Auth-User-Email", session.email)
end

if session.name then
    ngx.req.set_header("X-Auth-User-Name", session.name)
end

-- Set truncated session ID for logging/debugging
ngx.req.set_header("X-Auth-Session-Id", string.sub(session_id, 1, 8))

utils.log_debug("Request authenticated for user: " .. (session.email or session.user_id))

-- Continue to proxy_pass (request allowed)
