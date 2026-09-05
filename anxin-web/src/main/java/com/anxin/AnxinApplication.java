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
        //指定 RocketMQ 客户端日志根目录，可用 -Dsmartchat.log.root 覆盖
        System.setProperty("rocketmq.log.root", System.getProperty("smartchat.log.root", "logs"));
        SpringApplication.run(AnxinApplication.class, args);
    }
}
