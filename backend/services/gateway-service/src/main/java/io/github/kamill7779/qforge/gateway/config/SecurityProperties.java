package io.github.kamill7779.qforge.gateway.config;

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

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
