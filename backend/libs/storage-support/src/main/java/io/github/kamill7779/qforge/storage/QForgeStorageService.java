package io.github.kamill7779.qforge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public interface QForgeStorageService {

    String putObject(String key, InputStream inputStream, long contentLength, String contentType) throws IOException;

    InputStream getObjectStream(String storageRef) throws IOException;

    default byte[] getObjectBytes(String storageRef) throws IOException {
        try (InputStream inputStream = getObjectStream(storageRef)) {
            return inputStream.readAllBytes();
        }
    }

    default String getObjectBase64(String storageRef) throws IOException {
        return Base64.getEncoder().encodeToString(getObjectBytes(storageRef));
    }

    void deleteObject(String storageRef);

    default String buildObjectKey(String prefix, String... segments) {
        StringBuilder builder = new StringBuilder(normalize(prefix));
        for (String segment : segments) {
            String normalized = normalize(segment);
            if (!normalized.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('/');
                }
                builder.append(normalized);
            }
        }
        return builder.toString();
    }

    default CosStorageRef parseStorageRef(String storageRef) {
        return CosStorageRef.parse(storageRef);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
