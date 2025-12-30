-- OAuth2 Callback Handler
-- Handles both:
-- 1. Traditional OAuth code flow (from Auth0 hosted login)
-- 2. Direct token flow (from custom passwordless login page)

local http = require "resty.http"
local cjson = require "cjson"
local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Parse query parameters
local args = ngx.req.get_uri_args()
local code = args.code
local state = args.state
local error_code = args.error
local error_description = args.error_description

-- Check for direct token flow (from custom login page)
local access_token = args.access_token
local id_token = args.id_token
local return_to = args.return_to or "/"

if access_token and id_token then
    -- Direct token flow from custom login page
    utils.log_info("Processing tokens from custom login page")

    -- Decode ID token to get user info
    local id_token_payload, err = utils.decode_jwt_payload(id_token)
    if not id_token_payload then
        utils.log_error("Failed to decode ID token: " .. (err or "unknown"))
        ngx.status = 500
        ngx.say("Failed to process ID token")
        return ngx.exit(500)
    end

    -- Extract user information
    local user_email = id_token_payload.email or id_token_payload.sub
    local user_id = id_token_payload.sub
    local login_id = id_token_payload["https://auth-gateway.local/loginId"] or user_email

    utils.log_info("User authenticated via passwordless: " .. user_email)

    -- Get client type from platform API
    local client_type = "CLIENT"  -- Default
    local platform_api_url = config.get("platform_api_url") or "http://platform:8080"
    local login_api_username = config.get("login_api_username") or "gateway"
    local login_api_password = config.get("login_api_password")

    if login_api_password then
        local check_httpc = http.new()
        check_httpc:set_timeout(5000)

        local check_body = cjson.encode({email = user_email})
        local auth_header = "Basic " .. ngx.encode_base64(login_api_username .. ":" .. login_api_password)

        local check_res, check_err = check_httpc:request_uri(platform_api_url .. "/api/login/user/check", {
            method = "POST",
            body = check_body,
            headers = {
                ["Content-Type"] = "application/json",
                ["Authorization"] = auth_header
            },
            ssl_verify = false
        })

        if check_res and check_res.status == 200 then
            local ok, user_check_data = pcall(cjson.decode, check_res.body)
            if ok and user_check_data.clientType then
                client_type = user_check_data.clientType
                utils.log_info("User " .. user_email .. " has client type: " .. client_type)
            end
        else
            utils.log_error("Failed to check user client type: " .. (check_err or "unknown"))
        end
    else
        utils.log_error("LOGIN_API_PASSWORD not configured, using default client type")
    end

    -- Create session with CSRF token
    local session_id = utils.generate_random_string(48)
    local csrf_token = utils.generate_random_string(32)
    local session_ttl = config.get("session_ttl") or 1200

    local session_data = {
        user_id = user_id,
        email = user_email,
        login_id = login_id,
        name = id_token_payload.name,
        picture = id_token_payload.picture,
        id_token_claims = id_token_payload,
        access_token = access_token,
        csrf_token = csrf_token,
        client_type = client_type,  -- Store client type in session
        created_at = utils.now(),
        last_activity = utils.now()
    }

    local ok, err = redis_client.set_session(session_id, session_data, session_ttl)
    if not ok then
        utils.log_error("Failed to create session: " .. (err or "unknown"))
        ngx.status = 500
        ngx.say("Failed to create session")
        return ngx.exit(500)
    end

    utils.log_info("Session created for " .. user_email .. ", session_id=" .. session_id:sub(1, 8) .. "...")

    -- Set session cookie and CSRF cookie
    local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
    local session_cookie = cookie_name .. "=" .. session_id .. "; Path=/; HttpOnly; SameSite=Lax; Max-Age=1200"
    local csrf_cookie = "CSRF_TOKEN=" .. csrf_token .. "; Path=/; SameSite=Strict; Max-Age=1200"

    ngx.status = 302
    ngx.header["Location"] = return_to
    ngx.header["Set-Cookie"] = {session_cookie, csrf_cookie}
    ngx.header["Cache-Control"] = "no-cache, no-store, must-revalidate"
    ngx.header["Pragma"] = "no-cache"
    ngx.header["Content-Length"] = "0"
    ngx.send_headers()
    return ngx.exit(ngx.HTTP_OK)
end

-- Traditional OAuth code flow below
-- Handle OAuth errors
if error_code then
    utils.log_error("OAuth error: " .. error_code .. " - " .. (error_description or ""))
    ngx.status = 401
    ngx.header["Content-Type"] = "text/html"
    ngx.say("<html><body>")
    ngx.say("<h1>Authentication Failed</h1>")
    ngx.say("<p>Error: " .. error_code .. "</p>")
    ngx.say("<p>" .. (error_description or "") .. "</p>")
    ngx.say("<p><a href='/oauth2/login'>Try again</a></p>")
    ngx.say("</body></html>")
    return ngx.exit(401)
end

-- Validate required parameters
if not code or not state then
    utils.log_error("Missing code or state in callback")
    ngx.status = 400
    ngx.say("Bad request: missing code or state")
    return ngx.exit(400)
end

-- Validate state (CSRF protection) - consume it immediately (one-time use)
local state_data, err = redis_client.consume_oauth_state(state)
if not state_data then
    utils.log_error("Invalid or expired state: " .. (err or "unknown"))
    ngx.status = 400
    ngx.say("Bad request: invalid or expired state")
    return ngx.exit(400)
end

utils.log_info("Valid state received, exchanging code for tokens...")

-- Get configuration
local auth0_urls = config.get_auth0_urls()
local client_id = config.get("auth0_client_id")
local client_secret = config.get("auth0_client_secret")
local callback_url = "http://" .. ngx.var.http_host .. "/oauth2/callback"

-- Exchange code for tokens
local httpc = http.new()
httpc:set_timeout(10000)

local token_body = utils.build_query_string({
    grant_type = "authorization_code",
    client_id = client_id,
    client_secret = client_secret,
    code = code,
    redirect_uri = callback_url
})

local res, err = httpc:request_uri(auth0_urls.token, {
    method = "POST",
    body = token_body,
    headers = {
        ["Content-Type"] = "application/x-www-form-urlencoded"
    },
    ssl_verify = false  -- TODO: Configure proper CA certs for production
})

if not res then
    utils.log_error("Token request failed: " .. (err or "unknown"))
    ngx.status = 500
    ngx.say("Failed to exchange code for tokens")
    return ngx.exit(500)
end

if res.status ~= 200 then
    utils.log_error("Token request returned " .. res.status .. ": " .. res.body)
    ngx.status = 401
    ngx.say("Failed to authenticate")
    return ngx.exit(401)
end

-- Parse token response
local ok, token_data = pcall(cjson.decode, res.body)
if not ok then
    utils.log_error("Failed to parse token response")
    ngx.status = 500
    ngx.say("Internal server error")
    return ngx.exit(500)
end

local id_token = token_data.id_token
local access_token = token_data.access_token

if not id_token then
    utils.log_error("No ID token in response")
    ngx.status = 500
    ngx.say("No ID token received")
    return ngx.exit(500)
end

-- Decode ID token to get user info
local id_token_payload, err = utils.decode_jwt_payload(id_token)
if not id_token_payload then
    utils.log_error("Failed to decode ID token: " .. (err or "unknown"))
    ngx.status = 500
    ngx.say("Failed to process ID token")
    return ngx.exit(500)
end

-- Validate nonce
if id_token_payload.nonce ~= state_data.nonce then
    utils.log_error("Nonce mismatch - possible replay attack")
    ngx.status = 401
    ngx.say("Authentication failed: nonce mismatch")
    return ngx.exit(401)
end

-- Extract user information
local user_email = id_token_payload.email or id_token_payload.sub
local user_id = id_token_payload.sub
local login_id = id_token_payload["https://auth-gateway.local/loginId"] or user_email

utils.log_info("User authenticated: " .. user_email)

-- Get client type from platform API
local client_type = "CLIENT"  -- Default
local platform_api_url = config.get("platform_api_url") or "http://platform:8080"
local login_api_username = config.get("login_api_username") or "gateway"
local login_api_password = config.get("login_api_password")

if login_api_password then
    local check_httpc = http.new()
    check_httpc:set_timeout(5000)

    local check_body = cjson.encode({email = user_email})
    local auth_header = "Basic " .. ngx.encode_base64(login_api_username .. ":" .. login_api_password)

    local check_res, check_err = check_httpc:request_uri(platform_api_url .. "/api/login/user/check", {
        method = "POST",
        body = check_body,
        headers = {
            ["Content-Type"] = "application/json",
            ["Authorization"] = auth_header
        },
        ssl_verify = false
    })

    if check_res and check_res.status == 200 then
        local ok, user_check_data = pcall(cjson.decode, check_res.body)
        if ok and user_check_data.clientType then
            client_type = user_check_data.clientType
            utils.log_info("User " .. user_email .. " has client type: " .. client_type)
        end
    else
        utils.log_error("Failed to check user client type: " .. (check_err or "unknown"))
    end
else
    utils.log_error("LOGIN_API_PASSWORD not configured, using default client type")
end

-- Create session with CSRF token
local session_id = utils.generate_random_string(48)
local csrf_token = utils.generate_random_string(32)
local session_ttl = config.get("session_ttl") or 1200

local session_data = {
    user_id = user_id,
    email = user_email,
    login_id = login_id,
    name = id_token_payload.name,
    picture = id_token_payload.picture,
    id_token_claims = id_token_payload,
    access_token = access_token,
    csrf_token = csrf_token,
    client_type = client_type,  -- Store client type in session
    created_at = utils.now(),
    last_activity = utils.now()
}

local ok, err = redis_client.set_session(session_id, session_data, session_ttl)
if not ok then
    utils.log_error("Failed to create session: " .. (err or "unknown"))
    ngx.status = 500
    ngx.say("Failed to create session")
    return ngx.exit(500)
end

utils.log_info("Session created for " .. user_email .. ", session_id=" .. session_id:sub(1, 8) .. "...")

-- Set session cookie and CSRF cookie
local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
local session_cookie = cookie_name .. "=" .. session_id .. "; Path=/; HttpOnly; SameSite=Lax; Max-Age=1200"
local csrf_cookie = "CSRF_TOKEN=" .. csrf_token .. "; Path=/; SameSite=Strict; Max-Age=1200"

-- Redirect to original URI - manually construct redirect to ensure cookie is sent
local redirect_uri = state_data.redirect_uri or "/"

ngx.status = 302
ngx.header["Location"] = redirect_uri
ngx.header["Set-Cookie"] = {session_cookie, csrf_cookie}
ngx.header["Cache-Control"] = "no-cache, no-store, must-revalidate"
ngx.header["Pragma"] = "no-cache"
ngx.header["Content-Length"] = "0"
ngx.send_headers()
return ngx.exit(ngx.HTTP_OK)
