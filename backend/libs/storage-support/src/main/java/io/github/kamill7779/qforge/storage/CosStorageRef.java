package io.github.kamill7779.qforge.storage;

import java.util.Objects;

public record CosStorageRef(String bucket, String region, String key) {

    public CosStorageRef {
        bucket = require(bucket, "bucket");
        region = require(region, "region");
        key = normalizeKey(require(key, "key"));
    }

    public static String build(String bucket, String region, String key) {
        return new CosStorageRef(bucket, region, key).toUri();
    }

    public static CosStorageRef parse(String storageRef) {
        if (storageRef == null || !storageRef.startsWith("cos://")) {
            throw new IllegalArgumentException("Invalid COS storage ref: " + storageRef);
        }
        String remainder = storageRef.substring("cos://".length());
        int firstSlash = remainder.indexOf('/');
        int secondSlash = remainder.indexOf('/', firstSlash + 1);
        if (firstSlash <= 0 || secondSlash <= firstSlash + 1 || secondSlash >= remainder.length() - 1) {
            throw new IllegalArgumentException("Invalid COS storage ref: " + storageRef);
        }
        return new CosStorageRef(
                remainder.substring(0, firstSlash),
                remainder.substring(firstSlash + 1, secondSlash),
                remainder.substring(secondSlash + 1)
        );
    }

    public String toUri() {
        return "cos://" + bucket + "/" + region + "/" + key;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeKey(String value) {
        String normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return normalized;
    }
}
