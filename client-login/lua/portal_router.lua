-- portal_router.lua
-- Routes authenticated users to the correct portal based on client type

local redis_client = require "redis_client"
local config = require "config"

-- Get session ID from cookie
local session_id = ngx.var.cookie_CLIENT_SESSION
if not session_id then
    ngx.redirect("/login/")
    return
end

-- Get session from Redis
local session = redis_client.get_session(session_id)
if not session then
    ngx.redirect("/login/")
    return
end

-- Get client type from session (set during OAuth callback)
local client_type = session.client_type or "CLIENT"

-- Set headers for downstream portal
ngx.req.set_header("IV-USER", session.email)
ngx.req.set_header("Authorization", "Bearer " .. session.access_token)
ngx.req.set_header("X-Auth-User-Id", session.user_id)
ngx.req.set_header("X-Auth-User-Email", session.email)
ngx.req.set_header("X-Client-Type", client_type)

-- Route to appropriate portal using named location
ngx.exec("@" .. client_type)
