package br.com.oficina.os.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonStringUtil {

    private JsonStringUtil() {
    }

    public static String toJson(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
