package com.aiengineeringlab.api.repository;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.StoredDocument;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-side access to the vector table with plain SQL.
 * <p>
 * Developer note: writes and similarity search go through Spring AI's {@code VectorStore}. This
 * repository exists for what that abstraction deliberately hides: the stored vector, its size as
 * PostgreSQL sees it ({@code vector_dims}), and our own {@code created_at} column. It lets you prove
 * that the vector really landed in pgvector.
 */
@Repository
public class DocumentRepository {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final String qualifiedTableName;
    private final String embeddingModel;

    public DocumentRepository(JdbcClient jdbcClient, JsonMapper jsonMapper, PgVectorStoreProperties vectorStoreProperties,
            EmbeddingProperties embeddingProperties) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        // The table name is configuration, not user input, but it is concatenated into SQL - validate it anyway.
        this.qualifiedTableName = safeIdentifier(vectorStoreProperties.getSchemaName()) + "."
                + safeIdentifier(vectorStoreProperties.getTableName());
        this.embeddingModel = embeddingProperties.model();
    }

    public Optional<StoredDocument> findById(String id, boolean includeEmbedding) {
        String sql = """
                SELECT id, content, metadata::text AS metadata, vector_dims(embedding) AS dimensions,
                       created_at %s
                FROM %s
                WHERE id = :id
                """.formatted(includeEmbedding ? ", embedding::text AS embedding" : "", qualifiedTableName);
        return jdbcClient.sql(sql)
                .param("id", id)
                .query((rs, rowNum) -> mapRow(rs, includeEmbedding))
                .optional();
    }

    public long count() {
        return jdbcClient.sql("SELECT count(*) FROM " + qualifiedTableName).query(Long.class).single();
    }

    public String tableName() {
        return qualifiedTableName;
    }

    @SuppressWarnings("unchecked")
    private StoredDocument mapRow(ResultSet rs, boolean includeEmbedding) throws SQLException {
        Map<String, Object> metadata = jsonMapper.readValue(rs.getString("metadata"), Map.class);
        float[] embedding = includeEmbedding ? parseVector(rs.getString("embedding")) : null;
        return new StoredDocument(
                rs.getString("id"),
                rs.getString("content"),
                metadata,
                rs.getInt("dimensions"),
                embeddingModel,
                rs.getObject("created_at", OffsetDateTime.class),
                embedding);
    }

    /** pgvector's text representation is a plain array literal: "[0.012,-0.034,...]". */
    static float[] parseVector(String text) {
        String body = text.substring(1, text.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    private static String safeIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("Unsafe SQL identifier in configuration: " + identifier);
        }
        return identifier;
    }
}
