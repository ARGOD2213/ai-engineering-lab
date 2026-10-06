package com.aiengineeringlab.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same pgvector image as docker-compose.yml in a throw-away container.
 * {@code @ServiceConnection} points the Spring datasource at it automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PgVectorTestcontainersConfiguration {

    public static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgVectorContainer() {
        return new PostgreSQLContainer(PGVECTOR_IMAGE);
    }
}
