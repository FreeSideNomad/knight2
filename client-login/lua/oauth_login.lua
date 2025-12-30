-- OAuth2 Login Initiation
-- Routes users based on their status:
-- - New users: Custom /login page for onboarding (email OTP → password → MFA enrollment)
-- - Returning users: Auth0 Universal Login (SSO session → MFA, or Password → MFA)

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Check if using custom login page for new users
local use_custom_login = ngx.shared.sessions:get("config_use_custom_login")

-- Get the return_to parameter
local args = ngx.req.get_uri_args()
local original_uri = args.return_to

-- If no return_to parameter, default to /
if not original_uri or original_uri == "" then
    original_uri = "/"
end

utils.log_info("Login requested, return_to: " .. original_uri)

-- Check for force_universal parameter (skip custom login)
local force_universal = args.force_universal == "true"

-- Get login_hint for returning users (pre-fill email in Auth0)
local login_hint = args.login_hint

if use_custom_login and not force_universal then
    -- Redirect to custom login page for new user onboarding
    -- The custom page will check if user exists and redirect to Universal Login if needed
    local login_url = "/login/?return_to=" .. ngx.escape_uri(original_uri)
    utils.log_info("Redirecting to custom login page")
    return ngx.redirect(login_url)
end

-- Auth0 Universal Login flow
-- Used for returning users with password + MFA
local auth0_urls = config.get_auth0_urls()
local client_id = config.get("auth0_client_id")
local session_cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"

-- Generate state and nonce for CSRF and replay protection
local state = utils.generate_random_string(32)
local nonce = utils.generate_random_string(32)

-- Store OAuth state in Redis
local state_data = {
    nonce = nonce,
    redirect_uri = original_uri,
    created_at = utils.now()
}

local ok, err = redis_client.set_oauth_state(state, state_data, 300)
if not ok then
    utils.log_error("Failed to store OAuth state: " .. (err or "unknown"))
    ngx.status = 500
    ngx.say("Internal server error")
    return ngx.exit(500)
end

-- Build authorization URL (use http_host to include port)
local callback_url = "http://" .. ngx.var.http_host .. "/oauth2/callback"

-- Get audience from config (stored in shared memory)
local api_audience = config.get("auth0_api_audience") or ""
utils.log_info("Using API audience: " .. (api_audience ~= "" and api_audience or "(none)"))

local auth_params = {
    response_type = "code",
    client_id = client_id,
    redirect_uri = callback_url,
    scope = "openid profile email",
    state = state,
    nonce = nonce,
    connection = "Username-Password-Authentication"  -- Force database connection
}

-- Add audience if configured
if api_audience ~= "" then
    auth_params.audience = api_audience
end

-- Add login_hint if provided (pre-fills email in Auth0 login)
if login_hint and login_hint ~= "" then
    auth_params.login_hint = login_hint
    utils.log_info("Using login_hint: " .. login_hint)
end

local auth_url = auth0_urls.authorize .. "?" .. utils.build_query_string(auth_params)

utils.log_info("Redirecting to Auth0 Universal Login, state=" .. state:sub(1, 8) .. "...")

-- Redirect to Auth0
ngx.redirect(auth_url)
