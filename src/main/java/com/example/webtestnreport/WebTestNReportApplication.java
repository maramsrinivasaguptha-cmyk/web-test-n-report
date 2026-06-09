package com.example.webtestnreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebTestNReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebTestNReportApplication.class, args);
    }
}
