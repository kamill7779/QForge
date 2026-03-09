package io.github.kamill7779.qforge.examparse.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.business")
public class QForgeBusinessProperties {

    private int maxExamUploadFiles = 10;
    private String allowedExamExtensions = "pdf,jpg,jpeg,png";

    public int getMaxExamUploadFiles() { return maxExamUploadFiles; }
    public void setMaxExamUploadFiles(int maxExamUploadFiles) { this.maxExamUploadFiles = maxExamUploadFiles; }

    public String getAllowedExamExtensions() { return allowedExamExtensions; }
    public void setAllowedExamExtensions(String allowedExamExtensions) { this.allowedExamExtensions = allowedExamExtensions; }

    public Set<String> getAllowedExamExtensionSet() {
        return Arrays.stream(allowedExamExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
