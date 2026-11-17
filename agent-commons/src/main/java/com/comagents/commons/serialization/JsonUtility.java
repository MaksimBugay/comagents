package com.comagents.commons.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.MapType;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

/**
 * JSON (de)serialization with two shared mappers: the simple one skips nulls and ignores unknown properties,
 * the "with null" (strict) one writes nulls and fails on unknown properties.
 */
@SuppressWarnings("unused")
public final class JsonUtility {

    private static final JsonMapper SIMPLE_MAPPER = JsonMapperFactory.create();
    public static final MapType MAP_TYPE = SIMPLE_MAPPER.getTypeFactory()
            .constructMapType(Map.class, String.class, Object.class);
    public static final MapType STRING_MAP_TYPE = SIMPLE_MAPPER.getTypeFactory()
            .constructMapType(Map.class, String.class, String.class);
    private static final JsonMapper WITH_NULL_MAPPER = JsonMapperFactory.createStrict();
    private static final String EMPTY_JSON = "{}";

    private JsonUtility() {
    }

    public static JsonMapper getSimpleMapperInstance() {
        return JsonMapperFactory.create();
    }

    public static JsonMapper getWithNullMapperInstance() {
        return JsonMapperFactory.createStrict();
    }

    public static JsonMapper getSimpleMapper() {
        return SIMPLE_MAPPER;
    }

    public static JsonMapper getWithNullMapper() {
        return WITH_NULL_MAPPER;
    }

    public static String prettyPrintJson(String json, boolean writeNulls) {
        JsonMapper mapper = mapper(writeNulls);
        return wrap(() -> mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mapper.readValue(json, Object.class)));
    }

    public static String toJson(Object object) {
        return toJson(object, false);
    }

    public static String toJson(Object object, boolean writeNulls) {
        return wrap(() -> mapper(writeNulls).writeValueAsString(object));
    }

    public static byte[] toJsonAsBytes(Object object) {
        return toJsonAsBytes(object, false);
    }

    public static byte[] toJsonAsBytes(Object object, boolean writeNulls) {
        return wrap(() -> mapper(writeNulls).writeValueAsBytes(object));
    }

    public static <T> T fromJson(byte[] jsonBytes, Class<T> clazz) {
        return wrap(() -> SIMPLE_MAPPER.readValue(jsonBytes, clazz));
    }

    public static <T> T fromJsonStrict(byte[] jsonBytes, Class<T> clazz) {
        return wrap(() -> WITH_NULL_MAPPER.readValue(jsonBytes, clazz));
    }

    public static <T> T fromJson(String jsonString, Class<T> clazz) {
        return wrap(() -> SIMPLE_MAPPER.readValue(jsonString, clazz));
    }

    public static <T> T fromJsonStrict(String jsonString, Class<T> clazz) {
        return wrap(() -> WITH_NULL_MAPPER.readValue(jsonString, clazz));
    }

    public static <T> T fromJson(InputStream is, Class<T> clazz) {
        return wrap(() -> SIMPLE_MAPPER.readValue(is, clazz));
    }

    public static <T> T fromJsonStrict(InputStream is, Class<T> clazz) {
        return wrap(() -> WITH_NULL_MAPPER.readValue(is, clazz));
    }

    public static <T> T fromJsonWithTypeReference(String jsonString, JavaType resolvedType) {
        return wrap(() -> SIMPLE_MAPPER.readValue(jsonString, resolvedType));
    }

    public static <T> Set<T> fromJsonToSet(String jsonInput, Class<T> clazz) {
        return fromJsonWithTypeReference(jsonInput, setType(clazz));
    }

    public static <T> Set<T> fromJsonToSet(byte[] jsonInput, Class<T> clazz) {
        return wrap(() -> SIMPLE_MAPPER.readValue(jsonInput, setType(clazz)));
    }

    public static <K, V> Map<K, V> fromJsonToMap(byte[] jsonInput, Class<K> clazzKey, Class<V> clazzValue) {
        return wrap(() -> SIMPLE_MAPPER.readValue(jsonInput,
                SIMPLE_MAPPER.getTypeFactory().constructMapType(Map.class, clazzKey, clazzValue)));
    }

    public static <T> List<T> fromJsonToList(String jsonInput, Class<T> clazz) {
        return fromJsonWithTypeReference(jsonInput,
                SIMPLE_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    public static <T> List<T> fromJsonToList(String jsonInput, JavaType javaType) {
        return fromJsonWithTypeReference(jsonInput,
                SIMPLE_MAPPER.getTypeFactory().constructCollectionType(List.class, javaType));
    }

    public static Map<String, Object> convertToMap(Object content) {
        return fromJsonWithTypeReference(toJson(content), MAP_TYPE);
    }

    public static <T> T convertToObjectFromMap(Map<String, ?> map, Class<T> clazz) {
        return wrap(() -> SIMPLE_MAPPER.convertValue(map, clazz));
    }

    public static Map<String, Object> convertToFilteredMap(Object content, List<String> filter) {
        if (content == null) {
            return Collections.emptyMap();
        }
        return convertToMap(content).entrySet().stream()
                .filter(entry -> filter.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static String emptyJson() {
        return EMPTY_JSON;
    }

    public static boolean isValid(String json) {
        try {
            SIMPLE_MAPPER.readTree(json);
            return true;
        } catch (JacksonException e) {
            return false;
        }
    }

    private static JsonMapper mapper(boolean writeNulls) {
        return writeNulls ? WITH_NULL_MAPPER : SIMPLE_MAPPER;
    }

    private static JavaType setType(Class<?> clazz) {
        return SIMPLE_MAPPER.getTypeFactory().constructCollectionType(Set.class, clazz);
    }

    private static <T> T wrap(Supplier<T> action) {
        try {
            return action.get();
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }
}
