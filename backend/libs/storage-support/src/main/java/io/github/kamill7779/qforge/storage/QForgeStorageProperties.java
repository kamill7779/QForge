package io.github.kamill7779.qforge.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "qforge.storage")
public class QForgeStorageProperties {

    @NotBlank
    private String backend = "cos";

    @Valid
    private final Cos cos = new Cos();

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public Cos getCos() {
        return cos;
    }

    public static class Cos {
        @NotBlank
        private String bucket;
        @NotBlank
        private String region;
        @NotBlank
        private String endpoint;
        @NotBlank
        private String secretId;
        @NotBlank
        private String secretKey;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
