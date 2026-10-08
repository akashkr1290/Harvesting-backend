package com.harvestflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans the whole {@code com.harvestflow} tree, not only {@code com.harvestflow.api}.
 * The self-registration module lives in {@code com.harvestflow.registration};
 * without these three base packages its controller, services, entities and
 * repositories were never loaded and every /api/auth/register call returned 404.
 */
@SpringBootApplication(scanBasePackages = "com.harvestflow")
@EntityScan(basePackages = "com.harvestflow")
@EnableJpaRepositories(basePackages = "com.harvestflow")
public class HarvestFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarvestFlowApplication.class, args);
    }
}
