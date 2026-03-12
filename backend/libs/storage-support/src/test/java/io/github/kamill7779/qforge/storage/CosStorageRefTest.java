package io.github.kamill7779.qforge.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CosStorageRefTest {

    @Test
    void buildShouldCreateCanonicalCosUri() {
        String uri = CosStorageRef.build("qforge-2026-1304896342", "ap-shanghai", "exam-parse/source/t1/0.pdf");

        assertEquals("cos://qforge-2026-1304896342/ap-shanghai/exam-parse/source/t1/0.pdf", uri);
    }

    @Test
    void parseShouldExtractBucketRegionAndKey() {
        CosStorageRef ref = CosStorageRef.parse("cos://qforge-2026-1304896342/ap-shanghai/exam-parse/source/t1/0.pdf");

        assertEquals("qforge-2026-1304896342", ref.bucket());
        assertEquals("ap-shanghai", ref.region());
        assertEquals("exam-parse/source/t1/0.pdf", ref.key());
    }

    @Test
    void parseShouldRejectNonCosUri() {
        assertThrows(IllegalArgumentException.class, () -> CosStorageRef.parse("/tmp/a.pdf"));
    }
}
