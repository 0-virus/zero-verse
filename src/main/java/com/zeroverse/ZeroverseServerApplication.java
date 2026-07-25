package com.zeroverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ZeroverseServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZeroverseServerApplication.class, args);
    }
}
