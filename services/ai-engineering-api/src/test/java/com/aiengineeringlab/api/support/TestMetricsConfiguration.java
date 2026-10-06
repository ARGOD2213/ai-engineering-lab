package com.aiengineeringlab.api.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Web slice tests do not auto-configure metrics; the exception handler still needs a registry. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMetricsConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
