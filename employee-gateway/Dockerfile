FROM openresty/openresty:1.25.3.1-alpine

# Install perl (required for opm), curl for healthcheck, and CA certificates for SSL
RUN apk add --no-cache perl curl ca-certificates

# Install lua-resty-http library for HTTP requests
RUN opm get ledgetech/lua-resty-http

# Copy configuration files
COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY conf.d/ /etc/nginx/conf.d/
COPY lua/ /usr/local/openresty/nginx/lua/

# Ensure Lua files are readable
RUN chmod -R 644 /usr/local/openresty/nginx/lua/*.lua

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s \
    CMD curl -f http://localhost/health || exit 1

CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
