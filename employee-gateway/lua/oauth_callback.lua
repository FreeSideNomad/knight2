-- OAuth2 Callback Handler
-- Exchanges authorization code for tokens and creates session

local http = require "resty.http"
local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get query parameters
local args = ngx.req.get_uri_args()
local code = args.code
local state = args.state
local error_code = args.error
local error_description = args.error_description

-- Handle error response from Entra
if error_code then
    utils.log_error("OAuth error: " .. error_code .. " - " .. (error_description or ""))
    ngx.status = 400
    ngx.header["Content-Type"] = "text/html"
    ngx.say([[
        <!DOCTYPE html>
        <html>
        <head><title>Authentication Error</title></head>
        <body>
            <h1>Authentication Error</h1>
            <p>Error: ]] .. error_code .. [[</p>
            <p>]] .. (error_description or "") .. [[</p>
            <p><a href="/">Try again</a></p>
        </body>
        </html>
    ]])
    return ngx.exit(400)
end

-- Validate required parameters
if not code or not state then
    utils.log_error("Missing code or state parameter")
    ngx.status = 400
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Missing required parameters"}')
    return ngx.exit(400)
end

-- Validate and consume OAuth state (CSRF protection)
local state_data, err = redis_client.consume_oauth_state(state)
if not state_data then
    utils.log_error("Invalid or expired OAuth state: " .. (err or "unknown"))
    ngx.status = 400
    ngx.header["Content-Type"] = "text/html"
    ngx.say([[
        <!DOCTYPE html>
        <html>
        <head><title>Session Expired</title></head>
        <body>
            <h1>Session Expired</h1>
            <p>Your login session has expired. Please try again.</p>
            <p><a href="/">Return to application</a></p>
        </body>
        </html>
    ]])
    return ngx.exit(400)
end

-- Exchange authorization code for tokens
local httpc = http.new()
httpc:set_timeout(10000) -- 10 second timeout

local token_params = {
    grant_type = "authorization_code",
    client_id = config.get_entra_client_id(),
    client_secret = config.get_entra_client_secret(),
    code = code,
    redirect_uri = config.get_redirect_uri()
}

local res, err = httpc:request_uri(config.get_token_url(), {
    method = "POST",
    body = utils.build_query_string(token_params),
    headers = {
        ["Content-Type"] = "application/x-www-form-urlencoded"
    },
    ssl_verify = true
})

if not res then
    utils.log_error("Token exchange failed: " .. (err or "unknown"))
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Token exchange failed"}')
    return ngx.exit(500)
end

if res.status ~= 200 then
    utils.log_error("Token exchange returned status " .. res.status .. ": " .. (res.body or ""))
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Token exchange failed"}')
    return ngx.exit(500)
end

-- Parse token response
local token_data = utils.json_decode(res.body)
if not token_data then
    utils.log_error("Failed to parse token response")
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Invalid token response"}')
    return ngx.exit(500)
end

local access_token = token_data.access_token
local id_token = token_data.id_token
local refresh_token = token_data.refresh_token
local expires_in = token_data.expires_in or 3600

-- Use access token for downstream authorization
-- With custom API scope, access token is a validatable JWT
local auth_token = access_token

-- Decode ID token to get user claims
local id_token_claims, err = utils.decode_jwt_payload(id_token)
if not id_token_claims then
    utils.log_error("Failed to decode ID token: " .. (err or "unknown"))
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Invalid ID token"}')
    return ngx.exit(500)
end

-- Validate nonce to prevent replay attacks
if id_token_claims.nonce ~= state_data.nonce then
    utils.log_error("Nonce mismatch - possible replay attack")
    ngx.status = 400
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Invalid nonce"}')
    return ngx.exit(400)
end

-- Extract user information from ID token
local user_id = id_token_claims.oid or id_token_claims.sub
local email = id_token_claims.email or id_token_claims.preferred_username
local name = id_token_claims.name or email

-- Generate session ID
local session_id = utils.random_string(48)
local session_ttl = config.get_session_ttl()

-- Build session data
local session_data = {
    user_id = user_id,
    email = email,
    name = name,
    preferred_username = id_token_claims.preferred_username,
    id_token_claims = id_token_claims,
    access_token = auth_token,  -- Access token for downstream authorization
    refresh_token = refresh_token,
    token_expires_at = utils.now() + expires_in,
    created_at = utils.now(),
    last_activity = utils.now()
}

-- Store session in Redis
local ok, err = redis_client.set_session(session_id, session_data, session_ttl)
if not ok then
    utils.log_error("Failed to create session: " .. (err or "unknown"))
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Failed to create session"}')
    return ngx.exit(500)
end

utils.log_info("User logged in: " .. (email or user_id))

-- Set session cookie
local cookie_name = config.get_session_cookie_name()
local cookie = cookie_name .. "=" .. session_id ..
    "; HttpOnly; SameSite=Lax; Path=/; Max-Age=" .. session_ttl

ngx.header["Set-Cookie"] = cookie

-- Redirect to original return_to URL
local return_to = state_data.return_to or "/"
return ngx.redirect(return_to)
