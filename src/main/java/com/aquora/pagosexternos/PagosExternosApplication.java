package com.aquora.pagosexternos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PagosExternosApplication {
    public static void main(String[] args) {
        SpringApplication.run(PagosExternosApplication.class, args);
    }
}
