package com.cloudbeats.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.util.*;
import java.util.stream.StreamSupport;

@Configuration
public class OAuth2ClientConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public RequestCache frontendRedirectRequestCache(ClientRegistrationRepository repository) {
        HttpSessionRequestCache delegate = new HttpSessionRequestCache();

        List<String> callbackPatterns = resolveCallbackPatterns(repository);

        return new RequestCache() {
            @Override
            public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
                if (!isOAuth2Callback(request)) {
                    delegate.saveRequest(request, response);
                }
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
                return delegate.getMatchingRequest(request, response);
            }

            @Override
            public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
                delegate.removeRequest(request, response);
            }

            private boolean isOAuth2Callback(HttpServletRequest request) {
                String path = request.getServletPath();
                return callbackPatterns.stream().anyMatch(path::equals);
            }
        };
    }

    private List<String> resolveCallbackPatterns(ClientRegistrationRepository repository) {
        if (repository instanceof Iterable<?> iterable) {
            return StreamSupport.stream(iterable.spliterator(), false)
                    .filter(ClientRegistration.class::isInstance)
                    .map(ClientRegistration.class::cast)
                    .map(ClientRegistration::getRedirectUri)
                    .map(uri -> uri.replace("{baseUrl}", ""))
                    .toList();
        }
        return Collections.emptyList();
    }

    private static class SimpleFrontendSavedRequest implements SavedRequest {
        private final String redirectUrl;

        SimpleFrontendSavedRequest(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        @Override
        public String getRedirectUrl() {
            return redirectUrl;
        }

        @Override
        public List<Cookie> getCookies() {
            return List.of();
        }

        @Override public String getMethod() { return "GET"; }

        @Override
        public List<String> getHeaderValues(String name) {
            return List.of();
        }

        @Override
        public Collection<String> getHeaderNames() {
            return List.of();
        }

        @Override
        public List<Locale> getLocales() {
            return List.of();
        }

        @Override
        public String @Nullable [] getParameterValues(String name) {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Map.of();
        }
    }
}

