package com.anxin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


@SpringBootApplication(scanBasePackages = "com.anxin")
@ConfigurationPropertiesScan("com.anxin.config.properties")
@MapperScan("com.anxin.mapper")
public class AnxinApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnxinApplication.class, args);
    }
}
