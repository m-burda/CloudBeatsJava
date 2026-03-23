package com.cloudbeats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication
@EnableJpaRepositories("com.cloudbeats.repositories")
@EntityScan("com.cloudbeats.db.entities")
public class CloudBeatsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudBeatsApplication.class, args);
    }
}
