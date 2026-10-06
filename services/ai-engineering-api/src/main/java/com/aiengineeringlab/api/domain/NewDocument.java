package com.aiengineeringlab.api.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A document to be embedded and stored. A null id means "generate one". */
public record NewDocument(@Nullable String id, String text, Map<String, Object> metadata) {
}
