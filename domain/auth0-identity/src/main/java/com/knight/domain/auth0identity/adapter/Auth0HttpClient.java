package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.api.Auth0IntegrationException;
import com.knight.domain.auth0identity.api.Auth0TokenService;
import com.knight.domain.auth0identity.config.Auth0Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for Auth0 Management API.
 * Uses Spring RestClient for synchronous HTTP calls.
 */
@Component
public class Auth0HttpClient {

    private static final Logger log = LoggerFactory.getLogger(Auth0HttpClient.class);

    private final RestClient restClient;
    private final Auth0TokenService tokenService;

    public Auth0HttpClient(Auth0Config config, Auth0TokenService tokenService) {
        this.tokenService = tokenService;
        this.restClient = RestClient.builder()
            .baseUrl(config.getManagementApiUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private void handleError(String method, String uri, org.springframework.http.client.ClientHttpResponse response) {
        try {
            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            String message = String.format("Auth0 API error: %s for %s %s. Body: %s", response.getStatusCode(), method, uri, body);
            log.error(message);
            throw new Auth0IntegrationException(message);
        } catch (IOException e) {
            throw new Auth0IntegrationException("Auth0 API error (failed to read body): " + e.getMessage());
        }
    }

    public <T> T get(String uri, Class<T> responseType) {
        log.debug("GET {}", uri);
        T result = restClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> handleError("GET", uri, response))
            .body(responseType);
        log.debug("GET {} returned: {}", uri, result);
        return result;
    }

    public <T> T getWithQueryParam(String uri, String paramName, String paramValue, Class<T> responseType) {
        // Auth0 expects the email NOT to be URL-encoded in the query parameter
        // Use URI.create to build the URI without additional encoding
        String fullUri = uri + "?" + paramName + "=" + paramValue;
        log.debug("GET {}", fullUri);
        T result = restClient.get()
            .uri(fullUri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> handleError("GET", fullUri, response))
            .body(responseType);
        log.debug("GET {} returned: {}", fullUri, result);
        return result;
    }

    public <T> T post(String uri, Object body, Class<T> responseType) {
        log.debug("POST {}", uri);
        return restClient.post()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> handleError("POST", uri, response))
            .body(responseType);
    }

    public <T> T patch(String uri, Object body, Class<T> responseType) {
        log.debug("PATCH {}", uri);
        return restClient.patch()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> handleError("PATCH", uri, response))
            .body(responseType);
    }

    public void delete(String uri) {
        log.debug("DELETE {}", uri);
        restClient.delete()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> handleError("DELETE", uri, response))
            .toBodilessEntity();
    }
}
