package com.stylink.fo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.stylink"})
public class FoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoApiApplication.class, args);
    }
}
