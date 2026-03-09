package io.github.kamill7779.qforge.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private boolean swaggerPublic;
    private final Jwt jwt = new Jwt();

    public boolean isSwaggerPublic() {
        return swaggerPublic;
    }

    public void setSwaggerPublic(boolean swaggerPublic) {
        this.swaggerPublic = swaggerPublic;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {
        private String secret;
        private long expiresInSeconds = 7200;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(long expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }
    }
}
