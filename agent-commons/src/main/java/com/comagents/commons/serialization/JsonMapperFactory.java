package com.comagents.commons.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the JSON mappers behind {@link JsonUtility}.
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    /**
     * Skips null and absent values on write, ignores unknown properties on read.
     */
    public static JsonMapper create() {
        return create(JsonInclude.Include.NON_ABSENT, false);
    }

    /**
     * Writes every value including nulls, fails on unknown properties on read.
     */
    public static JsonMapper createStrict() {
        return create(JsonInclude.Include.ALWAYS, true);
    }

    public static JsonMapper create(JsonInclude.Include include, boolean failOnUnknownProperties) {
        return JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                        .withValueInclusion(include)
                        .withContentInclusion(include))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // null -> 0/false for primitives
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
                .build();
    }
}
