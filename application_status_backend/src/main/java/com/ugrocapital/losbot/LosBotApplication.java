package com.ugrocapital.losbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LosBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LosBotApplication.class, args);
    }
}