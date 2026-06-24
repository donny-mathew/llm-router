package com.llmrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.llmrouter.config.RouterProperties;

@SpringBootApplication
@EnableConfigurationProperties(RouterProperties.class)
public class LlmRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmRouterApplication.class, args);
    }
}
