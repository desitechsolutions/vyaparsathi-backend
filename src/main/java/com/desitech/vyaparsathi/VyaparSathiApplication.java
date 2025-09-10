package com.desitech.vyaparsathi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class VyaparSathiApplication {
    public static void main(String[] args) {
        SpringApplication.run(VyaparSathiApplication.class, args);
    }
}
