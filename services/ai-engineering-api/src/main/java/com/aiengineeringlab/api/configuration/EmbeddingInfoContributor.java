package com.aiengineeringlab.api.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/** Shows the active embedding model and vector table on /actuator/info (no secrets). */
@Component
public class EmbeddingInfoContributor implements InfoContributor {

    private final EmbeddingProperties embeddingProperties;
    private final PgVectorStoreProperties vectorStoreProperties;

    public EmbeddingInfoContributor(EmbeddingProperties embeddingProperties,
            PgVectorStoreProperties vectorStoreProperties) {
        this.embeddingProperties = embeddingProperties;
        this.vectorStoreProperties = vectorStoreProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> embedding = new LinkedHashMap<>();
        embedding.put("provider", embeddingProperties.provider());
        embedding.put("model", embeddingProperties.model());
        embedding.put("dimensions", embeddingProperties.dimensions());
        builder.withDetail("embedding", embedding);

        Map<String, Object> vectorStore = new LinkedHashMap<>();
        vectorStore.put("type", "pgvector");
        vectorStore.put("table", vectorStoreProperties.getSchemaName() + "." + vectorStoreProperties.getTableName());
        vectorStore.put("distanceType", vectorStoreProperties.getDistanceType().name());
        vectorStore.put("indexType", vectorStoreProperties.getIndexType().name());
        builder.withDetail("vectorStore", vectorStore);
    }
}
