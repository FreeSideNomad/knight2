-- API Proxy with Session Creation
-- Wraps Java API calls and creates sessions when tokens are returned

local http = require "resty.http"
local cjson = require "cjson"
local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"
local csrf = require "csrf"

local _M = {}

-- Get Basic Auth header for platform login API
local function get_basic_auth_header()
    return config.get_auth_service_basic_auth()
end

-- Forward request to platform login API
function _M.proxy_request(path, body)
    local httpc = http.new()
    httpc:set_timeout(30000)

    -- API endpoints are at /api/login/* but path already includes /api prefix
    -- So /api/mfa/verify -> http://platform:8080/api/login/mfa/verify
    local platform_url = config.get("platform_api_url") or "http://platform:8080"
    local auth_service_url = platform_url .. "/api/login" .. path:gsub("^/api", "")
    local auth_header = get_basic_auth_header()
    utils.log_info("proxy_request: url=" .. auth_service_url .. " auth=" .. (auth_header and auth_header:sub(1, 20) .. "..." or "nil"))

    local res, err = httpc:request_uri(auth_service_url, {
        method = ngx.req.get_method(),
        body = body,
        headers = {
            ["Content-Type"] = "application/json",
            ["Authorization"] = auth_header
        }
    })

    if not res then
        return nil, err
    end

    return res
end

-- Create session from tokens and return session_id and csrf_token
function _M.create_session_from_tokens(access_token, id_token, email, mfa_token, mfa_token_expires_at, client_type)
    -- Decode ID token to get user info
    local id_token_payload, err = utils.decode_jwt_payload(id_token)
    if not id_token_payload then
        utils.log_error("Failed to decode ID token: " .. (err or "unknown"))
        return nil, nil, "Failed to decode ID token"
    end

    -- Extract user information
    local user_email = email or id_token_payload.email or id_token_payload.sub
    local user_id = id_token_payload.sub
    local login_id = id_token_payload["https://auth-gateway.local/loginId"] or user_email

    utils.log_info("Creating session for user: " .. user_email .. " (type: " .. (client_type or "CLIENT") .. ")")

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
        mfa_token = mfa_token,
        mfa_token_expires_at = mfa_token_expires_at,
        csrf_token = csrf_token,
        client_type = client_type or "CLIENT",  -- Store portal type in session
        created_at = utils.now(),
        last_activity = utils.now()
    }

    local ok, err = redis_client.set_session(session_id, session_data, session_ttl)
    if not ok then
        utils.log_error("Failed to create session: " .. (err or "unknown"))
        return nil, nil, "Failed to create session"
    end

    utils.log_info("Session created for " .. user_email .. ", session_id=" .. session_id:sub(1, 8) .. "..., mfa_token=" .. (mfa_token and "present" or "nil"))
    return session_id, csrf_token
end

-- Set session and CSRF cookies in response headers
-- Clears pre-session CSRF cookie and sets session CSRF key for Lapis-style tokens
function _M.set_session_cookies(session_id, csrf_token)
    local encoding = require "encoding"

    local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
    local session_ttl = config.get("session_ttl") or 1200

    local session_cookie = cookie_name .. "=" .. session_id .. "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" .. session_ttl
    -- Legacy CSRF cookie (for backward compatibility)
    local csrf_cookie = "CSRF_TOKEN=" .. csrf_token .. "; Path=/; SameSite=Strict; Max-Age=" .. session_ttl
    -- Clear the login CSRF key cookie (login flow is complete)
    local clear_login_csrf = "LOGIN_CSRF_KEY=; Path=/; Max-Age=0"
    -- Generate session CSRF key for Lapis-style tokens
    local session_csrf_key = ngx.encode_base64(encoding.random_bytes(32))
    local session_csrf_cookie = "SESSION_CSRF_KEY=" .. session_csrf_key .. "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" .. session_ttl

    ngx.header["Set-Cookie"] = {session_cookie, csrf_cookie, clear_login_csrf, session_csrf_cookie}
end

-- Handle MFA challenge endpoint (Guardian enrollment polling)
function _M.handle_mfa_challenge()
    -- Read request body first
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    local res, err = _M.proxy_request("/api/mfa/challenge", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If success with tokens, create session
    if data.success and data.access_token and data.id_token then
        local session_id, csrf_token, err = _M.create_session_from_tokens(
            data.access_token,
            data.id_token,
            req_data.email,
            data.mfa_token,
            data.mfa_token_expires_at,
            data.client_type  -- Portal type from platform response
        )

        if session_id then
            _M.set_session_cookies(session_id, csrf_token)
            ngx.status = 200
            ngx.header["Content-Type"] = "application/json"
            ngx.say(cjson.encode({
                authenticated = true,
                session_id = session_id
            }))
            return ngx.exit(200)
        end
    end

    -- Return original response (pending, error, etc.)
    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle MFA verify endpoint (OTP enrollment)
function _M.handle_mfa_verify()
    -- Read request body first
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    local res, err = _M.proxy_request("/api/mfa/verify", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If success with tokens, create session
    if data.success and data.access_token and data.id_token then
        local session_id, csrf_token, err = _M.create_session_from_tokens(
            data.access_token,
            data.id_token,
            req_data.email,
            data.mfa_token,
            data.mfa_token_expires_at,
            data.client_type  -- Portal type from platform response
        )

        if session_id then
            _M.set_session_cookies(session_id, csrf_token)
            ngx.status = 200
            ngx.header["Content-Type"] = "application/json"
            ngx.say(cjson.encode({
                authenticated = true,
                session_id = session_id
            }))
            return ngx.exit(200)
        end
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle MFA verify-challenge endpoint (login MFA verification)
function _M.handle_mfa_verify_challenge()
    -- Read request body first
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    local res, err = _M.proxy_request("/api/mfa/verify-challenge", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If success with tokens, create session
    if data.success and data.access_token and data.id_token then
        local session_id, csrf_token, err = _M.create_session_from_tokens(
            data.access_token,
            data.id_token,
            req_data.email,
            data.mfa_token,
            data.mfa_token_expires_at,
            data.client_type  -- Portal type from platform response
        )

        if session_id then
            _M.set_session_cookies(session_id, csrf_token)
            ngx.status = 200
            ngx.header["Content-Type"] = "application/json"
            ngx.say(cjson.encode({
                authenticated = true,
                session_id = session_id
            }))
            return ngx.exit(200)
        end
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle CIBA verify endpoint
function _M.handle_ciba_verify()
    -- Read request body first
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    local res, err = _M.proxy_request("/api/auth/ciba-verify", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If success with tokens, create session
    if data.access_token and data.id_token and not data.pending then
        local session_id, csrf_token, err = _M.create_session_from_tokens(
            data.access_token,
            data.id_token,
            req_data.email,
            nil,  -- mfa_token
            nil,  -- mfa_token_expires_at
            data.client_type  -- Portal type from platform response
        )

        if session_id then
            _M.set_session_cookies(session_id, csrf_token)
            ngx.status = 200
            ngx.header["Content-Type"] = "application/json"
            ngx.say(cjson.encode({
                authenticated = true,
                session_id = session_id
            }))
            return ngx.exit(200)
        end
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Get session from cookie
function _M.get_session_from_cookie()
    local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
    local cookie_header = ngx.var.http_cookie or ""
    -- Extract cookie value using pattern matching
    local pattern = cookie_name .. "=([^;]+)"
    local cookie = string.match(cookie_header, pattern)
    if not cookie or cookie == "" then
        return nil, "No session cookie"
    end
    return redis_client.get_session(cookie)
end

-- Update session data
function _M.update_session(session_data)
    local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
    local cookie_header = ngx.var.http_cookie or ""
    -- Extract cookie value using pattern matching
    local pattern = cookie_name .. "=([^;]+)"
    local cookie = string.match(cookie_header, pattern)
    if not cookie or cookie == "" then
        return nil, "No session cookie"
    end
    local session_ttl = config.get("session_ttl") or 1200
    return redis_client.set_session(cookie, session_data, session_ttl)
end

-- Handle stepup start endpoint
function _M.handle_stepup_start()
    -- Get session from cookie
    local session, err = _M.get_session_from_cookie()
    if not session then
        ngx.status = 401
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "unauthorized", error_description = "No valid session"}))
        return ngx.exit(401)
    end

    -- Check if MFA token is valid
    local mfa_token = session.mfa_token
    local mfa_token_expires_at = session.mfa_token_expires_at or 0
    local now = ngx.now()

    if not mfa_token or mfa_token_expires_at < now then
        ngx.status = 400
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({
            error = "mfa_token_expired",
            error_description = "MFA token expired. Please refresh with password.",
            token_expired = true
        }))
        return ngx.exit(400)
    end

    -- Read request body and inject mfa_token
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    -- Build request with mfa_token
    local java_request = {
        mfa_token = mfa_token,
        message = req_data.binding_message or req_data.message
    }

    local res, err = _M.proxy_request("/api/stepup/start", cjson.encode(java_request))
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If successful, store oob_code in session for later verification
    if res.status == 200 and data.oob_code then
        session.stepup_oob_code = data.oob_code
        _M.update_session(session)

        -- Return request_id to frontend (use oob_code as request_id)
        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({
            request_id = data.oob_code
        }))
        return ngx.exit(200)
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle stepup verify endpoint
function _M.handle_stepup_verify()
    -- Get session from cookie
    local session, err = _M.get_session_from_cookie()
    if not session then
        ngx.status = 401
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "unauthorized", error_description = "No valid session"}))
        return ngx.exit(401)
    end

    -- Get mfa_token and oob_code from session
    local mfa_token = session.mfa_token
    local oob_code = session.stepup_oob_code

    if not mfa_token or not oob_code then
        ngx.status = 400
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "invalid_request", error_description = "No pending step-up request"}))
        return ngx.exit(400)
    end

    -- Build request with mfa_token and oob_code
    local java_request = {
        mfa_token = mfa_token,
        oob_code = oob_code
    }

    local res, err = _M.proxy_request("/api/stepup/verify", cjson.encode(java_request))
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- Map Java response to frontend expected format
    if data.approved then
        -- Clear the oob_code from session
        session.stepup_oob_code = nil
        _M.update_session(session)

        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({status = "approved"}))
        return ngx.exit(200)
    elseif data.rejected then
        session.stepup_oob_code = nil
        _M.update_session(session)

        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({status = "rejected"}))
        return ngx.exit(200)
    elseif data.pending then
        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({status = "pending"}))
        return ngx.exit(200)
    elseif data.expired then
        session.stepup_oob_code = nil
        _M.update_session(session)

        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({status = "expired"}))
        return ngx.exit(200)
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle stepup refresh-token endpoint
function _M.handle_stepup_refresh_token()
    -- Get session from cookie
    local session, err = _M.get_session_from_cookie()
    if not session then
        ngx.status = 401
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "unauthorized", error_description = "No valid session"}))
        return ngx.exit(401)
    end

    -- Read request body (contains email and password)
    ngx.req.read_body()
    local body = ngx.req.get_body_data()

    local res, err = _M.proxy_request("/api/stepup/refresh-token", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body)
        return ngx.exit(res.status)
    end

    -- If successful, update session with new mfa_token
    if res.status == 200 and data.mfa_token then
        session.mfa_token = data.mfa_token
        session.mfa_token_expires_at = data.mfa_token_expires_at
        _M.update_session(session)

        ngx.status = 200
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({success = true}))
        return ngx.exit(200)
    end

    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle forgot password endpoint
function _M.handle_forgot_password()
    -- Read request body
    ngx.req.read_body()
    local body = ngx.req.get_body_data()

    local res, err = _M.proxy_request("/api/auth/forgot-password", body)
    if not res then
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    -- Return response from platform
    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

-- Handle login endpoint
function _M.handle_login()
    -- Read request body first
    ngx.req.read_body()
    local body = ngx.req.get_body_data()
    local req_data = {}
    if body then
        local ok, parsed = pcall(cjson.decode, body)
        if ok then req_data = parsed end
    end

    utils.log_info("handle_login: proxying to /api/auth/login")
    local res, err = _M.proxy_request("/api/auth/login", body)
    if not res then
        utils.log_error("handle_login: proxy error: " .. (err or "unknown"))
        ngx.status = 500
        ngx.header["Content-Type"] = "application/json"
        ngx.say(cjson.encode({error = "proxy_error", error_description = err or "unknown"}))
        return ngx.exit(500)
    end

    utils.log_info("handle_login: response status=" .. res.status .. " body_len=" .. (res.body and #res.body or 0))
    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        utils.log_error("handle_login: JSON decode failed, returning raw body: " .. (res.body or "nil"))
        ngx.status = res.status
        ngx.header["Content-Type"] = "application/json"
        ngx.say(res.body or "{}")
        return ngx.exit(res.status)
    end

    -- If success with tokens (no MFA required), create session
    if data.access_token and data.id_token and not data.mfa_required then
        local session_id, csrf_token, err = _M.create_session_from_tokens(
            data.access_token,
            data.id_token,
            req_data.email or req_data.username,
            nil,  -- mfa_token
            nil,  -- mfa_token_expires_at
            data.client_type  -- Portal type from platform response
        )

        if session_id then
            _M.set_session_cookies(session_id, csrf_token)
            ngx.status = 200
            ngx.header["Content-Type"] = "application/json"
            ngx.say(cjson.encode({
                authenticated = true,
                session_id = session_id
            }))
            return ngx.exit(200)
        end
    end

    -- Return original response (mfa_required, error, etc.)
    ngx.status = res.status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(res.body)
    return ngx.exit(res.status)
end

return _M
