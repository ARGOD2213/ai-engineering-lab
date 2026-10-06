package com.aiengineeringlab.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup when the embedding model does not produce the number of dimensions
 * the vector table was created for.
 * <p>
 * Developer note: we measure the dimension by embedding a probe text instead of trusting a lookup
 * table. If someone swaps the model but forgets to change the table, the application refuses to
 * start instead of failing on the first insert (or, worse, silently mixing vectors from two
 * different models).
 */
@Component
public class EmbeddingDimensionVerifier implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionVerifier.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public EmbeddingDimensionVerifier(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.verifyDimensionsOnStartup()) {
            log.info("Embedding dimension verification disabled (expected {} dimensions for model {})",
                    properties.dimensions(), properties.model());
            return;
        }
        long start = System.nanoTime();
        int actual = embeddingModel.embed("dimension probe").length;
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        if (actual != properties.dimensions()) {
            throw new IllegalStateException(("Embedding model '%s' produced %d dimensions but the service is configured "
                    + "for %d. Point lab.embedding.dimensions and the vector table at the same model, then "
                    + "re-embed existing documents.").formatted(properties.model(), actual, properties.dimensions()));
        }
        log.info("Embedding model verified: provider={} model={} dimensions={} probeLatencyMs={}",
                properties.provider(), properties.model(), actual, latencyMs);
    }
}
