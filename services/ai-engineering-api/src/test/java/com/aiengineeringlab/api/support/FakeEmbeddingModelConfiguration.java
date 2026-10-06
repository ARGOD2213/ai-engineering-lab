package com.aiengineeringlab.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Replaces the real embedding model with {@link HashingEmbeddingModel} (see application-test.yml). */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingModelConfiguration {

    public static final int DIMENSIONS = 384;

    @Bean
    HashingEmbeddingModel hashingEmbeddingModel() {
        return new HashingEmbeddingModel(DIMENSIONS);
    }
}
