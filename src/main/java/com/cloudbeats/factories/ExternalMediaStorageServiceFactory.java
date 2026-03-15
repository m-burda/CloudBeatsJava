package com.cloudbeats.factories;

import com.cloudbeats.models.Provider;
import com.cloudbeats.services.externalMediaStorage.ExternalMediaStorageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExternalMediaStorageServiceFactory {
    private final Map<Provider, ExternalMediaStorageService> services;

    public ExternalMediaStorageServiceFactory(List<ExternalMediaStorageService> storageServices) {
        this.services = storageServices.stream()
                .collect(Collectors.toMap(ExternalMediaStorageService::getProvider, s -> s));
    }

    public ExternalMediaStorageService getService(Provider provider) {
        ExternalMediaStorageService service = services.get(provider);
        if (service == null) {
            throw new IllegalArgumentException("No storage service found for provider: " + provider);
        }
        return service;
    }
}
