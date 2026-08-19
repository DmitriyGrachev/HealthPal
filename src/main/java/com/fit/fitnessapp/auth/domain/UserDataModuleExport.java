package com.fit.fitnessapp.auth.domain;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record UserDataModuleExport(
        String moduleKey,
        int schemaVersion,
        List<DataRetentionDisclosure> retentionDisclosure,
        Map<String, Object> data) {

    public UserDataModuleExport {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new IllegalArgumentException("moduleKey must not be blank");
        }
        moduleKey = moduleKey.trim();
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be at least 1");
        }
        if (retentionDisclosure == null || retentionDisclosure.isEmpty()
                || retentionDisclosure.stream().anyMatch(disclosure -> disclosure == null)) {
            throw new IllegalArgumentException("retentionDisclosure must be nonempty");
        }
        retentionDisclosure = retentionDisclosure.stream()
                .sorted(Comparator.comparing(DataRetentionDisclosure::category))
                .toList();
        data = immutableSortedMap(data);
    }

    private static Map<String, Object> immutableSortedMap(Map<String, Object> source) {
        if (source == null || source.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("data keys must not be blank");
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(sorted);
    }

    @SuppressWarnings("unchecked")
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nestedValue) -> {
                if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                    throw new IllegalArgumentException("nested data keys must be nonblank strings");
                }
                sorted.put(stringKey, immutableValue(nestedValue));
            });
            return Collections.unmodifiableMap(sorted);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = collection.stream()
                    .map(UserDataModuleExport::immutableValue)
                    .toList();
            return Collections.unmodifiableList(copy);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(immutableValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
