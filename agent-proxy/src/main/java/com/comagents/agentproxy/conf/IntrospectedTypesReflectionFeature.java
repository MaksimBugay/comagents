package com.comagents.agentproxy.conf;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Native image: Jackson, Micronaut's JSON engine and {@code JsonUtility}'s, reads and writes types by reflection.
 * This registers every type that has a Micronaut bean introspection: agent-proxy's own ({@code @Introspected}), the MCP
 * schema's (micronaut-mcp ships their introspections) and Micronaut's (error bodies, health results). Enabled in
 * {@code META-INF/native-image/.../native-image.properties}.
 */
@SuppressWarnings("unused") // loaded by native-image by name (--features), never referenced in code
public final class IntrospectedTypesReflectionFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (BeanIntrospection<Object> introspection : BeanIntrospector.forClassLoader(access.getApplicationClassLoader())
                .findIntrospections(_ -> true)) {
            register(introspection.getBeanType());
        }
    }

    private static void register(Class<?> type) {
        RuntimeReflection.register(type);
        RuntimeReflection.registerAllDeclaredConstructors(type);
        RuntimeReflection.registerAllDeclaredMethods(type);
        RuntimeReflection.registerAllDeclaredFields(type);
        RuntimeReflection.register(type.getDeclaredConstructors());
        RuntimeReflection.register(type.getDeclaredMethods());
        RuntimeReflection.register(type.getDeclaredFields());
        if (type.isRecord()) {
            RuntimeReflection.registerAllRecordComponents(type);
        }
    }
}
