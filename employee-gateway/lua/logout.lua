-- Logout Handler
-- Clears session and redirects to Microsoft Entra logout

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get session cookie name
local cookie_name = config.get_session_cookie_name()

-- Extract session ID from cookie
local session_id = ngx.var["cookie_" .. cookie_name]

-- Delete session from Redis if exists
if session_id and session_id ~= "" then
    local session = redis_client.get_session(session_id)
    if session then
        utils.log_info("Logging out user: " .. (session.email or session.user_id or "unknown"))
    end

    redis_client.delete_session(session_id)
end

-- Clear session cookie
ngx.header["Set-Cookie"] = cookie_name .. "=; Path=/; HttpOnly; Max-Age=0"

-- Build Entra logout URL
-- The post_logout_redirect_uri must be registered in Entra app registration
local scheme = ngx.var.scheme or "http"
local host = ngx.var.host
local post_logout_uri = scheme .. "://" .. host .. "/"

local logout_params = {
    post_logout_redirect_uri = post_logout_uri
}

local logout_url = config.get_logout_url() .. "?" .. utils.build_query_string(logout_params)

utils.log_info("Redirecting to Entra logout")

-- Redirect to Microsoft Entra logout
return ngx.redirect(logout_url)
