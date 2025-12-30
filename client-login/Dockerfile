# OpenResty (Nginx + Lua) for Auth0 Authentication Gateway
FROM openresty/openresty:1.25.3.1-alpine

# Install dependencies
RUN apk add --no-cache \
    curl \
    openssl

# Install perl for opm and packages
RUN apk add --no-cache perl \
    && opm get ledgetech/lua-resty-http

# Create directories
RUN mkdir -p /usr/local/openresty/nginx/lua \
    && mkdir -p /usr/local/openresty/nginx/login \
    && mkdir -p /var/log/nginx

# Copy configuration files
COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY conf.d/ /etc/nginx/conf.d/
COPY lua/ /usr/local/openresty/nginx/lua/
COPY login/ /usr/local/openresty/nginx/login/

# Set permissions
RUN chmod -R 755 /usr/local/openresty/nginx/lua \
    && chmod -R 755 /usr/local/openresty/nginx/login

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Start OpenResty
CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
