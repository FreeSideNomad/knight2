-- Configuration module
-- Loads configuration from shared memory

local _M = {}

function _M.get(key)
    local config = ngx.shared.config
    return config:get(key)
end

function _M.get_entra_tenant_id()
    return _M.get("entra_tenant_id")
end

function _M.get_entra_client_id()
    return _M.get("entra_client_id")
end

function _M.get_entra_client_secret()
    return _M.get("entra_client_secret")
end

function _M.get_redirect_uri()
    return _M.get("entra_redirect_uri")
end

function _M.get_redis_host()
    return _M.get("redis_host")
end

function _M.get_redis_port()
    return _M.get("redis_port")
end

function _M.get_session_ttl()
    return _M.get("session_ttl")
end

function _M.get_session_cookie_name()
    return _M.get("session_cookie_name")
end

function _M.get_backend_url()
    return _M.get("backend_url")
end

-- Build Microsoft Entra authorization URL
function _M.get_authorization_url()
    local tenant_id = _M.get_entra_tenant_id()
    return "https://login.microsoftonline.com/" .. tenant_id .. "/oauth2/v2.0/authorize"
end

-- Build Microsoft Entra token URL
function _M.get_token_url()
    local tenant_id = _M.get_entra_tenant_id()
    return "https://login.microsoftonline.com/" .. tenant_id .. "/oauth2/v2.0/token"
end

-- Build Microsoft Entra logout URL
function _M.get_logout_url()
    local tenant_id = _M.get_entra_tenant_id()
    return "https://login.microsoftonline.com/" .. tenant_id .. "/oauth2/v2.0/logout"
end

return _M
