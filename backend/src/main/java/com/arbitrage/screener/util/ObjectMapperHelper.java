package com.arbitrage.screener.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectMapperHelper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }

    public static <T> T parseJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper().readValue(json, typeReference);
        } catch (Exception ex) {
            log.error("Couldn't parse json {}", json, ex);
            throw new IllegalArgumentException("Couldn't parse json");
        }
    }

    public static String writeValueAsString(Object anObject) {
        try {
            return objectMapper().writeValueAsString(anObject);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
