-- Logout Callback Handler
-- Called after Auth0 logout, shows logout confirmation

local config = require "config"
local utils = require "utils"

-- Ensure cookie is cleared (belt and suspenders)
local cookie_name = config.get("session_cookie_name") or "CLIENT_SESSION"
ngx.header["Set-Cookie"] = cookie_name .. "=; Path=/; HttpOnly; Max-Age=0"

utils.log_info("Logout complete, showing confirmation page")

-- Show logout confirmation page
ngx.header["Content-Type"] = "text/html"
ngx.status = 200
ngx.say([[
<!DOCTYPE html>
<html>
<head>
    <title>Logged Out</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
            background-color: #f5f5f5;
        }
        .container {
            text-align: center;
            padding: 40px;
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 { color: #333; margin-bottom: 20px; }
        p { color: #666; margin-bottom: 30px; }
        a {
            display: inline-block;
            padding: 12px 24px;
            background-color: #0066cc;
            color: white;
            text-decoration: none;
            border-radius: 4px;
            font-weight: 500;
        }
        a:hover { background-color: #0052a3; }
    </style>
</head>
<body>
    <div class="container">
        <h1>You have been logged out</h1>
        <p>Your session has been ended successfully.</p>
        <a href="/oauth2/login">Sign in again</a>
    </div>
</body>
</html>
]])
