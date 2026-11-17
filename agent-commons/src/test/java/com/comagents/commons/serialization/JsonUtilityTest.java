package com.comagents.commons.serialization;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilityTest {

    record Person(String name, Integer age) {
    }

    record Event(String title, Instant at, Optional<String> note) {
    }

    record Empty() {
    }

    @Test
    void nullsAreSkippedByDefault() {
        assertEquals("{\"name\":\"Ann\"}", JsonUtility.toJson(new Person("Ann", null)));
        assertArrayEquals("{\"name\":\"Ann\"}".getBytes(StandardCharsets.UTF_8),
                JsonUtility.toJsonAsBytes(new Person("Ann", null)));
    }

    @Test
    void nullsAreWrittenOnRequest() {
        assertEquals("{\"name\":\"Ann\",\"age\":null}", JsonUtility.toJson(new Person("Ann", null), true));
        assertArrayEquals("{\"name\":\"Ann\",\"age\":null}".getBytes(StandardCharsets.UTF_8),
                JsonUtility.toJsonAsBytes(new Person("Ann", null), true));
    }

    @Test
    void absentOptionalIsSkippedAndDatesAreIsoStrings() {
        Event event = new Event("deploy", Instant.parse("2026-11-17T10:15:30Z"), Optional.empty());

        String json = JsonUtility.toJson(event);

        assertEquals("{\"title\":\"deploy\",\"at\":\"2026-11-17T10:15:30Z\"}", json);
        assertEquals(event, JsonUtility.fromJson(json, Event.class));
    }

    @Test
    void emptyBeanIsSerialized() {
        assertEquals("{}", JsonUtility.toJson(new Empty()));
    }

    @Test
    void readsFromStringBytesAndStream() {
        String json = "{\"name\":\"Ann\",\"age\":30}";
        Person ann = new Person("Ann", 30);

        assertEquals(ann, JsonUtility.fromJson(json, Person.class));
        assertEquals(ann, JsonUtility.fromJson(json.getBytes(StandardCharsets.UTF_8), Person.class));
        assertEquals(ann, JsonUtility.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                Person.class));
        assertEquals(ann, JsonUtility.fromJsonStrict(json, Person.class));
        assertEquals(ann, JsonUtility.fromJsonStrict(json.getBytes(StandardCharsets.UTF_8), Person.class));
        assertEquals(ann, JsonUtility.fromJsonStrict(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Person.class));
    }

    @Test
    void unknownPropertiesAreIgnoredByDefaultAndRejectedWhenStrict() {
        String json = "{\"name\":\"Ann\",\"nickname\":\"A\"}";

        assertEquals(new Person("Ann", null), JsonUtility.fromJson(json, Person.class));
        assertThrows(SerializationException.class, () -> JsonUtility.fromJsonStrict(json, Person.class));
        assertThrows(SerializationException.class,
                () -> JsonUtility.fromJsonStrict(json.getBytes(StandardCharsets.UTF_8), Person.class));
    }

    @Test
    void malformedJsonFailsWithSerializationException() {
        SerializationException exception = assertThrows(SerializationException.class,
                () -> JsonUtility.fromJson("{not json", Person.class));

        assertInstanceOf(tools.jackson.core.JacksonException.class, exception.getCause());
    }

    @Test
    void emptyStringIsReadAsNullObject() {
        record Holder(Person person) {
        }

        assertNull(JsonUtility.fromJson("{\"person\":\"\"}", Holder.class).person());
    }

    @Test
    void nullIsReadAsDefaultForPrimitives() {
        record Counter(int count) {
        }

        assertEquals(0, JsonUtility.fromJson("{\"count\":null}", Counter.class).count());
    }

    @Test
    void floatsAreReadAsBigDecimal() {
        Map<String, Object> map = JsonUtility.fromJsonWithTypeReference("{\"price\":0.1}", JsonUtility.MAP_TYPE);

        assertEquals(new BigDecimal("0.1"), map.get("price"));
    }

    @Test
    void readsCollections() {
        assertEquals(List.of(new Person("Ann", 30), new Person("Bob", 40)),
                JsonUtility.fromJsonToList("[{\"name\":\"Ann\",\"age\":30},{\"name\":\"Bob\",\"age\":40}]",
                        Person.class));
        assertEquals(List.of(List.of("a"), List.of("b", "c")),
                JsonUtility.fromJsonToList("[[\"a\"],[\"b\",\"c\"]]",
                        JsonUtility.getSimpleMapper().getTypeFactory()
                                .constructCollectionType(List.class, String.class)));
        assertEquals(Set.of("a", "b"), JsonUtility.fromJsonToSet("[\"a\",\"b\",\"a\"]", String.class));
        assertEquals(Set.of(1, 2),
                JsonUtility.fromJsonToSet("[1,2,2]".getBytes(StandardCharsets.UTF_8), Integer.class));
        assertEquals(Map.of("a", 1, "b", 2),
                JsonUtility.fromJsonToMap("{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8), String.class,
                        Integer.class));
        assertEquals(Map.of("a", "x"),
                JsonUtility.fromJsonWithTypeReference("{\"a\":\"x\"}", JsonUtility.STRING_MAP_TYPE));
    }

    @Test
    void convertsBetweenObjectAndMap() {
        Person ann = new Person("Ann", 30);

        Map<String, Object> map = JsonUtility.convertToMap(ann);

        assertEquals(Map.of("name", "Ann", "age", 30), map);
        assertEquals(ann, JsonUtility.convertToObjectFromMap(map, Person.class));
    }

    @Test
    void filteredMapKeepsOnlyRequestedKeys() {
        assertEquals(Map.of("name", "Ann"),
                JsonUtility.convertToFilteredMap(new Person("Ann", 30), List.of("name", "missing")));
        assertEquals(Map.of(), JsonUtility.convertToFilteredMap(null, List.of("name")));
    }

    @Test
    void prettyPrintKeepsOrDropsNulls() {
        String json = "{\"name\":\"Ann\",\"age\":null}";

        String withNulls = JsonUtility.prettyPrintJson(json, true);
        String withoutNulls = JsonUtility.prettyPrintJson(json, false);

        assertTrue(withNulls.contains("\n"));
        assertTrue(withNulls.contains("\"age\" : null"));
        assertFalse(withoutNulls.contains("age"));
        assertThrows(SerializationException.class, () -> JsonUtility.prettyPrintJson("{", false));
    }

    @Test
    void validatesJson() {
        assertTrue(JsonUtility.isValid("{\"a\":[1,2]}"));
        assertTrue(JsonUtility.isValid(JsonUtility.emptyJson()));
        assertFalse(JsonUtility.isValid("{\"a\":"));
    }

    @Test
    void instanceGettersCreateNewMappers() {
        assertNotSame(JsonUtility.getSimpleMapper(), JsonUtility.getSimpleMapperInstance());
        assertNotSame(JsonUtility.getWithNullMapper(), JsonUtility.getWithNullMapperInstance());
        assertEquals("{\"name\":\"Ann\",\"age\":null}",
                JsonUtility.getWithNullMapperInstance().writeValueAsString(new Person("Ann", null)));
    }
}
