package com.comagents.commons.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base64ConverterTest {

    @Test
    void encodesUtf8TextAsStandardBase64() {
        assertEquals("aGVsbG8=", Base64Converter.toBase64String("hello"));
        assertEquals("0J/RgNC40LLQtdGC", Base64Converter.toBase64String("Привет"));
        assertEquals("", Base64Converter.toBase64String(""));
    }

    @Test
    void decodesStandardBase64ToUtf8Text() {
        assertEquals("hello", Base64Converter.fromBase64String("aGVsbG8="));
        assertEquals("Привет", Base64Converter.fromBase64String("0J/RgNC40LLQtdGC"));
        assertEquals("", Base64Converter.fromBase64String(""));
    }

    @Test
    void roundTripKeepsText() {
        String source = "{\"text\":\"multi\\nline ✓\"}";

        assertEquals(source, Base64Converter.fromBase64String(Base64Converter.toBase64String(source)));
    }

    @Test
    void invalidBase64IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Base64Converter.fromBase64String("not base64!"));
    }
}
