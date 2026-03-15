package com.cloudbeats.db.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.cloudbeats.db.repositories")
public class DatabaseConfig {
}
