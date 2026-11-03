package com.hubc.magnusblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MagnusblogApplication {

    public static void main(String[] args) {
        SpringApplication.run(MagnusblogApplication.class, args);
    }
}
