package com.learn.rxjs.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExamMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamMockApplication.class, args);
    }
}
