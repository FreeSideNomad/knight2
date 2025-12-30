-- Logout Handler
-- Clears local session and redirects to Auth0 logout

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get session cookie
local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
local session_id = ngx.var["cookie_" .. cookie_name]

-- Delete session from Redis if exists
if session_id and session_id ~= "" then
    local session = redis_client.get_session(session_id)
    if session then
        utils.log_info("Logging out user: " .. (session.email or "unknown"))
    end
    redis_client.delete_session(session_id)
end

-- Clear the session cookie
ngx.header["Set-Cookie"] = cookie_name .. "=; Path=/; HttpOnly; Max-Age=0"

-- Build Auth0 logout URL
local auth0_urls = config.get_auth0_urls()
local client_id = config.get("auth0_client_id")
local return_to = "http://" .. ngx.var.http_host .. "/"

local logout_params = {
    client_id = client_id,
    returnTo = return_to
}

local logout_url = auth0_urls.logout .. "?" .. utils.build_query_string(logout_params)

utils.log_info("Redirecting to Auth0 logout")

-- Redirect to Auth0 logout
ngx.redirect(logout_url)
