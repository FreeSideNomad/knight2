-- OAuth2 Login Initiation
-- Redirects user to Microsoft Entra for authentication

local config = require "config"
local utils = require "utils"
local redis_client = require "redis_client"

-- Get return_to parameter (where to redirect after login)
local args = ngx.req.get_uri_args()
local return_to = args.return_to or "/"

-- Generate random state and nonce for CSRF protection
local state = utils.random_string(32)
local nonce = utils.random_string(32)

-- Store state in Redis with return_to URL
local state_data = {
    nonce = nonce,
    return_to = return_to,
    created_at = utils.now()
}

local ok, err = redis_client.set_oauth_state(state, state_data, 300) -- 5 minute TTL
if not ok then
    utils.log_error("Failed to store OAuth state: " .. (err or "unknown"))
    ngx.status = 500
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"error": "Failed to initiate login"}')
    return ngx.exit(500)
end

-- Build authorization URL
local auth_params = {
      client_id = config.get_entra_client_id(),
      response_type = "code",
      redirect_uri = config.get_redirect_uri(),
      scope = "openid profile email api://74fdc44e-8ef5-4465-8848-f67d608c5651/access",
      state = state,
      nonce = nonce,
      response_mode = "query"
  }

local auth_url = config.get_authorization_url() .. "?" .. utils.build_query_string(auth_params)

utils.log_info("Redirecting to Entra login, state: " .. string.sub(state, 1, 8) .. "...")

-- Redirect to Microsoft Entra
return ngx.redirect(auth_url)
