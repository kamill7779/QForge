package io.github.kamill7779.qforge.gaokaocorpus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngestServiceImplTest {

    private final IngestServiceImpl service = new IngestServiceImpl(
            null, null, null, null, null, 20, "pdf,jpg,jpeg,png", "./data/test-uploads"
    );

    @TempDir
    Path tempDir;

    @Test
    void calculateSha256ShouldReturnCorrectHash() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello world", StandardCharsets.UTF_8);

        Method method = IngestServiceImpl.class.getDeclaredMethod("calculateSha256", Path.class);
        method.setAccessible(true);
        String hash = (String) method.invoke(service, file);

        // SHA-256 of "hello world" is well-known
        assertEquals(
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                hash);
    }

    @Test
    void encodeFileAsBase64ShouldMatchRoundTrip() throws Exception {
        byte[] content = "QForge大文件测试内容！".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("base64test.bin");
        Files.write(file, content);

        Method method = IngestServiceImpl.class.getDeclaredMethod("encodeFileAsBase64", String.class);
        method.setAccessible(true);
        String base64 = (String) method.invoke(service, file.toString());

        assertFalse(base64.isBlank());
        byte[] decoded = Base64.getDecoder().decode(base64);
        assertEquals(new String(content, StandardCharsets.UTF_8), new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void encodeFileAsBase64ShouldHandleLargeFiles() throws Exception {
        // 1 MB file — verify streaming doesn't break with larger data
        byte[] content = new byte[1024 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        Path file = tempDir.resolve("largefile.bin");
        Files.write(file, content);

        Method method = IngestServiceImpl.class.getDeclaredMethod("encodeFileAsBase64", String.class);
        method.setAccessible(true);
        String base64 = (String) method.invoke(service, file.toString());

        byte[] decoded = Base64.getDecoder().decode(base64);
        assertEquals(content.length, decoded.length);
        // Verify first and last bytes
        assertEquals(content[0], decoded[0]);
        assertEquals(content[content.length - 1], decoded[decoded.length - 1]);
    }

    @Test
    void calculateSha256ShouldHandleLargeFiles() throws Exception {
        byte[] content = new byte[1024 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        Path file = tempDir.resolve("largefile-hash.bin");
        Files.write(file, content);

        Method method = IngestServiceImpl.class.getDeclaredMethod("calculateSha256", Path.class);
        method.setAccessible(true);
        String hash = (String) method.invoke(service, file);

        // Just verify it's a valid 64-char hex string
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}
