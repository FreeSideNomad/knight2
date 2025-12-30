-- Configuration module for Auth Gateway
local _M = {}

function _M.get(key)
    local sessions = ngx.shared.sessions
    return sessions:get("config_" .. key)
end

function _M.get_all()
    return {
        auth0_domain = _M.get("auth0_domain"),
        auth0_client_id = _M.get("auth0_client_id"),
        auth0_client_secret = _M.get("auth0_client_secret"),
        redis_host = _M.get("redis_host"),
        redis_port = _M.get("redis_port"),
        session_ttl = _M.get("session_ttl"),
        session_cookie_name = _M.get("session_cookie_name"),
        backend_url = _M.get("backend_url")
    }
end

function _M.get_auth0_urls()
    local domain = _M.get("auth0_domain")
    return {
        authorize = "https://" .. domain .. "/authorize",
        token = "https://" .. domain .. "/oauth/token",
        userinfo = "https://" .. domain .. "/userinfo",
        logout = "https://" .. domain .. "/v2/logout",
        jwks = "https://" .. domain .. "/.well-known/jwks.json"
    }
end

-- Get Basic Auth header for platform login API
function _M.get_auth_service_basic_auth()
    local username = _M.get("login_api_username") or "gateway"
    local password = _M.get("login_api_password") or ""
    local credentials = ngx.encode_base64(username .. ":" .. password)
    return "Basic " .. credentials
end

return _M
