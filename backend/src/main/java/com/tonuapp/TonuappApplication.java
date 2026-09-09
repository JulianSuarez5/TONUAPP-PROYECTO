package com.tonuapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TonuappApplication {

    // Punto de entrada de la aplicacion Spring Boot
    public static void main(String[] args) {
        SpringApplication.run(TonuappApplication.class, args);
    }
}
