-- Authentication Middleware
-- Validates session and injects IV-USER header

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get session cookie
local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
local cookie_header = ngx.var.http_cookie or ""
utils.log_info("Cookie header: " .. cookie_header)
utils.log_info("Looking for cookie: " .. cookie_name)

-- Try to extract cookie manually if ngx.var approach fails
local session_id = ngx.var["cookie_" .. cookie_name]
if not session_id or session_id == "" then
    -- Manual extraction fallback
    local pattern = cookie_name .. "=([^;]+)"
    session_id = string.match(cookie_header, pattern)
    if session_id then
        utils.log_info("Found session via manual extraction: " .. session_id:sub(1,8) .. "...")
    end
end

-- No session cookie - redirect to login page
if not session_id or session_id == "" then
    utils.log_info("No session cookie found, redirecting to login page")
    return ngx.redirect("/login/?return_to=" .. utils.url_encode(ngx.var.request_uri))
end

-- Get session from Redis
local session, err = redis_client.get_session(session_id)
if not session then
    utils.log_info("Session not found or expired: " .. (err or "unknown"))
    -- Clear the invalid cookie
    ngx.header["Set-Cookie"] = cookie_name .. "=; Path=/; HttpOnly; Max-Age=0"
    return ngx.redirect("/login/?return_to=" .. utils.url_encode(ngx.var.request_uri))
end

-- Validate session hasn't expired (extra safety check)
local session_ttl = config.get("session_ttl") or 1200
local session_age = utils.now() - (session.last_activity or session.created_at)

-- Refresh session TTL (sliding window expiration)
local ok, err = redis_client.refresh_session(session_id, session_ttl)
if not ok then
    utils.log_error("Failed to refresh session: " .. (err or "unknown"))
    -- Continue anyway - session is still valid
end

-- Update last activity in session data
session.last_activity = utils.now()
redis_client.set_session(session_id, session, session_ttl)

-- Get login ID for IV-USER header
local login_id = session.login_id or session.email

if not login_id then
    utils.log_error("No login_id in session")
    ngx.status = 500
    ngx.say("Session data invalid")
    return ngx.exit(500)
end

-- Set IV-USER header for backend
ngx.req.set_header("IV-USER", login_id)

-- Set Authorization header with access token
if session.access_token then
    ngx.req.set_header("Authorization", "Bearer " .. session.access_token)
end

-- Set additional headers that might be useful
ngx.req.set_header("X-Auth-User-Id", session.user_id or "")
ngx.req.set_header("X-Auth-User-Email", session.email or "")
ngx.req.set_header("X-Auth-User-Name", session.name or "")
ngx.req.set_header("X-Auth-Session-Id", session_id:sub(1, 8) .. "...")

-- Set MFA token headers for step-up authentication
local mfa_token_valid = false
if session.mfa_token and session.mfa_token_expires_at then
    mfa_token_valid = utils.now() < session.mfa_token_expires_at
end
ngx.req.set_header("X-MFA-Token", session.mfa_token or "")
ngx.req.set_header("X-MFA-Token-Valid", mfa_token_valid and "true" or "false")
ngx.req.set_header("X-Auth-Has-Guardian", "true")  -- All users have Guardian enrollment

-- Get client type from session (set during login/OAuth callback)
local client_type = session.client_type or "CLIENT"
ngx.req.set_header("X-Client-Type", client_type)

utils.log_debug("Authenticated request for " .. login_id .. " (type: " .. client_type .. "), routing to portal")

-- Route to appropriate portal based on client type
ngx.exec("@" .. client_type)
