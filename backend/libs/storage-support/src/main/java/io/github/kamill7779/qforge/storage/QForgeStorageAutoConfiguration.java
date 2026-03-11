package io.github.kamill7779.qforge.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(QForgeStorageProperties.class)
@ConditionalOnProperty(prefix = "qforge.storage", name = "backend", havingValue = "cos", matchIfMissing = true)
public class QForgeStorageAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public COSClient cosClient(QForgeStorageProperties properties) {
        BasicCOSCredentials credentials = new BasicCOSCredentials(
                properties.getCos().getSecretId(),
                properties.getCos().getSecretKey()
        );
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getCos().getRegion()));
        if (properties.getCos().getEndpoint() != null
                && properties.getCos().getEndpoint().startsWith("https://")) {
            clientConfig.setHttpProtocol(HttpProtocol.https);
        }
        return new COSClient(credentials, clientConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public QForgeStorageService qForgeStorageService(COSClient cosClient, QForgeStorageProperties properties) {
        return new CosQForgeStorageService(cosClient, properties);
    }
}
