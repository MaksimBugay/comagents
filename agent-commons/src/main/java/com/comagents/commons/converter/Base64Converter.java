package com.comagents.commons.converter;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64Converter {

    private Base64Converter() {
    }

    public static String toBase64String(String source) {
        if (StringUtils.isBlank(source)) {
            return source;
        }
        return Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64String(String base64String) {
        if (StringUtils.isBlank(base64String)) {
            return base64String;
        }
        return new String(Base64.getDecoder().decode(base64String), StandardCharsets.UTF_8);
    }
}
