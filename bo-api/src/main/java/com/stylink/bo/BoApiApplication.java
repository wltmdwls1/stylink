package com.stylink.bo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.stylink"})
public class BoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoApiApplication.class, args);
    }
}
