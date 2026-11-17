package com.comagents.agentproxy.conf;

import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.jackson.ObjectMapperFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import tools.jackson.databind.json.JsonMapper;

/**
 * Micronaut's JSON engine (HTTP bodies, the MCP server) is {@link JsonUtility}'s simple mapper: its settings (nulls
 * skipped, unknown properties ignored, ...) with the Jackson modules Micronaut registers itself, which serialize its
 * own types (its JSON tree, error bodies).
 */
@Factory
public class JsonUtilityMapperFactory {

    @Singleton
    @Primary
    @Named("json")
    @BootstrapContextCompatible
    @Replaces(bean = JsonMapper.class, factory = ObjectMapperFactory.class)
    // the IDE misses the builder bean: ObjectMapperFactory#jsonMapperBuilder provides it
    @SuppressWarnings("MnInjectionPoints")
    public JsonMapper jsonMapper(JsonMapper.Builder micronautBuilder) {
        return JsonUtility.getSimpleMapper().rebuild()
                .addModules(micronautBuilder.build().registeredModules())
                .build();
    }
}
