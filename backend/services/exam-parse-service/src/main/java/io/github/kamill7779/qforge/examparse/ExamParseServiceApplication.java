package io.github.kamill7779.qforge.examparse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "io.github.kamill7779.qforge.internal.api"
})
public class ExamParseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamParseServiceApplication.class, args);
    }
}
