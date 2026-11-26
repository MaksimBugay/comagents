package com.comagents.llmwatchdog;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Micronaut's JSON engine is agent-commons' JsonUtility mapper (JsonUtilityMapperFactory), not Micronaut's default. */
@MicronautTest
class JsonEngineTest {

    @Inject
    ApplicationContext context;

    @Test
    void hasJsonUtilitysSettings() {
        // resolved as every injection point gets it: JsonUtilityMapperFactory replaces Micronaut's own
        JsonMapper jsonMapper = context.getBean(JsonMapper.class);

        assertTrue(jsonMapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS), "off in Micronaut's default");
    }
}
