package com.cloudbeats.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.net.URI;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Configures the OAuth2 client flow to redirect back to the frontend
 * after a successful authorization code exchange.
 *
 * <p>A delegating {@link RequestCache} detects OAuth2 callback requests
 * (by matching their path against the configured {@code redirect-uri}
 * values) and returns a synthetic {@link SavedRequest} pointing to the
 * configured frontend URL.  All other requests are forwarded to the
 * default {@link HttpSessionRequestCache} so that normal Spring Security
 * behaviour is preserved.</p>
 */
@Configuration
public class OAuth2ClientConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Builds a {@link RequestCache} that intercepts only OAuth2 provider
     * callback requests and redirects them to the frontend.  Every other
     * request is delegated to the standard {@link HttpSessionRequestCache}.
     */
    @Bean
    public RequestCache frontendRedirectRequestCache(
            ClientRegistrationRepository clientRegistrationRepository) {

        Set<String> callbackPaths = resolveCallbackPaths(clientRegistrationRepository);
        HttpSessionRequestCache delegate = new HttpSessionRequestCache();

        return new RequestCache() {

            @Override
            public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
                delegate.saveRequest(request, response);
            }

            @Override
            public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
                if (isOAuth2Callback(request)) {
                    return new SimpleFrontendSavedRequest(frontendUrl);
                }
                return delegate.getRequest(request, response);
            }

            @Override
            public HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) {
                if (isOAuth2Callback(request)) {
                    return null;
                }
                return delegate.getMatchingRequest(request, response);
            }

            @Override
            public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
                delegate.removeRequest(request, response);
            }

            private boolean isOAuth2Callback(HttpServletRequest request) {
                return callbackPaths.contains(request.getRequestURI());
            }
        };
    }

    /**
     * Extracts the path component from every configured {@code redirect-uri}
     * so callback requests can be identified without session state.
     */
    private Set<String> resolveCallbackPaths(ClientRegistrationRepository repository) {
        Set<String> paths = new HashSet<>();
        if (repository instanceof Iterable<?> iterable) {
            StreamSupport.stream(iterable.spliterator(), false)
                    .filter(ClientRegistration.class::isInstance)
                    .map(ClientRegistration.class::cast)
                    .map(ClientRegistration::getRedirectUri)
                    .filter(Objects::nonNull)
                    .forEach(uri -> {
                        // resolve {baseUrl} placeholder so we can extract the path
                        String resolved = uri.replace("{baseUrl}", "http://placeholder");
                        try {
                            paths.add(URI.create(resolved).getPath());
                        } catch (IllegalArgumentException ignored) {
                            // skip malformed URIs
                        }
                    });
        }
        return Collections.unmodifiableSet(paths);
    }

    /**
     * Minimal {@link SavedRequest} that only supplies the redirect URL.
     * Used by {@code OAuth2AuthorizationCodeGrantFilter} to determine
     * where to send the browser after a successful code exchange.
     */
    private static class SimpleFrontendSavedRequest implements SavedRequest {
        private final String redirectUrl;

        SimpleFrontendSavedRequest(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        @Override public String getRedirectUrl() { return redirectUrl; }
        @Override public List<Cookie> getCookies() { return Collections.emptyList(); }
        @Override public String getMethod() { return "GET"; }
        @Override public List<String> getHeaderValues(String name) { return Collections.emptyList(); }
        @Override public Collection<String> getHeaderNames() { return Collections.emptyList(); }
        @Override public List<Locale> getLocales() { return Collections.emptyList(); }
        @Override public String[] getParameterValues(String name) { return new String[0]; }
        @Override public Map<String, String[]> getParameterMap() { return Collections.emptyMap(); }
    }
}




