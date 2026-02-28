package io.github.kamill7779.qforge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class QForgeGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(QForgeGatewayApplication.class, args);
    }
}
