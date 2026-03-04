package io.github.kamill7779.qforge.persist;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("io.github.kamill7779.qforge.persist.repository")
public class PersistServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersistServiceApplication.class, args);
    }
}
